package fyi.kittens.shared.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import org.json.JSONObject

/**
 * DPoP (Demonstrating Proof-of-Possession) Manager
 *
 * Implements RFC 9449 for AT Protocol OAuth.
 * Now supports software-backed exportable keys for cross-device sync.
 */
class DPoPManager private constructor(context: Context) {
    companion object {
        private const val TAG = "DPoPManager"

        @Volatile
        private var instance: DPoPManager? = null

        fun getInstance(context: Context): DPoPManager {
            return instance ?: synchronized(this) {
                instance ?: DPoPManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Generate a new software-backed EC P-256 key pair.
     */
    fun generateSoftwareKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Export a KeyPair to a single Base64 string (JSON).
     */
    fun exportKeyPair(keyPair: KeyPair): String {
        val json = JSONObject().apply {
            put("private", Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
            put("public", Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
        }
        return json.toString()
    }

    /**
     * Import a KeyPair from a Base64 JSON string.
     */
    fun importKeyPair(exported: String): KeyPair {
        val json = JSONObject(exported)
        val privateBytes = Base64.decode(json.getString("private"), Base64.DEFAULT)
        val publicBytes = Base64.decode(json.getString("public"), Base64.DEFAULT)

        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateBytes))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicBytes))

        return KeyPair(publicKey, privateKey)
    }

    /**
     * Generate a DPoP proof JWT for a specific HTTP request using the provided KeyPair.
     */
    fun generateProof(
        method: String,
        url: String,
        keyPair: KeyPair,
        accessToken: String? = null,
        nonce: String? = null
    ): String {
        val publicKey = keyPair.public as ECPublicKey
        val privateKey = keyPair.private

        val point = publicKey.w
        val x = encodeUnsignedBigInt(point.affineX)
        val y = encodeUnsignedBigInt(point.affineY)

        val jwk = JSONObject().apply {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", x)
            put("y", y)
        }

        val header = JSONObject().apply {
            put("typ", "dpop+jwt")
            put("alg", "ES256")
            put("jwk", jwk)
        }

        val now = System.currentTimeMillis() / 1000
        val cleanUrl = stripQueryAndFragment(url)
        Log.v(TAG, "Generating proof for $method $cleanUrl")

        val payload = JSONObject().apply {
            put("jti", UUID.randomUUID().toString())
            put("htm", method.uppercase())
            put("htu", cleanUrl)
            put("iat", now)

            if (nonce != null) {
                put("nonce", nonce)
            }

            if (accessToken != null) {
                val tokenHash = MessageDigest.getInstance("SHA-256")
                    .digest(accessToken.toByteArray(Charsets.UTF_8))
                val ath = Base64.encodeToString(
                    tokenHash,
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
                put("ath", ath)
            }
        }

        val headerEncoded = base64UrlEncode(header.toString().toByteArray(Charsets.UTF_8))
        val payloadString = payload.toString().replace("\\/", "/")
        val payloadEncoded = base64UrlEncode(payloadString.toByteArray(Charsets.UTF_8))
        val signatureInput = "$headerEncoded.$payloadEncoded"

        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(signatureInput.toByteArray(Charsets.UTF_8))
        }.sign()

        val signatureEncoded = base64UrlEncode(convertDerToJoseSignature(signature))

        return "$signatureInput.$signatureEncoded"
    }

    private fun stripQueryAndFragment(url: String): String {
        return url.split('?', '#').first()
    }

    private fun convertDerToJoseSignature(derSignature: ByteArray): ByteArray {
        var offset = 0

        if (derSignature[offset++] != 0x30.toByte()) {
            throw IllegalArgumentException("Invalid DER signature: missing SEQUENCE")
        }

        val (_, seqLengthBytes) = readDerLength(derSignature, offset)
        offset += seqLengthBytes

        if (derSignature[offset++] != 0x02.toByte()) {
            throw IllegalArgumentException("Invalid DER signature: missing R INTEGER")
        }

        val (rLength, rLengthBytes) = readDerLength(derSignature, offset)
        offset += rLengthBytes

        var r = derSignature.copyOfRange(offset, offset + rLength)
        offset += rLength

        if (derSignature[offset++] != 0x02.toByte()) {
            throw IllegalArgumentException("Invalid DER signature: missing S INTEGER")
        }

        val (sLength, sLengthBytes) = readDerLength(derSignature, offset)
        offset += sLengthBytes

        var s = derSignature.copyOfRange(offset, offset + sLength)

        while (r.isNotEmpty() && r[0] == 0x00.toByte() && r.size > 32) {
            r = r.copyOfRange(1, r.size)
        }
        while (s.isNotEmpty() && s[0] == 0x00.toByte() && s.size > 32) {
            s = s.copyOfRange(1, s.size)
        }

        val rPadded = r.padStart(32)
        val sPadded = s.padStart(32)

        return rPadded + sPadded
    }

    private fun readDerLength(data: ByteArray, offset: Int): Pair<Int, Int> {
        val firstByte = data[offset].toInt() and 0xFF

        return if (firstByte < 0x80) {
            Pair(firstByte, 1)
        } else {
            val numLengthBytes = firstByte and 0x7F
            var length = 0
            for (i in 1..numLengthBytes) {
                length = (length shl 8) or (data[offset + i].toInt() and 0xFF)
            }
            Pair(length, 1 + numLengthBytes)
        }
    }

    private fun ByteArray.padStart(targetLength: Int): ByteArray {
        if (size >= targetLength) return copyOfRange(size - targetLength, size)

        val padded = ByteArray(targetLength)
        System.arraycopy(this, 0, padded, targetLength - size, size)
        return padded
    }

    private fun encodeUnsignedBigInt(value: BigInteger): String {
        val bytes = value.toByteArray()
        val unsigned = if (bytes[0] == 0.toByte() && bytes.size > 1) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }

        val padded = unsigned.padStart(32)
        return Base64.encodeToString(padded, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}

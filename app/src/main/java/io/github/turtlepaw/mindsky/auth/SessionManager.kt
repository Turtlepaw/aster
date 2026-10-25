package io.github.turtlepaw.mindsky.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import fyi.kittens.shared.auth.OAuthState
import fyi.kittens.shared.auth.SessionProvider
import fyi.kittens.shared.auth.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SessionManager(private val context: Context) : SessionProvider {

    private val alias = "mindsky_aes_key"
    private val prefsName = "mindsky_prefs"
    private val keySession = "user_session"
    private val keyOAuthState = "oauth_state"
    private val currentDidKey = "current_did"
    private val allDidsKey = "all_dids"
    private val gson = Gson()

    private val _sessionFlow = MutableStateFlow<UserSession?>(null)
    override val sessionFlow: StateFlow<UserSession?> = _sessionFlow.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    val currentDid get() = sessionFlow.value?.did

    init {
        try {
            generateSecretKeyIfNotExists()
            val currentDid = prefs.getString(currentDidKey, null)
            if (currentDid != null) {
                Log.d("SessionManager", "Loading session for $currentDid")
                _sessionFlow.value = getSession(currentDid)
            } else {
                Log.d("SessionManager", "No session found on disk")
            }
        } catch (e: Exception) {
            Log.e("SessionManager", "Critical error in session initialization", e)
        } finally {
            _isInitialized.value = true
        }
    }

    private fun generateSecretKeyIfNotExists() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val keyGenSpec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(keyGenSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (keyStore.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(4 + iv.size + cipherText.size)
        buffer.putInt(iv.size)
        buffer.put(iv)
        buffer.put(cipherText)
        return Base64.encodeToString(buffer.array(), Base64.DEFAULT)
    }

    private fun decrypt(encoded: String): String {
        return try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val buffer = ByteBuffer.wrap(bytes)
            val ivSize = buffer.int
            val iv = ByteArray(ivSize)
            buffer.get(iv)
            val cipherText = ByteArray(buffer.remaining())
            buffer.get(cipherText)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(cipherText)
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e("SessionManager", "Failed to decrypt session data", e)
            throw e
        }
    }

    fun UserSession.getPrefName(): String =
        getPrefName(did)

    fun getPrefName(did: String): String =
        keySession + did

    override fun saveSession(session: UserSession) {
        val json = gson.toJson(session)
        val encrypted = encrypt(json)
        
        val dids = getLoggedInDids().toMutableSet()
        dids.add(session.did)
        saveLoggedInDids(dids.toList())
        
        prefs.edit { 
            putString(session.getPrefName(), encrypted)
            putString(currentDidKey, session.did)
        }
        _sessionFlow.value = session
    }

    fun getSession(): UserSession? = getCurrentSession()

    override fun getCurrentSession(): UserSession? {
        val currentDid = prefs.getString(currentDidKey, null)
        return if (currentDid != null) {
            getSession(currentDid)
        } else {
            null
        }
    }

    fun getLoggedInDids(): List<String> {
        val json = prefs.getString(allDidsKey, null) ?: return emptyList()
        return gson.fromJson(json, Array<String>::class.java)?.toList() ?: emptyList()
    }

    private fun saveLoggedInDids(dids: List<String>) {
        val json = gson.toJson(dids)
        prefs.edit { putString(allDidsKey, json) }
    }

    fun getSessions(): List<UserSession> {
        return getLoggedInDids().mapNotNull { getSession(it) }
    }

    fun switchAccount(did: String) {
        val session = getSession(did) ?: return
        prefs.edit { putString(currentDidKey, did) }
        _sessionFlow.value = session
    }

    fun getSession(did: String): UserSession? {
        val encrypted = prefs.getString(getPrefName(did), null) ?: return null
        return try {
            val decrypted = decrypt(encrypted)
            val session = gson.fromJson(decrypted, UserSession::class.java)
            if (session.pdsUrl.isBlank()) {
                Log.w("SessionManager", "Stored session missing PDS URL; clearing.")
                clearSession(did)
                null
            } else {
                session
            }
        } catch (e: Exception) {
            Log.w("SessionManager", "Failed to load session for $did", e)
            // Don't clear immediately, it might be a transient KeyStore issue
            // clearSession(did) 
            null
        }
    }

    override fun clearSession(did: String) {
        prefs.edit { remove(getPrefName(did)) }
        
        val dids = getLoggedInDids().toMutableList()
        dids.remove(did)
        saveLoggedInDids(dids)
        
        val currentDid = prefs.getString(currentDidKey, null)
        if (currentDid == did) {
            val nextDid = dids.firstOrNull()
            prefs.edit { 
                if (nextDid != null) putString(currentDidKey, nextDid)
                else remove(currentDidKey)
            }
            _sessionFlow.value = nextDid?.let { getSession(it) }
        }
    }

    override fun saveOAuthState(state: OAuthState) {
        val json = gson.toJson(state)
        val encrypted = encrypt(json)
        prefs.edit { putString(keyOAuthState, encrypted) }
    }

    override fun getOAuthState(): OAuthState? {
        val encrypted = prefs.getString(keyOAuthState, null) ?: return null
        return try {
            val decrypted = decrypt(encrypted)
            gson.fromJson(decrypted, OAuthState::class.java)
        } catch (e: Exception) {
            Log.w("SessionManager", "Failed to load OAuth state; clearing.", e)
            clearOAuthState()
            null
        }
    }

    override fun clearOAuthState() {
        prefs.edit { remove(keyOAuthState) }
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
}

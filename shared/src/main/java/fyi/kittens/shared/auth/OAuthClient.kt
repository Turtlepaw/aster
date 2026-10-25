package fyi.kittens.shared.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.security.KeyPair
import java.security.MessageDigest

@Serializable
data class SlingshotResponse(
    val did: String
)

class AuthFailureException(
    message: String,
    val status: HttpStatusCode,
    val body: String? = null
) : Exception(message)

/**
 * OAuth Client for ATProto using DPoP.
 */
class OAuthClient(
    private val context: Context,
    private val sessionProvider: SessionProvider
) {
    private val dpopManager = DPoPManager.getInstance(context)

    private val httpClient = HttpClient(OkHttp)

    companion object {
        private const val TAG = "OAuthClient"
        private const val REDIRECT_URI = "https://aster.kittens.fyi/oauth/callback"
        private const val CLIENT_ID = "https://aster.kittens.fyi/client-metadata.json"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private var codeVerifier: String? = null
    private var expectedState: String? = null

    /**
     * Start the OAuth flow by resolving the user's handle and building the authorization URL.
     */
    suspend fun startOAuthFlowWithHandle(handle: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting OAuth flow for handle: $handle")

        val did = resolveHandleToDid(handle, httpClient)
        val pdsUrl = getPdsUrlFromDid(did, httpClient)
        val authServerUrl = discoverAuthorizationServer(pdsUrl)
        val metadata = getAuthorizationServerMetadata(authServerUrl)

        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier!!)
        expectedState = generateState()
        val keyPair = dpopManager.generateSoftwareKeyPair()

        val authUrl = if (metadata.pushedAuthorizationRequestEndpoint != null) {
            val requestUri = requestPushedAuthorization(
                metadata.pushedAuthorizationRequestEndpoint,
                codeChallenge,
                expectedState!!,
                keyPair,
                handle
            )
            buildAuthorizationUrlWithRequestUri(metadata.authorizationEndpoint, requestUri)
        } else {
            buildAuthorizationUrl(
                metadata.authorizationEndpoint,
                codeChallenge,
                expectedState!!,
                handle
            )
        }

        sessionProvider.saveOAuthState(
            OAuthState(
                expectedState!!,
                codeVerifier!!,
                authServerUrl,
                handle,
                did,
                dpopManager.exportKeyPair(keyPair)
            )
        )

        authUrl
    }

    /**
     * Start the OAuth flow by resolving the user's handle and building the authorization URL.
     */
    suspend fun startOAuthFlow(pdsUrl: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting OAuth flow for pds: $pdsUrl")

        val authServerUrl = discoverAuthorizationServer(pdsUrl)
        val metadata = getAuthorizationServerMetadata(authServerUrl)

        codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier!!)
        expectedState = generateState()
        val keyPair = dpopManager.generateSoftwareKeyPair()

        val authUrl = if (metadata.pushedAuthorizationRequestEndpoint != null) {
            val requestUri = requestPushedAuthorization(
                metadata.pushedAuthorizationRequestEndpoint,
                codeChallenge,
                expectedState!!,
                keyPair
            )
            buildAuthorizationUrlWithRequestUri(metadata.authorizationEndpoint, requestUri)
        } else {
            buildAuthorizationUrl(
                metadata.authorizationEndpoint,
                codeChallenge,
                expectedState!!,
            )
        }

        sessionProvider.saveOAuthState(
            OAuthState(
                expectedState!!,
                codeVerifier!!,
                authServerUrl,
                dpopKeyPair = dpopManager.exportKeyPair(keyPair)
            )
        )

        authUrl
    }

    /**
     * Handle OAuth callback and exchange code for tokens.
     */
    suspend fun handleCallback(code: String, state: String? = null): UserSession = withContext(Dispatchers.IO) {
        Log.d(TAG, "Handling OAuth callback")

        val storedState = sessionProvider.getOAuthState()
            ?: throw IllegalStateException("No OAuth state found")
        if (state != null && state != storedState.state) {
            throw IllegalStateException("State mismatch: CSRF protection failed")
        }

        val tokenEndpoint = getAuthorizationServerMetadata(storedState.authServerUrl).tokenEndpoint
        val keyPair = storedState.dpopKeyPair?.let { dpopManager.importKeyPair(it) }
            ?: dpopManager.generateSoftwareKeyPair()
        val session = exchangeCodeForTokens(code, storedState.codeVerifier, tokenEndpoint, keyPair)

        sessionProvider.saveSession(session)
        sessionProvider.clearOAuthState()

        session
    }

    /**
     * Refresh access token using refresh token.
     */
    suspend fun refreshToken(session: UserSession): UserSession = withContext(Dispatchers.IO) {
        Log.d(TAG, "Refreshing access token")

        val authServerUrl = discoverAuthorizationServer(session.pdsUrl)
        val metadata = getAuthorizationServerMetadata(authServerUrl)

        val keyPair = session.dpopKeyPair?.let { dpopManager.importKeyPair(it) } 
            ?: dpopManager.generateSoftwareKeyPair()
            
        var dpopProof = dpopManager.generateProof("POST", metadata.tokenEndpoint, keyPair)
        var response: HttpResponse = httpClient.post(metadata.tokenEndpoint) {
            header("DPoP", dpopProof)
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody(buildFormData {
                append("grant_type", "refresh_token")
                append("refresh_token", session.refreshToken)
                append("client_id", CLIENT_ID)
            })
        }

        if (response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.Unauthorized) {
            val dpopNonce = response.headers["DPoP-Nonce"]
            if (dpopNonce != null) {
                Log.d(TAG, "Server requires DPoP nonce for refresh, retrying")
                dpopProof = dpopManager.generateProof("POST", metadata.tokenEndpoint, keyPair, nonce = dpopNonce)
                response = httpClient.post(metadata.tokenEndpoint) {
                    header("DPoP", dpopProof)
                    header("Content-Type", "application/x-www-form-urlencoded")
                    setBody(buildFormData {
                        append("grant_type", "refresh_token")
                        append("refresh_token", session.refreshToken)
                        append("client_id", CLIENT_ID)
                    })
                }
            }
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw AuthFailureException("Token refresh failed: ${response.status} - $body", response.status, body)
        }

        val tokenResponse = json.decodeFromString<TokenResponse>(response.bodyAsText())

        val newSession = session.copy(
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken ?: session.refreshToken,
            tokenType = tokenResponse.tokenType,
            expiresAt = tokenResponse.expiresIn?.let { System.currentTimeMillis() + it * 1000 },
            dpopKeyPair = dpopManager.exportKeyPair(keyPair)
        )

        sessionProvider.saveSession(newSession)
        newSession
    }

    private suspend fun discoverAuthorizationServer(pdsUrl: String): String {
        val response: HttpResponse = httpClient.get("$pdsUrl/.well-known/oauth-protected-resource")
        if (response.status.isSuccess()) {
            val jsonElement = json.parseToJsonElement(response.bodyAsText())
            val jsonObj = jsonElement as? kotlinx.serialization.json.JsonObject
            val authServers = jsonObj?.get("authorization_servers")?.toString()?.trim('"')
                ?.removeSurrounding("[", "]")
                ?.split(",")
                ?.map { it.trim().trim('"') }

            return authServers?.firstOrNull()
                ?: throw Exception("No authorization server found")
        }

        return pdsUrl
    }

    private suspend fun getAuthorizationServerMetadata(authServerUrl: String): AuthorizationServerMetadata {
        val response = httpClient.get("$authServerUrl/.well-known/oauth-authorization-server")
        return json.decodeFromString(response.bodyAsText())
    }

    private suspend fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String,
        tokenEndpoint: String,
        softwareKeyPair: KeyPair
    ): UserSession {
        var dpopProof = dpopManager.generateProof("POST", tokenEndpoint, softwareKeyPair)
        var response: HttpResponse = httpClient.post(tokenEndpoint) {
            header("DPoP", dpopProof)
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody(buildFormData {
                append("grant_type", "authorization_code")
                append("code", code)
                append("redirect_uri", REDIRECT_URI)
                append("code_verifier", codeVerifier)
                append("client_id", CLIENT_ID)
            })
        }

        if (response.status == HttpStatusCode.BadRequest || response.status == HttpStatusCode.Unauthorized) {
            val dpopNonce = response.headers["DPoP-Nonce"]
            if (dpopNonce != null) {
                Log.d(TAG, "Server requires DPoP nonce, retrying with nonce: $dpopNonce")
                dpopProof = dpopManager.generateProof("POST", tokenEndpoint, softwareKeyPair, nonce = dpopNonce)
                response = httpClient.post(tokenEndpoint) {
                    header("DPoP", dpopProof)
                    header("Content-Type", "application/x-www-form-urlencoded")
                    setBody(buildFormData {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", REDIRECT_URI)
                        append("code_verifier", codeVerifier)
                        append("client_id", CLIENT_ID)
                    })
                }
            }
        }

        if (!response.status.isSuccess()) {
            val error = response.bodyAsText()
            throw AuthFailureException("Token exchange failed: ${response.status} - $error", response.status, error)
        }

        val tokenResponse = json.decodeFromString<TokenResponse>(response.bodyAsText())

        val did = tokenResponse.sub ?: throw Exception("No sub in token response")
        val handle = resolveDidToHandle(did)
        val pdsUrl = getPdsUrlFromDid(did, httpClient)

        return UserSession(
            did = did,
            handle = handle,
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken ?: throw Exception("No refresh token"),
            tokenType = tokenResponse.tokenType,
            expiresAt = tokenResponse.expiresIn?.let { System.currentTimeMillis() + it * 1000 },
            pdsUrl = pdsUrl,
            dpopKeyPair = dpopManager.exportKeyPair(softwareKeyPair)
        )
    }

    private suspend fun requestPushedAuthorization(
        parEndpoint: String,
        codeChallenge: String,
        state: String,
        keyPair: KeyPair,
        loginHint: String? = null
    ): String {
        var dpopProof = dpopManager.generateProof("POST", parEndpoint, keyPair)
        var response: HttpResponse = httpClient.post(parEndpoint) {
            header("DPoP", dpopProof)
            header("Content-Type", "application/x-www-form-urlencoded")
            setBody(buildFormData {
                append("response_type", "code")
                append("client_id", CLIENT_ID)
                append("redirect_uri", REDIRECT_URI)
                append("scope", "atproto transition:generic")
                append("code_challenge", codeChallenge)
                append("code_challenge_method", "S256")
                append("state", state)
                if(loginHint != null) {
                    append("login_hint", loginHint)
                }
            })
        }

        if (response.status.value == 400 || response.status.value == 401) {
            val dpopNonce = response.headers["DPoP-Nonce"]
            if (dpopNonce != null) {
                Log.d(TAG, "Server requires DPoP nonce for PAR, retrying")
                dpopProof = dpopManager.generateProof("POST", parEndpoint, keyPair, nonce = dpopNonce)
                response = httpClient.post(parEndpoint) {
                    header("DPoP", dpopProof)
                    header("Content-Type", "application/x-www-form-urlencoded")
                    setBody(buildFormData {
                        append("response_type", "code")
                        append("client_id", CLIENT_ID)
                        append("redirect_uri", REDIRECT_URI)
                        append("scope", "atproto transition:generic")
                        append("code_challenge", codeChallenge)
                        append("code_challenge_method", "S256")
                        append("state", state)
                        if(loginHint != null) {
                            append("login_hint", loginHint)
                        }
                    })
                }
            }
        }

        if (!response.status.isSuccess()) {
            val error = response.bodyAsText()
            throw Exception("PAR request failed: ${response.status} - $error")
        }

        val parResponse = json.decodeFromString<PARResponse>(response.bodyAsText())
        return parResponse.requestUri
    }

    private suspend fun resolveDidToHandle(did: String): String {
        val didDocText = when {
            did.startsWith("did:plc:") -> httpClient.get("https://plc.directory/$did").bodyAsText()
            did.startsWith("did:web:") -> {
                val domain = did.removePrefix("did:web:")
                httpClient.get("https://$domain/.well-known/did.json").bodyAsText()
            }
            else -> throw Exception("Unsupported DID method")
        }

        val didDoc = json.decodeFromString<DidDocument>(didDocText)
        val atUri = didDoc.alsoKnownAs?.firstOrNull { it.startsWith("at://") }
        return atUri?.removePrefix("at://") ?: did
    }

    private fun buildAuthorizationUrl(
        authEndpoint: String,
        codeChallenge: String,
        state: String,
        loginHint: String? = null
    ): String {
        val params = buildString {
            append("response_type=code")
            append("&client_id=${URLEncoder.encode(CLIENT_ID, "UTF-8")}")
            append("&redirect_uri=${URLEncoder.encode(REDIRECT_URI, "UTF-8")}")
            append("&scope=${URLEncoder.encode("atproto transition:generic", "UTF-8")}")
            append("&code_challenge=$codeChallenge")
            append("&code_challenge_method=S256")
            append("&state=$state")
            if(loginHint != null)
                append("&login_hint=${URLEncoder.encode(loginHint, "UTF-8")}")
        }

        return "$authEndpoint?$params"
    }

    private fun buildAuthorizationUrlWithRequestUri(
        authEndpoint: String,
        requestUri: String
    ): String {
        val params = buildString {
            append("client_id=${URLEncoder.encode(CLIENT_ID, "UTF-8")}")
            append("&request_uri=${URLEncoder.encode(requestUri, "UTF-8")}")
        }

        return "$authEndpoint?$params"
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(verifier.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generateState(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun buildFormData(builder: FormBuilder.() -> Unit): String {
        val formBuilder = FormBuilder()
        formBuilder.builder()
        return formBuilder.build()
    }

    private class FormBuilder {
        private val params = mutableListOf<Pair<String, String>>()

        fun append(key: String, value: String) {
            params.add(key to value)
        }

        fun build(): String {
            return params.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
            }
        }
    }
}

suspend fun getPdsUrlFromDid(did: String, httpClient: HttpClient = HttpClient()): String {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    val didDocText = when {
        did.startsWith("did:plc:") -> httpClient.get("https://plc.directory/$did").bodyAsText()
        did.startsWith("did:web:") -> {
            val domain = did.removePrefix("did:web:")
            httpClient.get("https://$domain/.well-known/did.json").bodyAsText()
        }
        else -> throw Exception("Unsupported DID method")
    }

    val didDoc = json.decodeFromString<DidDocument>(didDocText)
    val pdsService = didDoc.service?.firstOrNull { it.type == "AtprotoPersonalDataServer" }
        ?: throw Exception("No PDS service found in DID document")

    return pdsService.serviceEndpoint
}

suspend fun resolveHandleToDid(handle: String, httpClient: HttpClient = HttpClient()): String {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
    val response: HttpResponse = httpClient.get(
        "https://slingshot.microcosm.blue/xrpc/com.atproto.identity.resolveHandle?handle=$handle"
    )
    if (!response.status.isSuccess()) {
        throw Exception("Could not resolve handle to DID")
    }

    val text = response.bodyAsText()
    return json.decodeFromString<SlingshotResponse>(text).did
}

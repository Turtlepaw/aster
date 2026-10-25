package fyi.kittens.shared.auth

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.util.AttributeKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

class DpopAuthPlugin private constructor(
    private val sessionProvider: SessionProvider,
    private val oauthClient: OAuthClient,
    private val dpopManager: DPoPManager,
    private val refreshBufferMs: Long
) {
    private val refreshMutex = Mutex()

    class Config {
        lateinit var sessionProvider: SessionProvider
        lateinit var oauthClient: OAuthClient
        lateinit var dpopManager: DPoPManager
        var refreshBufferMs: Long = 30_000L
    }

    companion object : HttpClientPlugin<Config, DpopAuthPlugin> {
        override val key: AttributeKey<DpopAuthPlugin> = AttributeKey("DpopAuthPlugin")

        override fun prepare(block: Config.() -> Unit): DpopAuthPlugin {
            val config = Config().apply(block)
            return DpopAuthPlugin(
                sessionProvider = config.sessionProvider,
                oauthClient = config.oauthClient,
                dpopManager = config.dpopManager,
                refreshBufferMs = config.refreshBufferMs
            )
        }

        override fun install(plugin: DpopAuthPlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val baseRequest = HttpRequestBuilder().takeFrom(request)
                val session = plugin.sessionProvider.getCurrentSession()
                    ?: return@intercept execute(baseRequest)

                var activeSession = plugin.ensureFreshSession(session)

                suspend fun sendWithDpop(
                    sessionToUse: UserSession,
                    nonce: String? = null
                ): HttpClientCall {
                    val targetUrl = baseRequest.url.buildString()
                    
                    // Force the proxy header just before execution
                    if (targetUrl.contains("app.bsky") || targetUrl.contains("chat.bsky")) {
                        baseRequest.headers["atproto-proxy"] = "did:web:api.bsky.app#bsky_appview"
                    }

                    Log.d("DpopAuthPlugin", "Executing request: $targetUrl")
                    val startTime = System.currentTimeMillis()
                    
                    val call = try {
                        execute(HttpRequestBuilder().takeFrom(baseRequest).apply {
                            plugin.applyAuthHeaders(this, sessionToUse, nonce)
                        })
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("DpopAuthPlugin", "Network execution failed for $targetUrl after ${System.currentTimeMillis() - startTime}ms", e)
                        throw e
                    }

                    Log.d("DpopAuthPlugin", "Received response: ${call.response.status} for $targetUrl after ${System.currentTimeMillis() - startTime}ms")

                    // Always save non-success responses so we can read the body multiple times for error checks
                    return if (!call.response.status.isSuccess()) {
                        call.save()
                    } else {
                        call
                    }
                }

                var call = sendWithDpop(activeSession)

                val nonce = call.response.headers["DPoP-Nonce"]
                if (
                    nonce != null && (
                        call.response.status == HttpStatusCode.Unauthorized ||
                            call.response.status == HttpStatusCode.BadRequest
                        )
                ) {
                    Log.d("DpopAuthPlugin", "Retrying with nonce from header")
                    call = sendWithDpop(activeSession, nonce)
                }

                if (
                    call.response.status == HttpStatusCode.BadRequest &&
                    nonce != null &&
                    plugin.requiresNonce(call.response)
                ) {
                    Log.d("DpopAuthPlugin", "Retrying with nonce from body")
                    call = sendWithDpop(activeSession, nonce)
                }

                if (call.response.status == HttpStatusCode.Unauthorized && plugin.isInvalidToken(call.response)) {
                    Log.i("DpopAuthPlugin", "Token invalid, attempting refresh")
                    
                    val refreshedSession = plugin.refreshMutex.withLock {
                        val current = plugin.sessionProvider.getCurrentSession()
                        if (current != null && current.accessToken != activeSession.accessToken && !plugin.isExpiringSoon(current)) {
                            Log.d("DpopAuthPlugin", "Session already refreshed by another request")
                            current
                        } else {
                            plugin.refreshSession(activeSession)
                        }
                    }

                    if (refreshedSession != null) {
                        activeSession = refreshedSession
                        Log.d("DpopAuthPlugin", "Retrying after token refresh")
                        call = sendWithDpop(activeSession)

                        val retryNonce = call.response.headers["DPoP-Nonce"]
                        if (
                            retryNonce != null && (
                                call.response.status == HttpStatusCode.Unauthorized ||
                                    call.response.status == HttpStatusCode.BadRequest
                                )
                        ) {
                            Log.d("DpopAuthPlugin", "Retrying with nonce after refresh")
                            call = sendWithDpop(activeSession, retryNonce)
                        }
                    }
                }

                if (!call.response.status.isSuccess()) {
                    Log.w("DpopAuthPlugin", "Final request failed with status: ${call.response.status}")
                }

                call
            }
        }
    }

    private fun applyAuthHeaders(
        request: HttpRequestBuilder,
        session: UserSession,
        nonce: String?
    ) {
        request.headers.remove("Authorization")
        request.headers.remove("DPoP")
        val tokenType = if (session.tokenType.isBlank()) "DPoP" else session.tokenType
        val isBearer = tokenType.equals("Bearer", ignoreCase = true)
        
        val dpopProof = if (isBearer) null else {
            val keyPair = session.dpopKeyPair?.let { dpopManager.importKeyPair(it) }
                ?: dpopManager.generateSoftwareKeyPair()
            
            dpopManager.generateProof(
                request.method.value,
                request.url.buildString(),
                keyPair,
                session.accessToken,
                nonce
            )
        }
        
        request.headers.append("Authorization", "$tokenType ${session.accessToken}")
        if (dpopProof != null) {
            request.headers.append("DPoP", dpopProof)
        }
        
        // Log final headers for debugging (excluding sensitive token prefix)
        val headerKeys = request.headers.names().joinToString(", ")
        Log.v("DpopAuthPlugin", "Final headers for ${request.url.host}: $headerKeys")
        if (request.headers.contains("atproto-proxy")) {
            Log.v("DpopAuthPlugin", "Proxy target: ${request.headers["atproto-proxy"]}")
        }
    }

    private suspend fun ensureFreshSession(session: UserSession): UserSession {
        if (!isExpiringSoon(session)) {
            return session
        }

        return refreshMutex.withLock {
            val latest = sessionProvider.getCurrentSession()
            if (latest != null && !isExpiringSoon(latest)) {
                return@withLock latest
            }
            // If refresh fails (e.g. network error), fallback to the current session
            // instead of failing the entire request/logging out.
            refreshSession(latest ?: session) ?: latest ?: session
        }
    }

    private fun isExpiringSoon(session: UserSession): Boolean {
        val expiresAt = session.expiresAt ?: return false
        return System.currentTimeMillis() >= expiresAt - refreshBufferMs
    }

    private suspend fun refreshSession(session: UserSession): UserSession? {
        return try {
            oauthClient.refreshToken(session)
        } catch (e: AuthFailureException) {
            if (e.status == HttpStatusCode.TooManyRequests) {
                Log.e("DpopAuthPlugin", "Token refresh rate limited (429); keeping session.", e)
                return null
            }
            val errorBody = e.body ?: "No body"
            Log.e("DpopAuthPlugin", "Token refresh failed with auth error (${e.status}); clearing session. Body: $errorBody", e)
            sessionProvider.clearSession(session.did)
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("DpopAuthPlugin", "Token refresh failed with network/transient error; keeping session.", e)
            null
        }
    }

    private suspend fun isInvalidToken(response: HttpResponse): Boolean {
        val authHeader = response.headers["WWW-Authenticate"]
        if (authHeader != null && authHeader.contains("invalid_token", ignoreCase = true)) {
            return true
        }

        return try {
            val body = response.bodyAsText()
            body.contains("invalid_token", ignoreCase = true) ||
                body.contains("ExpiredToken", ignoreCase = true) ||
                body.contains("Token has expired", ignoreCase = true) ||
                body.contains("Token has been revoked", ignoreCase = true) ||
                body.contains("Invalid refresh token", ignoreCase = true) ||
                body.contains("AuthenticationFailed", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun requiresNonce(response: HttpResponse): Boolean {
        return try {
            response.bodyAsText().contains("use_dpop_nonce", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
}

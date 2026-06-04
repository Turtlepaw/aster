package io.github.turtlepaw.mindsky.auth

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
import io.ktor.util.AttributeKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DpopAuthPlugin private constructor(
    private val sessionManager: SessionManager,
    private val oauthClient: OAuthClient,
    private val dpopManager: DPoPManager,
    private val refreshBufferMs: Long
) {
    private val refreshMutex = Mutex()

    class Config {
        lateinit var sessionManager: SessionManager
        lateinit var oauthClient: OAuthClient
        lateinit var dpopManager: DPoPManager
        var refreshBufferMs: Long = 30_000L
    }

    companion object : HttpClientPlugin<Config, DpopAuthPlugin> {
        override val key: AttributeKey<DpopAuthPlugin> = AttributeKey("DpopAuthPlugin")

        override fun prepare(block: Config.() -> Unit): DpopAuthPlugin {
            val config = Config().apply(block)
            return DpopAuthPlugin(
                sessionManager = config.sessionManager,
                oauthClient = config.oauthClient,
                dpopManager = config.dpopManager,
                refreshBufferMs = config.refreshBufferMs
            )
        }

        override fun install(plugin: DpopAuthPlugin, scope: HttpClient) {
            scope.plugin(HttpSend).intercept { request ->
                val baseRequest = HttpRequestBuilder().takeFrom(request)
                val session = plugin.sessionManager.getCurrentSession()
                    ?: return@intercept execute(baseRequest)

                var activeSession = plugin.ensureFreshSession(session)
                    ?: return@intercept execute(baseRequest)

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
                    val refreshed = plugin.refreshSession(activeSession)
                    if (refreshed != null) {
                        activeSession = refreshed
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
        val dpopProof = if (isBearer) null else dpopManager.generateProof(
            request.method.value,
            request.url.buildString(),
            session.accessToken,
            nonce
        )
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

    private suspend fun ensureFreshSession(session: UserSession): UserSession? {
        if (!isExpiringSoon(session)) {
            return session
        }

        return refreshMutex.withLock {
            val latest = sessionManager.getCurrentSession()
            if (latest != null && !isExpiringSoon(latest)) {
                return@withLock latest
            }
            refreshSession(latest ?: session)
        }
    }

    private fun isExpiringSoon(session: UserSession): Boolean {
        val expiresAt = session.expiresAt ?: return false
        return System.currentTimeMillis() >= expiresAt - refreshBufferMs
    }

    private suspend fun refreshSession(session: UserSession): UserSession? {
        return try {
            oauthClient.refreshToken(session)
        } catch (e: Exception) {
            Log.e("DpopAuthPlugin", "Token refresh failed; clearing session.", e)
            sessionManager.clearSession(session.did)
            null
        }
    }

    private suspend fun isInvalidToken(response: HttpResponse): Boolean {
        return try {
            val body = response.bodyAsText()
            body.contains("invalid_token", ignoreCase = true) ||
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

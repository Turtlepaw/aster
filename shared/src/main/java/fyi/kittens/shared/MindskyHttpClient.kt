package fyi.kittens.shared

import android.content.Context
import android.util.Log
import fyi.kittens.shared.auth.DPoPManager
import fyi.kittens.shared.auth.DpopAuthPlugin
import fyi.kittens.shared.auth.OAuthClient
import fyi.kittens.shared.auth.SessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.Logger as KtorLogger

object MindskyHttpClient {
    fun create(
        context: Context,
        sessionProvider: SessionProvider,
        hostUrlProvider: () -> String,
        acceptLabelersProvider: () -> String? = { null },
        loggerName: String = "Ktor_Authenticated"
    ): HttpClient {
        return HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(DpopAuthPlugin) {
                this.sessionProvider = sessionProvider
                this.oauthClient = OAuthClient(context, sessionProvider)
                this.dpopManager = DPoPManager.getInstance(context)
            }
            install(Logging) {
                logger = object : KtorLogger {
                    override fun log(message: String) {
                        Log.v(loggerName, message)
                    }
                }
                level = LogLevel.INFO
            }

            defaultRequest {
                val currentHost = hostUrlProvider()
                val parsedHost = io.ktor.http.Url(currentHost)

                // Set the PDS host if no host is currently specified
                if (url.host.isBlank() || url.host == "localhost") {
                    url.protocol = parsedHost.protocol
                    url.host = parsedHost.host
                    url.port = parsedHost.port
                }

                val acceptLabelers = acceptLabelersProvider()
                if (acceptLabelers != null && acceptLabelers.isNotEmpty()) {
                    headers["atproto-accept-labelers"] = acceptLabelers
                }
            }
        }
    }
}

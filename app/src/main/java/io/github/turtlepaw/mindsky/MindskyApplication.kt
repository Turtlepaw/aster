package io.github.turtlepaw.mindsky

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.directory
import coil3.request.crossfade
import io.github.turtlepaw.mindsky.auth.DPoPManager
import io.github.turtlepaw.mindsky.auth.DpopAuthPlugin
import io.github.turtlepaw.mindsky.auth.OAuthClient
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.cache.LabelManager
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.URLProtocol
import io.ktor.http.takeFrom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.XrpcBlueskyApi
import io.ktor.client.plugins.logging.Logger as KtorLogger

class MindskyApplication : Application(), SingletonImageLoader.Factory/*, Configuration.Provider*/ {
//    override val workManagerConfiguration: Configuration
//        get() = Configuration.Builder()
//            .setMinimumLoggingLevel(Log.DEBUG)
//            .build()

    private lateinit var sessionManager: SessionManager
    private var currentSession: UserSession? = null
    lateinit var labelManager: LabelManager
        private set

    var hostUrl = MutableStateFlow<String>("https://bsky.social")
    private val _blueskyApi = MutableStateFlow<BlueskyApi?>(null)
    val blueskyApiFlow = _blueskyApi.asStateFlow()
    val blueskyApi: BlueskyApi
        get() = _blueskyApi.value ?: error("BlueskyApi not initialized")

    val scope = CoroutineScope(Dispatchers.IO)

    override fun newImageLoader(context: Context): ImageLoader { // Coil setup
        return ImageLoader.Builder(context)
            .crossfade(true)
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(1024 * 1024 * 100)
                    .build()
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MindskyApplication", "onCreate")
        ObjectBox.init(this) // Your ObjectBox initialization
        sessionManager = SessionManager(this)
        labelManager = LabelManager(this)
        // postThreadRepository will be initialized in configureAuthenticatedApi

        scope.launch {
            sessionManager.sessionFlow.collect {
                configureAuthenticatedApi(currentSession)
            }
        }
    }

    fun configureAuthenticatedApi(session: UserSession?) {
        configureAuthenticatedApi(
            session?.pdsUrl?.takeIf { it.isNotBlank() } ?: "https://bsky.social",
            session
        )
    }

    fun configureAuthenticatedApi(newHostUrl: String, session: UserSession? = null): BlueskyApi {
        synchronized(this) {
            if (session?.did == currentSession?.did &&
                session?.pdsUrl == currentSession?.pdsUrl &&
                _blueskyApi.value != null &&
                hostUrl.value == newHostUrl
            ) {
                currentSession = session
                Log.d(
                    "MindskyApplication",
                    "Session unchanged (DID/PDS matches), skipping reconfiguration"
                )
                return blueskyApi
            }

            currentSession = session
            hostUrl.value = newHostUrl
        }

        Log.i(
            "MindskyApplication",
            "Configuring authenticated API for host: $newHostUrl, user: ${session?.handle}"
        )

        // Now, create the main client with the correct labelers
        val httpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(DpopAuthPlugin) {
                sessionManager = this@MindskyApplication.sessionManager
                oauthClient =
                    OAuthClient(this@MindskyApplication, this@MindskyApplication.sessionManager)
                dpopManager = DPoPManager.getInstance(this@MindskyApplication)
            }
            install(Logging) {
                logger = object : KtorLogger {
                    override fun log(message: String) {
                        Log.v("Ktor_Authenticated", message)
                    }
                }
                level = LogLevel.HEADERS
            }

            defaultRequest {
                val currentHost = hostUrl.value
                val parsedHost = io.ktor.http.Url(currentHost)

                // Set the PDS host if no host is currently specified
                if (url.host.isBlank() || url.host == "localhost") {
                    url.protocol = parsedHost.protocol
                    url.host = parsedHost.host
                    url.port = parsedHost.port
                }

                val cachedLabelers = labelManager.labelersFlow.value
                if (cachedLabelers.isNotEmpty()) {
                    headers["atproto-accept-labelers"] = cachedLabelers.joinToString(",")
                }
            }
        }

        val client = XrpcBlueskyApi(httpClient)

        this._blueskyApi.value = client

        CoroutineScope(Dispatchers.IO).launch {
            labelManager.preWarmDefinitions(client)
            labelManager.revalidateLabelers(client)
        }

        Log.i("MindskyApplication", "Authenticated API client configured for ${session?.handle}.")

        return client
    }
}

package io.github.turtlepaw.mindsky

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.Logger
import fyi.kittens.shared.MindskyHttpClient
import fyi.kittens.shared.auth.DPoPManager
import fyi.kittens.shared.auth.DpopAuthPlugin
import fyi.kittens.shared.auth.OAuthClient
import io.github.turtlepaw.mindsky.auth.SessionManager
import fyi.kittens.shared.auth.UserSession
import io.github.turtlepaw.mindsky.auth.WearAuthManager
import io.github.turtlepaw.mindsky.cache.LabelManager
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.preferences.PreferenceManager
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
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.XrpcBlueskyApi
import io.ktor.client.plugins.logging.Logger as KtorLogger

class MindskyApplication : Application(), SingletonImageLoader.Factory/*, Configuration.Provider*/ {
//    override val workManagerConfiguration: Configuration
//        get() = Configuration.Builder()
//            .setMinimumLoggingLevel(Log.DEBUG)
//            .build()

    lateinit var sessionManager: SessionManager
        private set
    private lateinit var wearAuthManager: WearAuthManager
    private var currentSession: UserSession? = null
    lateinit var labelManager: LabelManager
        private set
    lateinit var preferenceManager: PreferenceManager
        private set

    var hostUrl = MutableStateFlow<String>("https://bsky.social")
    private val _blueskyApi = MutableStateFlow<BlueskyApi?>(null)
    val blueskyApiFlow = _blueskyApi.asStateFlow()
    val blueskyApi: BlueskyApi
        get() = _blueskyApi.value ?: error("BlueskyApi not initialized")

    val scope = CoroutineScope(Dispatchers.IO)

    class RequestHeaderInterceptor : Interceptor {

        override fun intercept(chain: Interceptor.Chain): Response {
            val headers = Headers.Builder()
                .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val request = chain.request().newBuilder()
                .headers(headers)
                .build()
            return chain.proceed(request)
        }
    }

    override fun newImageLoader(context: Context): ImageLoader { // Coil setup
        return ImageLoader.Builder(context)
            .crossfade(true)
            .diskCache {
                coil3.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(1024 * 1024 * 100)
                    .build()
            }
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            OkHttpClient.Builder()
                                .addInterceptor(RequestHeaderInterceptor())
                                .build()
                        },
                    )
                )
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("MindskyApplication", "onCreate")
        ObjectBox.init(this) // Your ObjectBox initialization
        preferenceManager = PreferenceManager(this)
        sessionManager = SessionManager(this)
        wearAuthManager = WearAuthManager(this, sessionManager)
        wearAuthManager.start()
        labelManager = LabelManager(this)
        
        // Load initial session immediately to avoid guest flash
        val initialSession = sessionManager.getCurrentSession()
        if (initialSession != null) {
            configureAuthenticatedApi(initialSession)
        }

        scope.launch {
            sessionManager.sessionFlow.collect { session ->
                configureAuthenticatedApi(session)
            }
        }
    }

    fun configureAuthenticatedApi(session: UserSession?): BlueskyApi {
        return configureAuthenticatedApi(
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
        val httpClient = MindskyHttpClient.create(
            context = this,
            sessionProvider = sessionManager,
            hostUrlProvider = { hostUrl.value },
            acceptLabelersProvider = {
                val labels = labelManager.labelersFlow.value.takeIf { it.isNotEmpty() }?.joinToString(",")
                Log.d("MindskyApplication", "Accept labelers: $labels")
                labelManager.labelersFlow.value.takeIf { it.isNotEmpty() }?.joinToString(",")
            }
        )

        val client = XrpcBlueskyApi(httpClient)

        this._blueskyApi.value = client

        if (session != null) {
            CoroutineScope(Dispatchers.IO).launch {
                labelManager.preWarmDefinitions(client)
                labelManager.revalidateLabelers(client)
            }
        }

        Log.i("MindskyApplication", "Authenticated API client configured for ${session?.handle}.")

        return client
    }
}

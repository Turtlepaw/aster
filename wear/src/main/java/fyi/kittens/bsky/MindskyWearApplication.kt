package fyi.kittens.bsky

import android.app.Application
import fyi.kittens.shared.MindskyHttpClient
import fyi.kittens.bsky.auth.SessionManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.XrpcBlueskyApi

class MindskyWearApplication : Application() {
    lateinit var sessionManager: SessionManager
        private set

    private val _blueskyApi = MutableStateFlow<BlueskyApi?>(null)
    val blueskyApiFlow = _blueskyApi.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager(this)

        scope.launch {
            sessionManager.sessionFlow.collect { session ->
                configureApi(session)
            }
        }
    }

    private fun configureApi(session: fyi.kittens.shared.auth.UserSession?) {
        if (session == null) {
            _blueskyApi.value = null
            return
        }

        val httpClient = MindskyHttpClient.create(
            context = this,
            sessionProvider = sessionManager,
            hostUrlProvider = { session.pdsUrl.takeIf { it.isNotBlank() } ?: "https://bsky.social" }
        )

        _blueskyApi.value = XrpcBlueskyApi(httpClient)
        Log.d("MindskyWear", "API configured for ${session.handle}")
    }
}

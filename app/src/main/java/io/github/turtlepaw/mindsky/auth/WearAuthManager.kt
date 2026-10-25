package io.github.turtlepaw.mindsky.auth

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import fyi.kittens.shared.auth.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearAuthManager(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val dataClient = Wearable.getDataClient(context)
    private val gson = Gson()

    fun start() {
        Log.d(TAG, "Starting WearAuthManager")
        scope.launch {
            sessionManager.sessionFlow.collectLatest { session ->
                Log.d(TAG, "Session changed, syncing to wear: ${session?.handle}")
                syncSession(session)
            }
        }
    }

    private suspend fun syncSession(session: UserSession?) {
        try {
            val request = PutDataMapRequest.create("/auth_session").apply {
                if (session != null) {
                    dataMap.putString("session_json", gson.toJson(session))
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                } else {
                    dataMap.remove("session_json")
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }
            }.asPutDataRequest()
                .setUrgent()

            dataClient.putDataItem(request).await()
            Log.d(TAG, "Successfully synced session to wear: ${session?.handle ?: "Logged out"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync session to wear", e)
        }
    }

    companion object {
        private const val TAG = "WearAuthManager"
    }
}

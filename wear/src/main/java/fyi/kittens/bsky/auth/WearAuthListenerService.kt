package fyi.kittens.bsky.auth

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import com.google.gson.Gson
import fyi.kittens.shared.auth.UserSession

class WearAuthListenerService : WearableListenerService() {
    private val gson = Gson()

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged: ${dataEvents.count} events")
        for (event in dataEvents) {
            Log.d(TAG, "Event path: ${event.dataItem.uri.path}")
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/auth_session") {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val sessionJson = dataMap.getString("session_json")

                Log.d(TAG, "Received session update from phone: $sessionJson")

                val session = try {
                    gson.fromJson(sessionJson, UserSession::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse session JSON", e)
                    null
                } ?: return

                // Update local session manager
                (applicationContext as? fyi.kittens.bsky.MindskyWearApplication)?.sessionManager?.saveSession(
                    session
                )
            }
        }
    }

    companion object {
        private const val TAG = "WearAuthListener"
    }
}

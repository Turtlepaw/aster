package io.github.turtlepaw.mindsky.preferences

import android.content.Context

object TrackingPreferences {
    private const val PREFS_NAME = "app_prefs"
    
    fun isTrackingEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppPrefs.TrackingEnabled.getValue(prefs)
    }
    
    fun setTrackingEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        AppPrefs.TrackingEnabled.setValue(prefs, enabled)
    }

    fun getScrobblingDid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppPrefs.ScrobblingDid.getValue(prefs)
    }
}

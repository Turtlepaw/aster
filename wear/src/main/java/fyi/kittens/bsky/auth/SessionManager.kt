package fyi.kittens.bsky.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import fyi.kittens.shared.auth.OAuthState
import fyi.kittens.shared.auth.SessionProvider
import fyi.kittens.shared.auth.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionManager(context: Context) : SessionProvider {
    private val prefs: SharedPreferences = context.getSharedPreferences("wear_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val _sessionFlow = MutableStateFlow<UserSession?>(null)
    override val sessionFlow: StateFlow<UserSession?> = _sessionFlow.asStateFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        val json = prefs.getString("user_session", null)
        if (json != null) {
            try {
                _sessionFlow.value = gson.fromJson(json, UserSession::class.java)
            } catch (_: Exception) {
                _sessionFlow.value = null
            }
        }
    }

    override fun saveSession(session: UserSession) {
        _sessionFlow.value = session
        prefs.edit {
            putString("user_session", gson.toJson(session))
        }
    }

    override fun getCurrentSession(): UserSession? = _sessionFlow.value

    override fun clearSession(did: String) {
        if (_sessionFlow.value?.did == did) {
            _sessionFlow.value = null
            prefs.edit { remove("user_session") }
        }
    }

    override fun getOAuthState(): OAuthState? {
        val json = prefs.getString("oauth_state", null) ?: return null
        return try {
            gson.fromJson(json, OAuthState::class.java)
        } catch (_: Exception) {
            null
        }
    }

    override fun saveOAuthState(state: OAuthState) {
        prefs.edit { putString("oauth_state", gson.toJson(state)) }
    }

    override fun clearOAuthState() {
        prefs.edit { remove("oauth_state") }
    }
}

package fyi.kittens.shared.auth

import kotlinx.coroutines.flow.StateFlow

interface SessionProvider {
    val sessionFlow: StateFlow<UserSession?>
    fun getCurrentSession(): UserSession?
    fun saveSession(session: UserSession)
    fun clearSession(did: String)
    fun getOAuthState(): OAuthState?
    fun saveOAuthState(state: OAuthState)
    fun clearOAuthState()
}

data class OAuthState(
    val state: String,
    val codeVerifier: String,
    val authServerUrl: String,
    val handle: String? = null,
    val did: String? = null,
    val dpopKeyPair: String? = null
)

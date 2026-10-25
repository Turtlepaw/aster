package fyi.kittens.shared.auth

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val did: String,
    val handle: String,
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "DPoP",
    val expiresAt: Long? = null,
    val pdsUrl: String = "",
    val avatarUrl: String? = null,
    val displayName: String? = null,
    val dpopKeyPair: String? = null
)

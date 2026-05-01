package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import sh.christian.ozone.api.AtUri

data class PostState(
    val likeUri: AtUri? = null,
    val repostUri: AtUri? = null
)

class PostsState {
    private val posts = mutableStateMapOf<AtUri, PostState>()

    fun getLike(uri: AtUri) = posts[uri]?.likeUri
    fun getRepost(uri: AtUri) = posts[uri]?.repostUri

    fun syncFromViewer(
        uri: AtUri,
        likeUri: AtUri?,
        repostUri: AtUri?
    ): PostState {
        val current = posts[uri]
        val merged = PostState(
            likeUri = current?.likeUri ?: likeUri,
            repostUri = current?.repostUri ?: repostUri
        )
        if (current != merged) {
            posts[uri] = merged
        }
        return merged
    }

    fun setLike(uri: AtUri, state: AtUri? = null) {
        val current = posts[uri] ?: PostState()
        posts[uri] = current.copy(likeUri = state)
    }

    fun setReposted(uri: AtUri, state: AtUri? = null) {
        val current = posts[uri] ?: PostState()
        posts[uri] = current.copy(repostUri = state)
    }
}

val LocalPostState = staticCompositionLocalOf<PostsState> {
    error("PostState not provided")
}

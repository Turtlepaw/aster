package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import sh.christian.ozone.api.AtUri

val OPTIMISTIC_LIKE_URI = AtUri("at://optimistic/like")

data class PostState(
    val likeUri: AtUri? = null,
    val repostUri: AtUri? = null,
    val bookmarked: Boolean? = null
)

class PostsState {
    private val posts = mutableStateMapOf<AtUri, PostState>()
    private val inFlightLikes = mutableSetOf<AtUri>()

    fun getLike(uri: AtUri): AtUri? = posts[uri]?.likeUri
    fun getRepost(uri: AtUri): AtUri? = posts[uri]?.repostUri

    fun syncFromViewer(
        uri: AtUri,
        likeUri: AtUri?,
        repostUri: AtUri?,
        bookmarked: Boolean = false,
    ): PostState {
        return posts[uri] ?: PostState(
            likeUri = likeUri,
            repostUri = repostUri,
            bookmarked = bookmarked,
        ).also { posts[uri] = it }
    }

    fun setLike(uri: AtUri, state: AtUri? = null) {
        val current = posts[uri] ?: PostState()
        posts[uri] = current.copy(likeUri = state)
    }

    fun setReposted(uri: AtUri, state: AtUri? = null) {
        val current = posts[uri] ?: PostState()
        posts[uri] = current.copy(repostUri = state)
    }

    fun setBookmarked(uri: AtUri, state: Boolean? = null) {
        val current = posts[uri] ?: PostState()
        posts[uri] = current.copy(bookmarked = state)
    }

    fun isInFlightLike(uri: AtUri): Boolean = synchronized(inFlightLikes) {
        inFlightLikes.contains(uri)
    }

    fun setInFlightLike(uri: AtUri, inFlight: Boolean) = synchronized(inFlightLikes) {
        if (inFlight) {
            inFlightLikes.add(uri)
        } else {
            inFlightLikes.remove(uri)
        }
    }
}

val LocalPostState = staticCompositionLocalOf<PostsState> {
    error("PostState not provided")
}

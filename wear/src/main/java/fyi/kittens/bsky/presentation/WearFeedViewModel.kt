package fyi.kittens.bsky.presentation

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.PostView
import fyi.kittens.bsky.presentation.routes.FeedItem
import fyi.kittens.shared.FeedTuner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.AtUri

class WearFeedViewModel(
    private val api: BlueskyApi
) : ViewModel() {
    private val _feed = MutableStateFlow<List<FeedViewPost>>(emptyList())
    val feed: StateFlow<List<FeedViewPost>> = _feed

    var isRefreshing = mutableStateOf(false)
        private set

    var currentFeed: FeedItem? = null
    var cursor: String? = null

    private var loadingJob: kotlinx.coroutines.Job? = null

    fun loadFeed(feed: FeedItem) {
        if (currentFeed?.getUri() == feed.getUri() && (isRefreshing.value || _feed.value.isNotEmpty())) return
        loadingJob?.cancel()
        Log.d("WearFeedViewModel", "Loading feed: ${feed.getName()}")
        currentFeed = feed
        cursor = null
        loadingJob = viewModelScope.launch {
            isRefreshing.value = true
            try {
                val feedItems = getFeed(feed)
                Log.d("WearFeedViewModel", "Fetched ${feedItems.size} items")
                _feed.value = feedItems
            } catch (e: Throwable) {
                Log.e("WearFeedViewModel", "Error loading feed", e)
            } finally {
                isRefreshing.value = false
            }
        }
    }

    private suspend fun getFeed(feed: FeedItem): List<FeedViewPost> {
        return when (feed) {
            is FeedItem.Following -> {
                val response =
                    api.getTimeline(GetTimelineQueryParams(limit = 20, cursor = cursor))
                        .requireResponse()
                cursor = response.cursor
                FeedTuner.cleanReplies(response.feed)
            }

            is FeedItem.FeedGenerator -> {
                val response = api.getFeed(
                    app.bsky.feed.GetFeedQueryParams(
                        feed = feed.generator.value.uri,
                        limit = 20,
                        cursor = cursor
                    )
                ).requireResponse()
                cursor = response.cursor
                FeedTuner.cleanReplies(response.feed)
            }

            is FeedItem.List -> {
                val response = api.getListFeed(
                    app.bsky.feed.GetListFeedQueryParams(
                        list = feed.list.uri,
                        limit = 20,
                        cursor = cursor
                    )
                ).requireResponse()
                cursor = response.cursor
                FeedTuner.cleanReplies(response.feed)
            }
        }
    }

    fun loadMoreFeed() {
        viewModelScope.launch {
            try {
                val feed = getFeed(currentFeed!!)
                _feed.value = _feed.value + feed
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }

    suspend fun loadPost(uri: AtUri): PostView? {
        try {
            val post = api.getPosts(
                GetPostsQueryParams(
                    uris = listOf(uri)
                )
            ).requireResponse()
            return post.posts.first()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

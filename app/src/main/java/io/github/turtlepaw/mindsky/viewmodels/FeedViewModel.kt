package io.github.turtlepaw.mindsky.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import app.bsky.actor.GetProfileQueryParams
import app.bsky.actor.ProfileViewDetailed
import app.bsky.actor.PreferencesUnion
import app.bsky.bookmark.BookmarkViewItemUnion
import app.bsky.bookmark.GetBookmarksQueryParams
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.GetAuthorFeedQueryParams
import app.bsky.feed.GetFeedGeneratorQueryParams
import app.bsky.feed.GetFeedGeneratorResponse
import app.bsky.feed.GetFeedGeneratorsQueryParams
import app.bsky.feed.GetFeedQueryParams
import app.bsky.feed.GetListFeedQueryParams
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.GetTimelineQueryParams
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.graph.GetListQueryParams
import io.github.turtlepaw.fetch_and_cache.LoadResult
import io.github.turtlepaw.mindsky.cache.PostCache
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.db.SuggestedFeed
import fyi.kittens.shared.FeedTuner
import io.github.turtlepaw.mindsky.logic.ranking.PostScore
import io.github.turtlepaw.mindsky.logic.ranking.PostScore_
import io.github.turtlepaw.mindsky.routes.FeedItem
import io.github.turtlepaw.mindsky.utils.ApiUtils.fetchChunkedPosts
import io.github.turtlepaw.mindsky.workers.WorkerManager.enqueueImmediateWorkers
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did

data class ForYouPost(
    val post: PostView,
    val score: PostScore
    //val suggestedFeed: SuggestedFeed,
    //val feedInfo: GetFeedGeneratorResponse
)

class FeedViewModel(
    private val api: BlueskyApi,
    private val appContext: Context
) : ViewModel() {

    private val postCache = PostCache(appContext)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelay: Long = 1000L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        var lastException: Throwable? = null
        for (attempt in 0..maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                if (e is CancellationException || e.message?.contains("cancel", ignoreCase = true) == true) {
                    throw e
                }
                lastException = e
                if (attempt == maxRetries) {
                    throw e
                }
                Log.w("FeedVM", "Attempt ${attempt + 1} failed, retrying in ${currentDelay}ms...", e)
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            } catch (t: Throwable) {
                if (t is CancellationException || t.message?.contains("cancel", ignoreCase = true) == true) {
                    throw t
                }
                lastException = t
                if (attempt == maxRetries) {
                    throw t
                }
                Log.w("FeedVM", "Attempt ${attempt + 1} failed (Throwable), retrying in ${currentDelay}ms...", t)
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        throw lastException ?: IllegalStateException("Retry failed")
    }

    var followingFeed = mutableStateOf<List<FeedViewPost>?>(null)
        private set
    var isFetchingFeed = mutableStateOf(false)
        private set
    var isFetchingMoreFollowing = mutableStateOf(false)
        private set
    var hasMoreFollowing = mutableStateOf(true)
        private set

    var isFetchingMoreForYou = mutableStateOf(false)
        private set
    var isFetchingForYou = mutableStateOf(false)
        private set
    var hasMoreForYou = mutableStateOf(true)
        private set

    var forYouFeed = mutableStateOf<List<ForYouPost>?>(null)
        private set

    var highlightsFeed = mutableStateOf<List<Pair<PostScore, PostView>>?>(null)
        private set

    var customFeeds = mutableStateMapOf<AtUri, List<FeedViewPost>>()
        private set
    var isFetchingCustomFeeds = mutableStateMapOf<AtUri, Boolean>()
        private set
    var isFetchingMoreCustomFeeds = mutableStateMapOf<AtUri, Boolean>()
        private set
    var hasMoreCustomFeeds = mutableStateMapOf<AtUri, Boolean>()
        private set

    var error = mutableStateOf<String?>(null)
        private set

    var profileDetailed = mutableStateOf<ProfileViewDetailed?>(null)
    var isFetchingProfileMetadata = mutableStateOf(false)

    fun fetchProfileMetadata(did: Did, force: Boolean = false) {
        if (!force && profileDetailed.value?.did == did) return
        viewModelScope.launch {
            isFetchingProfileMetadata.value = true
            try {
                profileDetailed.value = retryWithBackoff {
                    api.getProfile(GetProfileQueryParams(did)).requireResponse()
                }
            } catch (e: Exception) {
                if (e is CancellationException || e.message?.contains("cancel", ignoreCase = true) == true) throw e
                Log.e("FeedVM", "Error fetching profile metadata", e)
            } finally {
                isFetchingProfileMetadata.value = false
            }
        }
    }

    var userFeeds = mutableStateOf<List<Pair<String, FeedItem>>>(emptyList())
    var isFetchingUserFeeds = mutableStateOf(false)

    fun fetchUserFeeds() {
        if (userFeeds.value.isNotEmpty()) return
        viewModelScope.launch {
            isFetchingUserFeeds.value = true
            try {
                val prefs = retryWithBackoff { api.getPreferencesForActor().requireResponse() }
                val savedFeedUris: List<String> = when (
                    val pref = prefs.preferences.firstOrNull {
                        it is PreferencesUnion.SavedFeedsPrefV2 || it is PreferencesUnion.SavedFeedsPref
                    }
                ) {
                    is PreferencesUnion.SavedFeedsPrefV2 -> pref.value.items
                        .filter { it.pinned }
                        .map { it.value }

                    is PreferencesUnion.SavedFeedsPref -> pref.value.pinned.map { it.toString() }
                    else -> emptyList()
                }

                val fetchable = savedFeedUris.filter { it != "following" }

                val feedGenerators = fetchable
                    .filter { it.contains("app.bsky.feed.generator") }
                    .map { AtUri(it) }
                val fetchedFeedGenerators = if (feedGenerators.isNotEmpty()) {
                    retryWithBackoff {
                        api.getFeedGenerators(
                            GetFeedGeneratorsQueryParams(
                                feeds = feedGenerators
                            )
                        ).requireResponse().feeds.associateBy { it.uri.atUri }
                    }
                } else {
                    emptyMap()
                }

                val lists = fetchable.filter { it.contains("app.bsky.graph.list") }
                    .map { AtUri(it) }
                val fetchedLists = if (lists.isNotEmpty()) {
                    lists.map { listUri ->
                        retryWithBackoff {
                            api.getList(
                                GetListQueryParams(
                                    list = listUri,
                                    limit = 1
                                )
                            ).requireResponse().list
                        }
                    }
                } else {
                    emptyList()
                }

                val ordered = mutableListOf<Pair<String, FeedItem>>()
                ordered.add("for_you" to FeedItem.ForYou)
                for (item in savedFeedUris) {
                    val feed = when {
                        item.contains("app.bsky.feed.generator") -> {
                            val generator = fetchedFeedGenerators[item] ?: continue
                            FeedItem.FeedGenerator(generator)
                        }

                        item.contains("app.bsky.graph.list") -> {
                            val list = fetchedLists.firstOrNull { it.uri.atUri == item } ?: continue
                            FeedItem.List(list)
                        }

                        item == "following" -> FeedItem.Following()
                        else -> null
                    }

                    if (feed != null) {
                        ordered.add(item to feed)
                    }
                }
                userFeeds.value = ordered
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("FeedVM", "Error fetching user feeds", e)
            } finally {
                isFetchingUserFeeds.value = false
            }
        }
    }

    var userFeedCursors = mutableMapOf<AtUri, String?>()

    suspend fun fetchUserFeed(uri: AtUri): List<FeedViewPost> {
        return retryWithBackoff {
            if(uri.atUri.contains("app.bsky.feed.generator")) {
                val feed = api.getFeed(
                    GetFeedQueryParams(
                        feed = uri,
                        limit = 50,
                        cursor = userFeedCursors[uri]
                    )
                ).requireResponse()

                userFeedCursors[uri] = feed.cursor
                feed.feed
            } else {
                val feed = api.getListFeed(
                    GetListFeedQueryParams(
                        list = uri,
                        limit = 50,
                        cursor = userFeedCursors[uri]
                    )
                ).requireResponse()

                userFeedCursors[uri] = feed.cursor
                feed.feed
            }
        }
    }

    fun fetchCustomFeed(uri: AtUri, isRefresh: Boolean = false) {
        if (isFetchingCustomFeeds[uri] == true && !isRefresh) return
        if (isFetchingMoreCustomFeeds[uri] == true && !isRefresh) return

        viewModelScope.launch {
            isFetchingCustomFeeds[uri] = true
            if (isRefresh) {
                userFeedCursors[uri] = null
                hasMoreCustomFeeds[uri] = true
            }
            error.value = null

            try {
                val feedResponse = fetchUserFeed(uri)
                val newPosts = feedResponse
                
                hasMoreCustomFeeds[uri] = userFeedCursors[uri] != null

                // Cache all posts
                newPosts.forEach { addCachedPost(it.post) }

                if (isRefresh) {
                    customFeeds[uri] = newPosts
                } else {
                    customFeeds[uri] = (customFeeds[uri] ?: emptyList()) + newPosts
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("FeedVM", "Error fetching custom feed $uri", e)
                error.value = "Failed to fetch custom feed: ${e.message}"
            } finally {
                isFetchingCustomFeeds[uri] = false
            }
        }
    }

    fun loadMoreCustomFeed(uri: AtUri) {
        if (isFetchingCustomFeeds[uri] == true || isFetchingMoreCustomFeeds[uri] == true) return
        if (userFeedCursors[uri] == null && customFeeds[uri]?.isNotEmpty() == true) {
            hasMoreCustomFeeds[uri] = false
            return
        }

        viewModelScope.launch {
            isFetchingMoreCustomFeeds[uri] = true
            error.value = null
            try {
                val feedResponse = fetchUserFeed(uri)
                val newPosts = feedResponse
                
                hasMoreCustomFeeds[uri] = userFeedCursors[uri] != null

                // Cache all posts
                newPosts.forEach { addCachedPost(it.post) }

                customFeeds[uri] = (customFeeds[uri] ?: emptyList()) + newPosts
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("FeedVM", "Error loading more custom feed posts $uri", e)
                error.value = "Failed to load more posts: ${e.message}"
            } finally {
                isFetchingMoreCustomFeeds[uri] = false
            }
        }
    }

    // Keep in-memory cache for immediate access, but also persist to disk
    var cachedPosts = mutableMapOf<AtUri, PostView>()
        private set

    fun addCachedPost(post: PostView) {
        cachedPosts[post.uri] = post
        // Persist to disk cache asynchronously
        viewModelScope.launch {
            postCache.cachePost(post)
        }
    }

    fun addCachedPosts(posts: List<PostView>) {
        posts.forEach { cachedPosts[it.uri] = it }
        viewModelScope.launch {
            // Process sequentially in a single coroutine to avoid overload
            posts.forEach { post ->
                postCache.cachePost(post)
            }
        }
    }

    suspend fun getCachedPost(uri: AtUri): PostView? {
        // First check memory cache
        cachedPosts[uri]?.let { return it }

        // Then check persistent cache
        val cachedPost = postCache.getCachedPost(uri)
        if (cachedPost != null) {
            // Add back to memory cache for faster access
            cachedPosts[uri] = cachedPost
            return cachedPost
        }

        return null
    }

    // Convenience method for non-suspend contexts
    fun getCachedPostSync(uri: AtUri): PostView? {
        return cachedPosts[uri]
    }

    fun removeCachedPost(uri: AtUri) {
        cachedPosts.remove(uri)
        viewModelScope.launch {
            postCache.removeCachedPost(uri)
        }
    }

    fun clearCache() {
        cachedPosts.clear()
        viewModelScope.launch {
            postCache.clearCache()
        }
    }

    fun getCacheSize(callback: (Long) -> Unit) {
        viewModelScope.launch {
            val size = postCache.getCacheSize()
            callback(size)
        }
    }

    var profilePosts = mutableStateOf<List<FeedViewPost>>(emptyList())
        private set

    var bookmarkPosts = mutableStateOf<List<FeedViewPost>>(emptyList())
        private set

    private var profileCursor: String? = null
    var isFetchingProfile = mutableStateOf(false)
        private set
    var hasMoreProfile = mutableStateOf(true)
        private set

    private var bookmarkCursor: String? = null
    var isFetchingBookmarks = mutableStateOf(false)
        private set
    var hasMoreBookmarks = mutableStateOf(true)
        private set

    fun fetchBookmarkPosts(
        limit: Int = 30,
        isRefresh: Boolean = false
    ) {
        if (isFetchingBookmarks.value) return

        viewModelScope.launch {
            if (isRefresh) isFetchingBookmarks.value = true
            error.value = null
            if (isRefresh) {
                bookmarkCursor = null
                hasMoreBookmarks.value = true
            }

            try {
                val response = retryWithBackoff {
                    api.getBookmarks(
                        GetBookmarksQueryParams(
                            limit = limit.toLong(),
                            cursor = bookmarkCursor
                        )
                    ).maybeResponse()
                }

                val newPosts = response?.bookmarks?.mapNotNull {
                    when (val item = it.item) {
                        is BookmarkViewItemUnion.PostView -> FeedViewPost(post = item.value)
                        else -> null
                    }
                } ?: emptyList()
                bookmarkCursor = response?.cursor
                hasMoreBookmarks.value = bookmarkCursor != null

                // Cache them
                newPosts.forEach { addCachedPost(it.post) }

                // Merge or set
                bookmarkPosts.value = if (isRefresh) {
                    newPosts
                } else {
                    bookmarkPosts.value + newPosts
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("FeedVM", "Error fetching bookmark posts", e)
                error.value = "Failed to load bookmarks: ${e.message}"
            } finally {
                isFetchingBookmarks.value = false
            }
        }
    }

    fun loadMoreBookmarkPosts(limit: Int = 30) {
        if (isFetchingBookmarks.value || bookmarkCursor == null) {
            if (bookmarkCursor == null) hasMoreBookmarks.value = false
            return
        }

        fetchBookmarkPosts(limit, isRefresh = false)
    }

    fun fetchProfilePosts(
        actor: Did,
        limit: Int = 30,
        isRefresh: Boolean = false
    ) {
        if (isFetchingProfile.value) return

        viewModelScope.launch {
            if (isRefresh) isFetchingProfile.value = true
            error.value = null
            if (isRefresh) {
                profileCursor = null
                hasMoreProfile.value = true
            }

            try {
                val response = retryWithBackoff {
                    api.getAuthorFeed(
                        GetAuthorFeedQueryParams(
                            actor = actor,
                            limit = limit.toLong(),
                            cursor = profileCursor,
                            includePins = true
                        )
                    ).maybeResponse()
                }

                // log cursor and new cursor
                Log.d("FeedVM", "Cursor: ${profileCursor}, New Cursor: ${response?.cursor}")
                // log how many new posts were fetched
                Log.d("FeedVM", "Fetched ${response?.feed?.size} new posts")


                val newPosts = response?.feed ?: emptyList()
                profileCursor = response?.cursor
                hasMoreProfile.value = profileCursor != null

                // Cache them
                newPosts.forEach { addCachedPost(it.post) }

                // Merge or set
                profilePosts.value = if (isRefresh) {
                    newPosts
                } else {
                    profilePosts.value + newPosts
                }

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("FeedVM", "Error fetching profile posts", e)
                error.value = "Failed to load profile: ${e.message}"
            } finally {
                isFetchingProfile.value = false
            }
        }
    }

    fun loadMoreProfilePosts(limit: Int = 30) {
        val assumedActor =
            profilePosts.value.firstOrNull { it.reason !is FeedViewPostReasonUnion.ReasonRepost }?.post?.author?.did
        if (isFetchingProfile.value || profileCursor == null || assumedActor == null) {
            if (profileCursor == null) hasMoreProfile.value = false
            return
        }

        fetchProfilePosts(assumedActor, limit, isRefresh = false)
    }

    private var lastFetchTime = 0L
    private var followingFeedCursor: String? = null

    suspend fun getPost(uri: String): PostView {
        val followingMatch = followingFeed.value?.find { it.post.uri.atUri == uri }
        val forYouMatch = forYouFeed.value?.find { it.post.uri.atUri == uri }
        val highlightsMatch = highlightsFeed.value?.find { it.first.postUri == uri }

        return if (followingMatch != null) {
            followingMatch.post
        } else if (forYouMatch != null) {
            forYouMatch.post
        } else if (highlightsMatch != null) {
            highlightsMatch.second
        } else {
            // Check cache first
            val cachedPost = getCachedPost(AtUri(uri))
            if (cachedPost != null) {
                return cachedPost
            }

            // If not in cache, fetch from API
            val fetchedPost = retryWithBackoff {
                api.getPosts(
                    GetPostsQueryParams(
                        uris = listOf(AtUri(uri))
                    )
                ).requireResponse().posts.first()
            }

            // Cache the fetched post
            addCachedPost(fetchedPost)
            fetchedPost
        }
    }

    @Composable
    fun usePost(uri: String): LoadResult<PostView> {
        var post by remember { mutableStateOf<PostView?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(uri) {
            isLoading = true
            error = null
            
            // 1. Try cache first
            val cachedPost = getCachedPost(AtUri(uri))
            if (cachedPost != null) {
                post = cachedPost
                isLoading = false
            }

            // 2. Always fetch fresh from API (or only if not in cache, but fresh is usually better)
            try {
                val fetchedPost = retryWithBackoff {
                    api.getPosts(
                        GetPostsQueryParams(
                            uris = listOf(AtUri(uri))
                        )
                    ).requireResponse().posts.firstOrNull()
                }
                
                if (fetchedPost != null) {
                    post = fetchedPost
                    addCachedPost(fetchedPost)
                } else if (post == null) {
                    error = "Post not found"
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("FeedVM", "Error fetching post $uri", e)
                if (post == null) {
                    error = e.message
                }
            } finally {
                isLoading = false
            }
        }

        return LoadResult<PostView>(
            value = post,
            isLoading = isLoading,
            error = error
        )
    }

    fun fetchFeed(limit: Long = 100, isRefresh: Boolean = false) {
        if (isFetchingFeed.value && !isRefresh) {
            Log.d("FeedVM", "Already fetching initial feed.")
            return
        }
        if (isFetchingMoreFollowing.value && !isRefresh) {
            Log.d("FeedVM", "Already fetching more posts, refresh delayed or ignored.")
            return
        }

        val now = System.currentTimeMillis()
        if (!isRefresh && (now - lastFetchTime < 5000)) {
            Log.d("FeedVM", "Fetch cooldown active.")
            return
        }

        viewModelScope.launch {
            isFetchingFeed.value = true
            if (isRefresh) {
                followingFeedCursor = null
                hasMoreFollowing.value = true
                hasMoreForYou.value = true
            }
            lastFetchTime = now
            error.value = null

            try {
                Log.d("FeedVM", "Fetching following feed. Refresh: $isRefresh")
                fetchFollowingPostsInternal(
                    limit = limit,
                    isLoadMore = false
                )

                Log.d("FeedVM", "Fetching For You feed.")
                fetchForYou(isLoadMore = false)
                fetchHighlightsPostsInternal()
            } catch (e: Exception) {
                if (e is CancellationException || e.message?.contains("cancel", ignoreCase = true) == true) throw e
                Log.e("FeedVM", "Error fetching feeds (Exception)", e)
                error.value = "Failed to fetch feeds: ${e.message}"
            } catch (t: Throwable) {
                if (t is CancellationException || t.message?.contains("cancel", ignoreCase = true) == true) throw t
                Log.e("FeedVM", "Error fetching feeds (Throwable)", t)
                error.value = "An unexpected error occurred: ${t.message}"
            } finally {
                isFetchingFeed.value = false
            }
        }
    }

    private suspend fun fetchFollowingPostsInternal(limit: Long, isLoadMore: Boolean) {
        val cursorToUse = if (isLoadMore) followingFeedCursor else null
        if (isLoadMore && cursorToUse == null && followingFeed.value?.isNotEmpty() == true) {
            Log.d("FeedVM", "No more items to load for following feed.")
            return
        }

        Log.d(
            "FeedVM",
            "Fetching following. LoadMore: $isLoadMore, Cursor: $cursorToUse, Limit: $limit"
        )
        val timelineResponse = retryWithBackoff {
            api.getTimeline(
                GetTimelineQueryParams(
                    cursor = cursorToUse,
                    limit = limit
                )
            )
        }
        
        Log.d("FeedVM", "Timeline response received")

        timelineResponse.maybeResponse().let { response ->
            if (response == null) {
                Log.w("FeedVM", "Timeline response body is null (error or empty)")
                if (!isLoadMore) {
                    followingFeed.value = emptyList()
                }
                return@let
            }


            val newPosts = response.feed
            val deduped = newPosts
                .groupBy { it.post.uri.atUri }
                .map { (_, entries) ->
                    entries.firstOrNull { it.reason != null }
                        ?: entries.first()
                }

            followingFeedCursor = response.cursor
            hasMoreFollowing.value = followingFeedCursor != null

            // Cache all posts
            deduped.forEach { addCachedPost(it.post) }

            if (isLoadMore) {
                followingFeed.value = (followingFeed.value ?: emptyList()) + deduped
            } else {
                followingFeed.value = deduped
            }
        }
    }

    fun loadMoreFollowingFeed(limit: Long = 50) {
        if (isFetchingFeed.value || isFetchingMoreFollowing.value) {
            Log.d("FeedVM", "Cannot load more: Another fetch operation is in progress.")
            return
        }
        if (followingFeedCursor == null && followingFeed.value?.isNotEmpty() == true) {
            Log.d("FeedVM", "No cursor available, cannot load more (all items loaded).")
            hasMoreFollowing.value = false
            return
        }

        viewModelScope.launch {
            isFetchingMoreFollowing.value = true
            error.value = null
            try {
                fetchFollowingPostsInternal(limit = limit, isLoadMore = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("FeedVM", "Error loading more following posts (Exception)", e)
                error.value = "Failed to load more posts: ${e.message}"
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.e("FeedVM", "Error loading more following posts (Throwable)", t)
                error.value = "An unexpected error occurred while loading more: ${t.message}"
            } finally {
                isFetchingMoreFollowing.value = false
            }
        }
    }

    var feeds = emptyList<Pair<SuggestedFeed, GetFeedGeneratorResponse>>()
    var cursors = mutableMapOf<AtUri, String>()

    private var forYouOffset = 0
    private val forYouPageSize = 20

    private suspend fun fetchForYou(isLoadMore: Boolean = false) {
        val box = ObjectBox.store.boxFor(PostScore::class.java)

        if (!isLoadMore) forYouOffset = 0

        val page = box.query()
            .order(PostScore_.finalScore, QueryBuilder.DESCENDING) // adjust to your actual property
            .build()
            .find(forYouOffset.toLong(), forYouPageSize.toLong())

        Log.d(
            "FeedVM",
            "Fetched ${page.size} for you posts. Offset: $forYouOffset"
        )

        if (page.isEmpty()) {
            Log.d("FeedVM", "No more ranked posts available.")
            hasMoreForYou.value = false
            if (!isLoadMore) forYouFeed.value = emptyList()
            return
        }

        val chunkedPosts = retryWithBackoff {
            api.fetchChunkedPosts(
                page.map { Pair(it, it.postUri) }
            )
        }

        addCachedPosts(chunkedPosts.map { it.second })

        val newPosts = chunkedPosts.map { (score, post) ->
            return@map ForYouPost(
                post = post,
                score = score
            )
        }

        Log.d(
            "FeedVM",
            "Fetched ${newPosts.size} for you posts."
        )

        forYouOffset += page.size
        hasMoreForYou.value = page.size == forYouPageSize

        forYouFeed.value = if (isLoadMore) {
            (forYouFeed.value ?: emptyList()) + newPosts
        } else {
            newPosts
        }
    }

    fun refreshForYouFeed(){
        forYouOffset = 0
        hasMoreForYou.value = true
        forYouFeed.value = emptyList()

        // fetch for you feed
        viewModelScope.launch {
            try {
                fetchForYou(false)
            } catch (t: Throwable){
                t.printStackTrace()
                error.value = "An unexpected error occurred while loading more: ${t.message}"
            }
        }
    }

    fun loadMoreForYouFeeds() {
        if (isFetchingForYou.value || isFetchingMoreForYou.value) {
            Log.d("FeedVM", "Cannot load more: Another fetch operation is in progress.")
            return
        }
        if (cursors.isEmpty() || cursors.values.all { it.isBlank() }) {
            Log.d("FeedVM", "No cursor available, cannot load more (all items loaded).")
            hasMoreForYou.value = false
            return
        }

        viewModelScope.launch {
            isFetchingMoreForYou.value = true
            error.value = null
            try {
                fetchForYou(isLoadMore = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("FeedVM", "Error loading more for you posts (Exception)", e)
                error.value = "Failed to load more posts: ${e.message}"
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.e("FeedVM", "Error loading more for you posts (Throwable)", t)
                error.value = "An unexpected error occurred while loading more: ${t.message}"
            } finally {
                isFetchingMoreForYou.value = false
            }
        }
    }

    private suspend fun fetchHighlightsPostsInternal() {
        val box = ObjectBox.store.boxFor(PostScore::class.java)
        val posts = box.all
        if (posts.isEmpty()) {
            Log.d(
                "FeedVM",
                "No posts found in ObjectBox for ForYou feed, fetching from API not possible with this logic."
            )
            highlightsFeed.value = emptyList()
            return
        }

        val chunkedPosts = retryWithBackoff {
            api.fetchChunkedPosts(
                posts.map { Pair(it, it.postUri) }
            )
        }

        // Cache all fetched posts
        chunkedPosts.forEach { (_, post) ->
            addCachedPost(post)
        }

        highlightsFeed.value = chunkedPosts
    }

    fun notifyPostCreated(authorDid: Did? = null) {
        fetchFeed(isRefresh = true)
        authorDid?.let {
            fetchProfilePosts(it, isRefresh = true)
            fetchProfileMetadata(it, force = true)
        }
    }

    fun notifyPostDeleted(target: AtUri) {
        profilePosts.value = profilePosts.value.filter { it.post.uri != target }
        bookmarkPosts.value = bookmarkPosts.value.filter { it.post.uri != target }
        followingFeed.value = followingFeed.value?.filter { it.post.uri != target }
        forYouFeed.value = forYouFeed.value?.filter { it.post.uri != target }
        customFeeds.forEach {
            customFeeds[it.key] = it.value.filter { post -> post.post.uri != target }
        }
    }

    fun notifyBookmarked(target: AtUri, isBookmarked: Boolean) {
        if (!isBookmarked) {
            bookmarkPosts.value = bookmarkPosts.value.filter { it.post.uri != target }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Optional: Clear memory cache when ViewModel is destroyed
        cachedPosts.clear()
    }
}

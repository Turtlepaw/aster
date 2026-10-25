package io.github.turtlepaw.mindsky.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.SearchActorsTypeaheadQueryParams
import app.bsky.feed.FeedViewPost
import app.bsky.feed.PostView
import app.bsky.feed.SearchPostsQueryParams
import app.bsky.unspecced.GetTrendsQueryParams
import app.bsky.unspecced.GetTrendsResponse
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FeedGeneratorDestination
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.Cache
import fyi.kittens.shared.R
import fyi.kittens.shared.ui.Avatar
import fyi.kittens.shared.ui.ContentAwareProvider
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import io.github.turtlepaw.mindsky.preferences.PreferenceProvider
import io.github.turtlepaw.fetch_and_cache.SingleLoadResult
import app.bsky.labeler.GetServicesResponseViewUnion
import app.bsky.unspecced.TrendView
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import androidx.compose.ui.tooling.preview.Preview
import app.bsky.unspecced.TrendViewStatus
import io.github.turtlepaw.mindsky.ui.theme.MindskyTheme
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import io.github.turtlepaw.mindsky.components.post.PostComponent
import kotlinx.coroutines.launch
import sh.christian.ozone.api.AtUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlin.collections.emptyList
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

@Serializable
sealed interface SearchType {
    data class User(val did: String) : SearchType
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Search(navigator: DestinationsNavigator) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val preferences = LocalPreferences.current
    val searchHistory by rememberPreference(AppPrefs.SearchHistory)
    val userSearchHistory by rememberPreference(AppPrefs.UserSearchHistory)

    fun addToHistory(query: String) {
        if (query.isBlank()) return
        val currentHistory = searchHistory.toMutableList()
        currentHistory.remove(query)
        currentHistory.add(0, query)
        val newHistory = currentHistory.take(10)
        preferences.set(AppPrefs.SearchHistory, newHistory)
    }

    fun addToUserHistory(actor: ProfileViewBasic) {
        try {
            val json = BlueskyJson.encodeToString(ProfileViewBasic.serializer(), actor)
            val current = userSearchHistory.toMutableList()
            current.removeAll { item ->
                try {
                    val parsed = BlueskyJson.decodeFromString(ProfileViewBasic.serializer(), item)
                    parsed.did == actor.did
                } catch (e: Exception) {
                    false
                }
            }
            current.add(0, json)
            preferences.set(AppPrefs.UserSearchHistory, current.take(10))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val api = LocalMindskyApi.current
    var actors by remember { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }
    var posts by remember { mutableStateOf<List<PostView>>(emptyList()) }
    var isSearchingActors by remember { mutableStateOf(false) }
    var isSearchingPosts by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Users, 1: Posts

    LaunchedEffect(textFieldState.text) {
        val query = textFieldState.text.toString()
        if (query.isBlank()) {
            actors = emptyList()
            posts = emptyList()
            return@LaunchedEffect
        }

        delay(300.milliseconds)

        isSearchingActors = true
        try {
            val response = api.searchActorsTypeahead(
                SearchActorsTypeaheadQueryParams(
                    q = query,
                    limit = 15,
                )
            )
            actors = response.requireResponse().actors
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        } finally {
            isSearchingActors = false
        }

        isSearchingPosts = true
        try {
            val response = api.searchPosts(
                SearchPostsQueryParams(
                    q = query,
                    limit = 15,
                )
            )
            posts = response.requireResponse().posts
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            e.printStackTrace()
        } finally {
            isSearchingPosts = false
        }
    }

    val trending = SingleCache.rememberLoad(
        fetcher = {
            api.getTrendsUnspecced(
                GetTrendsQueryParams(
                    limit = 5,
                )
            ).requireResponse()
        },
        serializer = GetTrendsResponse.serializer(),
        identifier = "trends",
        ttl = 3.hours
    )

    val localLabels = LocalLabelManager.current
    val definitions by localLabels.labelersDefinitionFlow.collectAsState()

    val recentUsers = userSearchHistory.mapNotNull { json ->
        try {
            BlueskyJson.decodeFromString(ProfileViewBasic.serializer(), json)
        } catch (e: Exception) {
            null
        }
    }

    SearchContent(
        searchBarState = searchBarState,
        textFieldState = textFieldState,
        searchHistory = searchHistory,
        recentUsers = recentUsers,
        actors = actors,
        posts = posts,
        isSearchingActors = isSearchingActors,
        isSearchingPosts = isSearchingPosts,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        trending = trending,
        definitions = definitions,
        navigator = navigator,
        onSearch = { query ->
            addToHistory(query)
            // Keep search open on enter so search results remain visible
        },
        onClearHistory = {
            preferences.set(AppPrefs.SearchHistory, emptyList())
        },
        onClearUserHistory = {
            preferences.set(AppPrefs.UserSearchHistory, emptyList())
        },
        onRemoveFromHistory = { query ->
            val newHistory = searchHistory.filter { it != query }
            preferences.set(AppPrefs.SearchHistory, newHistory)
        },
        onRemoveFromUserHistory = { actor ->
            val newHistory = userSearchHistory.filter { json ->
                try {
                    val parsed = BlueskyJson.decodeFromString(ProfileViewBasic.serializer(), json)
                    parsed.did != actor.did
                } catch (e: Exception) {
                    true
                }
            }
            preferences.set(AppPrefs.UserSearchHistory, newHistory)
        },
        onActorClick = { actor ->
            addToUserHistory(actor)
            scope.launch { searchBarState.animateToCollapsed() }
            navigator.navigate(
                ProfileDestination(actor.did.did)
            )
        },
        onTrendingTopicClick = { topic ->
            val regex = """/profile/([^/]+)/feed/([^/]+)""".toRegex()
            val match = regex.find(topic.link)

            val actorDid = match?.groupValues?.get(1)
            val id = match?.groupValues?.get(2)

            if (actorDid != null && id != null) {
                scope.launch { searchBarState.animateToCollapsed() }
                navigator.navigate(
                    FeedGeneratorDestination(
                        AtUri(
                            "at://${actorDid}/app.bsky.feed.generator/${id}"
                        )
                    )
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchContent(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    searchHistory: List<String>,
    recentUsers: List<ProfileViewBasic>,
    actors: List<ProfileViewBasic>,
    posts: List<PostView>,
    isSearchingActors: Boolean,
    isSearchingPosts: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    trending: SingleLoadResult<GetTrendsResponse>,
    definitions: Map<String, GetServicesResponseViewUnion>,
    navigator: DestinationsNavigator,
    onSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
    onClearUserHistory: () -> Unit,
    onRemoveFromHistory: (String) -> Unit,
    onRemoveFromUserHistory: (ProfileViewBasic) -> Unit,
    onActorClick: (ProfileViewBasic) -> Unit,
    onTrendingTopicClick: (TrendView) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = onSearch,
                placeholder = {
                    Text(modifier = Modifier.clearAndSetSemantics {}, text = "Search users and posts")
                },
                leadingIcon = {
                    if (searchBarState.currentValue == SearchBarValue.Expanded) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    searchBarState.animateToCollapsed()
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    } else {
                        Icon(Icons.Rounded.Search, stringResource(R.string.search))
                    }
                },
                trailingIcon = {
                    if (textFieldState.text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                textFieldState.setTextAndPlaceCursorAtEnd("")
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.clear_search)
                            )
                        }
                    }
                },
            )
        }

    Scaffold { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    SearchBar(state = searchBarState, inputField = inputField)
                }
            }
            item {
                Spacer(modifier = Modifier.height(18.dp))
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.TrendingUp,
                        contentDescription = "Trending",
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Trending",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (trending.isLoading) {
                item {
                    Text("Loading trending topics...")
                }
            } else if (trending.error != null) {
                item {
                    Text("Failed to load trending topics: ${trending.error}")
                }
            } else if (trending.value != null) {
                item {
                    val topics = trending.value!!.trends
                    HorizontalMultiBrowseCarousel(
                        state = rememberCarouselState { topics.count() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(top = 16.dp, bottom = 16.dp),
                        preferredItemWidth = 220.dp,
                        itemSpacing = 8.dp,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) { i ->
                        val topic = topics[i]
                        Surface(
                            modifier = Modifier
                                .height(205.dp)
                                .fillMaxWidth()
                                .clickable { onTrendingTopicClick(topic) }
                                .maskClip(MaterialTheme.shapes.extraLarge),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(
                                        8.dp,
                                        Alignment.CenterVertically
                                    ),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                CircleShape
                                            )
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            (i + 1).toString(),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        )
                                    }

                                    Text(
                                        text = topic.displayName
                                    )

                                    if (topic.category != null) Text(
                                        text = topic.category!!.capitalize(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
            LazyColumn {
                if (textFieldState.text.isEmpty()) {
                    if (recentUsers.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Users",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
                                    onClick = onClearUserHistory,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Clear All")
                                }
                            }
                        }
                        items(recentUsers, key = { it.did.did }) { actor ->
                            ContentAwareProvider(
                                labels = actor.labels,
                                labelDefinitions = definitions
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onActorClick(actor) }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Avatar(
                                        modifier = Modifier.size(36.dp),
                                        avatarUrl = actor.avatar?.uri,
                                        contentDescription = "${actor.displayName ?: actor.handle.handle}'s avatar",
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = actor.displayName ?: actor.handle.handle)
                                        Text(
                                            text = "@${actor.handle.handle}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveFromUserHistory(actor) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Remove from history",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }

                    if (searchHistory.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recent Searches",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = onClearHistory,
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Clear All")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    searchHistory.forEach { historyItem ->
                                        AssistChip(
                                            onClick = {
                                                textFieldState.setTextAndPlaceCursorAtEnd(historyItem)
                                            },
                                            label = { Text(historyItem) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.History,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(searchHistory) { query ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        textFieldState.setTextAndPlaceCursorAtEnd(query)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = query,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Recent search query",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveFromHistory(query) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Remove from history",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (recentUsers.isEmpty() && searchHistory.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "No recent searches",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Search for users or posts to see them here",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            SegmentedButton(
                                selected = selectedTab == 0,
                                onClick = { onTabSelected(0) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Users (${actors.size})")
                            }
                            SegmentedButton(
                                selected = selectedTab == 1,
                                onClick = { onTabSelected(1) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("Posts (${posts.size})")
                            }
                        }
                    }

                    if (selectedTab == 0) {
                        if (isSearchingActors) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Searching users...")
                                }
                            }
                        } else if (actors.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No users found")
                                }
                            }
                        } else {
                            items(actors) { actor ->
                                ContentAwareProvider(
                                    labels = actor.labels,
                                    labelDefinitions = definitions
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onActorClick(actor) }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start)
                                    ) {
                                        Avatar(
                                            modifier = Modifier.size(30.dp),
                                            avatarUrl = actor.avatar?.uri,
                                            contentDescription = "${actor.displayName ?: actor.handle.handle}'s avatar",
                                        )
                                        Column {
                                            Text(text = actor.displayName ?: actor.handle.handle)
                                            Text(
                                                text = "@${actor.handle.handle}",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (isSearchingPosts) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Searching posts...")
                                }
                            }
                        } else if (posts.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No posts found")
                                }
                            }
                        } else {
                            items(posts) { post ->
                                PostComponent(
                                    postView = FeedViewPost(post = post),
                                    navigator = navigator,
                                    onClick = {
                                        scope.launch { searchBarState.animateToCollapsed() }
                                        navigator.navigate(
                                            FullsizePostDestination(post.uri.atUri)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun SearchPreview() {
    PreferenceProvider {
        MindskyTheme {
            SearchContent(
                searchBarState = rememberSearchBarState(),
                textFieldState = rememberTextFieldState(),
                searchHistory = listOf("Bluesky", "Mindsky", "Kotlin"),
                recentUsers = emptyList(),
                actors = emptyList(),
                posts = emptyList(),
                isSearchingActors = false,
                isSearchingPosts = false,
                selectedTab = 0,
                onTabSelected = {},
                trending = SingleLoadResult(
                    value = GetTrendsResponse(
                        trends = listOf(
                            TrendView(
                                topic = "mindsky",
                                displayName = "Mindsky",
                                link = "/profile/did:plc:qrz3lhbyuxbeilrc6nekdqme/feed/355b75f63684",
                                startedAt = Instant.fromEpochMilliseconds(0),
                                postCount = 100L,
                                status = TrendViewStatus.Hot,
                                category = "preview",
                                actors = emptyList()
                            )
                        )
                    ),
                    isLoading = false,
                    refresh = null,
                    error = null
                ),
                definitions = emptyMap(),
                navigator = EmptyDestinationsNavigator,
                onSearch = {},
                onClearHistory = {},
                onClearUserHistory = {},
                onRemoveFromHistory = {},
                onRemoveFromUserHistory = {},
                onActorClick = {},
                onTrendingTopicClick = {}
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun SearchExpandedPreview() {
    PreferenceProvider {
        MindskyTheme {
            val searchBarState = rememberSearchBarState()
            LaunchedEffect(Unit) {
                searchBarState.animateToExpanded()
            }
            SearchContent(
                searchBarState = searchBarState,
                textFieldState = rememberTextFieldState(),
                searchHistory = listOf("Bluesky", "Mindsky", "Kotlin"),
                recentUsers = emptyList(),
                actors = emptyList(),
                posts = emptyList(),
                isSearchingActors = false,
                isSearchingPosts = false,
                selectedTab = 0,
                onTabSelected = {},
                trending = SingleLoadResult(
                    value = GetTrendsResponse(
                        trends = listOf(
                            TrendView(
                                topic = "mindsky",
                                displayName = "Mindsky",
                                link = "/profile/did:plc:qrz3lhbyuxbeilrc6nekdqme/feed/355b75f63684",
                                startedAt = Instant.fromEpochMilliseconds(0),
                                postCount = 100L,
                                status = TrendViewStatus.Hot,
                                category = "preview",
                                actors = emptyList()
                            )
                        )
                    ),
                    isLoading = false,
                    refresh = null,
                    error = null
                ),
                definitions = emptyMap(),
                navigator = EmptyDestinationsNavigator,
                onSearch = {},
                onClearHistory = {},
                onClearUserHistory = {},
                onRemoveFromHistory = {},
                onRemoveFromUserHistory = {},
                onActorClick = {},
                onTrendingTopicClick = {}
            )
        }
    }
}

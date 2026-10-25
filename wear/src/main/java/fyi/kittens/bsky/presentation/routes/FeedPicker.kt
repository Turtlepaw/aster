package fyi.kittens.bsky.presentation.routes

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SplitRadioButton
import androidx.wear.compose.material3.Text
import app.bsky.actor.PreferencesUnion
import app.bsky.actor.ProfileViewDetailed
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.GetFeedGeneratorsQueryParams
import app.bsky.graph.GetListQueryParams
import app.bsky.graph.ListView
import coil3.compose.AsyncImage
import fyi.kittens.bsky.presentation.ErrorScreen
import fyi.kittens.bsky.presentation.LoadingScreen
import fyi.kittens.bsky.presentation.LocalFeeds
import fyi.kittens.bsky.presentation.LocalSelectedFeedIndex
import fyi.kittens.bsky.presentation.LocalSession
import fyi.kittens.bsky.presentation.components.post.Checkmark
import fyi.kittens.bsky.presentation.components.post.Robot
import fyi.kittens.shared.di.LocalMindskyApi
import fyi.kittens.shared.ui.Avatar
import io.github.turtlepaw.fetch_and_cache.SingleCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.AtUri
import kotlin.collections.orEmpty

@Keep
@Serializable
sealed class FeedItem {
    abstract fun getName(): String
    abstract fun getUri(): String

    @Keep
    @Serializable
    data class FeedGenerator(val generator: RecordViewRecordUnion.FeedGeneratorView) : FeedItem() {
        override fun getName(): String {
            return generator.value.displayName
        }

        override fun getUri(): String {
            return generator.value.uri.atUri
        }
    }

    @Keep
    @Serializable
    data class List(val list: ListView) : FeedItem() {
        override fun getName(): String {
            return list.name
        }

        override fun getUri(): String {
            return list.uri.atUri
        }
    }

    @Keep
    @Serializable
    data object Following : FeedItem() {
        override fun getName(): String {
            return "Following"
        }

        override fun getUri(): String {
            return "following"
        }
    }
}

@Composable
fun rememberSelectedFeed(did: String?): MutableState<Int?> {
    val selectedFeed = remember(did) { mutableStateOf<Int?>(null) }
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("bsky", Context.MODE_PRIVATE)
    val key = "selected_feed_index_${did ?: "default"}"

    LaunchedEffect(did) {
        selectedFeed.value = sharedPreferences.getInt(key, -1)
    }

    LaunchedEffect(selectedFeed.value, did) {
        sharedPreferences.edit {
            putInt(key, selectedFeed.value ?: -1)
        }
    }

    return selectedFeed
}

@Composable
fun rememberPinnedFeeds(
    did: String?,
    apiProvider: () -> BlueskyApi?
): SingleCache<List<Pair<String, FeedItem>>> {
    return remember(did) {
        SingleCache(
            fetcher = {
                val api = apiProvider() ?: throw IllegalStateException("API not available")
                coroutineScope {
                    val prefs = api.getPreferencesForActor().requireResponse()
                    Log.d("FeedPickerScreen", "Prefs: $prefs")
                    val savedFeedUris = when (val pref =
                        prefs.preferences.find { it is PreferencesUnion.SavedFeedsPrefV2 || it is PreferencesUnion.SavedFeedsPref }) {
                        is PreferencesUnion.SavedFeedsPrefV2 -> pref.value.items.filter { it.pinned }
                            .map { it.value }

                        is PreferencesUnion.SavedFeedsPref -> pref.value.pinned.map { it.toString() }
                        else -> emptyList()
                    }

                    val fetchedGenerators = savedFeedUris
                        .filter { "app.bsky.feed.generator" in it }
                        .map { AtUri(it) }
                        .takeIf { it.isNotEmpty() }
                        ?.let {
                            api.getFeedGenerators(GetFeedGeneratorsQueryParams(feeds = it))
                                .requireResponse().feeds
                        }
                        ?.associateBy { it.uri.atUri }.orEmpty()

                    val fetchedLists = savedFeedUris
                        .filter { "app.bsky.graph.list" in it }
                        .map { AtUri(it) }
                        .map {
                            async {
                                api.getList(GetListQueryParams(list = it, limit = 1))
                                    .requireResponse().list
                            }
                        }
                        .awaitAll()
                        .associateBy { it.uri.atUri }

                    savedFeedUris.mapNotNull { uri ->
                        val feed = when {
                            uri == "following" -> FeedItem.Following
                            "app.bsky.feed.generator" in uri -> fetchedGenerators[uri]?.let {
                                FeedItem.FeedGenerator(
                                    RecordViewRecordUnion.FeedGeneratorView(it)
                                )
                            }

                            "app.bsky.graph.list" in uri -> fetchedLists[uri]?.let { FeedItem.List(it) }
                            else -> null
                        }
                        feed?.let { uri to it }
                    }
                }
            },
            serializer = ListSerializer(PairSerializer(String.serializer(), FeedItem.serializer())),
            identifier = "saved_feeds:${did}",
        )
    }
}

@Composable
fun FeedPickerScreen(navController: NavController) {
    val api = LocalMindskyApi.current
    val currentApi = rememberUpdatedState(api)
    val session = LocalSession.current
    val scrollState = rememberScalingLazyListState()
    val pinnedFeeds = LocalFeeds.current
    val pinnedFeed = LocalSelectedFeedIndex.current

    if (pinnedFeeds.isLoading) {
        LoadingScreen()
    } else if(pinnedFeeds.error != null){
        ErrorScreen("Failed to load pinned feeds")
    } else if(pinnedFeeds.value != null) {
        ScreenScaffold(
            modifier = Modifier.fillMaxSize(),
            scrollState = scrollState,
        ) { contentPadding ->
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                autoCentering = null,
                contentPadding = contentPadding,
                state = scrollState
            ) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
                itemsIndexed(pinnedFeeds.value!!){ index, it ->
                    val isSelected = index == pinnedFeed.value || (pinnedFeed.value == -1 && it.second is FeedItem.Following)
                    SplitRadioButton(
                        selected = isSelected,
                        onContainerClick = {
                            pinnedFeed.value = index
                            navController.popBackStack()
                        },
                        onSelectionClick = {
                            pinnedFeed.value = index
                            navController.popBackStack()
                        },
                        selectionContentDescription = "Select feed ${it.second}",
                        label = {
                            Text(
                                text = it.second.getName(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
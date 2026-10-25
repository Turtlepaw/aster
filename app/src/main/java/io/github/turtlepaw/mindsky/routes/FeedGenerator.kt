package io.github.turtlepaw.mindsky.routes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bsky.feed.FeedViewPost
import app.bsky.feed.GeneratorView
import app.bsky.feed.GetFeedGeneratorQueryParams
import app.bsky.feed.GetFeedQueryParams
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.Cache
import io.github.turtlepaw.fetch_and_cache.InfiniteCache
import fyi.kittens.shared.ui.Avatar
import fyi.kittens.shared.ui.LoadMore
import io.github.turtlepaw.mindsky.components.post.PostComponent
import fyi.kittens.shared.ui.PostDensity
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.routes.settings.TopAppBarCommon
import sh.christian.ozone.api.AtUri

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun FeedGenerator(navigator: DestinationsNavigator, feed: AtUri) {
    val api = LocalMindskyApi.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val content = InfiniteCache<FeedViewPost>(
        fetcher = {
            val response = api.getFeed(
                GetFeedQueryParams(
                    feed = feed,
                    limit = 20,
                    cursor = it
                )
            ).requireResponse()

            response.cursor to response.feed
        },
        identifier = feed.atUri
            .replace("://", "_")
            .replace("/", "_")
            .replace(":", "_"),
        serializer = FeedViewPost.serializer(),
        context = context
    ).load()

    val feedGenerator = Cache<Any?, GeneratorView>(
        fetcher = {
            val response = api.getFeedGenerator(
                GetFeedGeneratorQueryParams(
                    feed = feed,
                )
            ).requireResponse()

            response.view
        },
        identifier = feed.atUri
            .replace("://", "_")
            .replace("/", "_")
            .replace(":", "_") + "_info",
        serializer = GeneratorView.serializer(),
        context = context
    ).load(Unit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if(feedGenerator.isLoading || feedGenerator.value == null){
                        Text("Loading...")
                    } else {
                        Row() {
                            if(feedGenerator.value!!.avatar != null){
                                Avatar(
                                    avatarUrl = feedGenerator.value!!.avatar!!.uri,
                                    contentDescription = "Feed generator avatar",
                                    clip = MaterialTheme.shapes.medium,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column() {
                                Text(
                                    feedGenerator.value!!.displayName,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "by @${feedGenerator.value!!.creator.handle.handle}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    TopAppBarCommon.BackButton(navigator)
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = paddingValues,
        ) {
            if(content.isLoading){
                Loading()
            } else if(content.error != null){
                item {
                    Text("Error: ${content.error}")
                }
            } else if(!content.value.isNullOrEmpty()) {
                items(content.value!!) {
                    PostComponent(
                        postView = it,
                        navigator = navigator,
                        density = PostDensity.Regular,
                    ){
                        navigator.navigate(FullsizePostDestination(it.post.uri.atUri))
                    }
                }
                if (content.hasMore) {
                    item {
                        LoadMore(content.value?.size ?: 0) {
                            coroutineScope.launch {
                                content.loadMore?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }
}

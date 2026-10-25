package io.github.turtlepaw.mindsky.routes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.R
import fyi.kittens.shared.ui.LoadMore
import io.github.turtlepaw.mindsky.components.GenericTopBar
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Bookmarks(
    navigator: DestinationsNavigator
) {
    val viewModel = LocalFeedModel.current
    val posts by viewModel.bookmarkPosts
    val isLoading by viewModel.isFetchingBookmarks
    val hasMore by viewModel.hasMoreBookmarks
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        if (posts.isEmpty()) {
            viewModel.fetchBookmarkPosts(isRefresh = true)
        }
    }

    Scaffold(
        topBar = {
            GenericTopBar(
                title = stringResource(R.string.bookmark),
                listState = listState,
                navigator = navigator
            )
        }
    ) { contentPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = {
                coroutineScope.launch {
                    viewModel.fetchBookmarkPosts(isRefresh = true)
                }
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isLoading,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = contentPadding.calculateTopPadding())
                        .zIndex(5f)
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0.5f)
            ) {
                if (isLoading && posts.isEmpty()) {
                    Loading()
                } else if (posts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No bookmarks found")
                        }
                    }
                } else {
                    items(
                        items = posts,
                        key = { it.post.uri.atUri }
                    ) {
                        PostComponent(
                            it,
                            navigator
                        ) {
                            navigator.navigate(
                                FullsizePostDestination(postUri = it.post.uri.atUri)
                            )
                        }
                    }

                    if (hasMore) {
                        item {
                            LoadMore(posts.size) {
                                viewModel.loadMoreBookmarkPosts()
                            }
                        }
                    }
                }
            }
        }
    }
}

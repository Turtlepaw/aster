package io.github.turtlepaw.mindsky.routes.post

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.bsky.feed.ThreadViewPost
import app.bsky.feed.ThreadViewPostParentUnion
import app.bsky.feed.ThreadViewPostReplieUnion
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ComposerDestination
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.R
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.components.post.PostComponent
import fyi.kittens.shared.ui.PostDensity
import io.github.turtlepaw.mindsky.components.ErrorMessage
import io.github.turtlepaw.mindsky.components.GenericTopBar
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalProfileModel
import io.github.turtlepaw.mindsky.di.LocalScrollToTop
import io.github.turtlepaw.mindsky.routes.settings.TopAppBarCommon
import kotlinx.coroutines.launch
import kotlin.math.min

@Destination<RootGraph>
@Composable
fun FullsizePost(navigator: DestinationsNavigator, postUri: String) {
    val feedModel = LocalFeedModel.current
    val threadResult = rememberThread(postUri, depth = FullThreadDepth)
    val thread = threadResult.value
    val post = feedModel.usePost(postUri)

    val parents = remember(thread) {
        val list = mutableListOf<ThreadViewPost>()
        var current = thread?.parent
        while (current != null) {
            when (current) {
                is ThreadViewPostParentUnion.ThreadViewPost -> {
                    list.add(0, current.value)
                    current = current.value.parent
                }
                else -> {
                    current = null
                }
            }
        }
        list
    }

    val replyList = remember(thread) {
        thread?.replies.orEmpty()
            .filterIsInstance<ThreadViewPostReplieUnion.ThreadViewPost>()
            .map { it.value }
    }
    val listState = rememberLazyListState()
    val scrollToTopHandler = LocalScrollToTop.current
    val coroutineScope = rememberCoroutineScope()
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    var visibleCount by remember(replyList.size) {
        mutableIntStateOf(min(replyList.size, ReplyPageSize))
    }
    val visibleReplies = remember(replyList, visibleCount) { replyList.take(visibleCount) }
    val shouldLoadMore by remember(listState, visibleCount, replyList.size) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= visibleCount && visibleCount < replyList.size
        }
    }

    DisposableEffect(listState) {
        val handler: () -> Unit = {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
        }
        scrollToTopHandler.value = handler
        onDispose {
            if (scrollToTopHandler.value === handler) {
                scrollToTopHandler.value = null
            }
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleCount = min(visibleCount + ReplyPageSize, replyList.size)
        }
    }

    Scaffold(
        topBar = {
            GenericTopBar(
                title = stringResource(R.string.post),
                navigator = navigator,
                listState = listState
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = paddingValues,
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            state = listState
        ) {
            itemsIndexed(parents, key = { _, it -> it.post.uri.atUri }) { index, parent ->
                Box {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val verticalX = 37.dp.toPx()
                        val centerY = 67.dp.toPx()
                        if (index > 0) {
//                            drawLine(
//                                color = lineColor,
//                                start = Offset(verticalX, 0f),
//                                end = Offset(verticalX, centerY),
//                                strokeWidth = 2.dp.toPx()
//                            )
                        }
                        drawLine(
                            color = lineColor,
                            start = Offset(verticalX, centerY),
                            end = Offset(verticalX, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    PostComponent(
                        parent.post,
                        navigator,
                        density = PostDensity.Regular,
                        showSeparator = false
                    ) {
                        navigator.navigate(FullsizePostDestination(parent.post.uri.atUri))
                    }
                }
            }

            item {
                Box {
//                    if (parents.isNotEmpty() || replyList.isNotEmpty()) {
//                        Canvas(modifier = Modifier.matchParentSize()) {
//                            val verticalX = 37.dp.toPx()
//                            val centerY = 37.dp.toPx()
//                            if (parents.isNotEmpty()) {
//                                drawLine(
//                                    color = lineColor,
//                                    start = Offset(verticalX, 0f),
//                                    end = Offset(verticalX, centerY),
//                                    strokeWidth = 2.dp.toPx()
//                                )
//                            }
//                            if (replyList.isNotEmpty()) {
//                                drawLine(
//                                    color = lineColor,
//                                    start = Offset(verticalX, centerY),
//                                    end = Offset(verticalX, size.height),
//                                    strokeWidth = 2.dp.toPx()
//                                )
//                            }
//                        }
//                    }
                    if (post.value != null) {
                        PostComponent(
                            post.value!!,
                            navigator,
                            enabled = false,
                            density = PostDensity.Expanded,
                            showSeparator = false
                        ) {}
                    } else if (post.isLoading || (threadResult.isLoading && thread == null)) {
                        // Optionally show a loading state or error message
                        LoadingPost()
                    } else {
                        ErrorMessage(post.error ?: "Failed to load post")
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth()) {
//                    if (replyList.isNotEmpty()) {
//                        Canvas(modifier = Modifier.matchParentSize()) {
//                            val verticalX = 37.dp.toPx()
//                            drawLine(
//                                color = lineColor,
//                                start = Offset(verticalX, 0f),
//                                end = Offset(verticalX, size.height),
//                                strokeWidth = 2.dp.toPx()
//                            )
//                        }
//                    }
                    OutlinedButton(
                        onClick = {
                            navigator.navigate(ComposerDestination(postUri))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Reply,
                                contentDescription = "Reply",
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Reply to post",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            if (threadResult.isLoading && replyList.isEmpty()) {
                item {
                    LoadingPost()
                }
            }

            itemsIndexed(visibleReplies, key = { _, it -> it.post.uri.atUri }) { index, reply ->
                RenderReply(
                    reply = ThreadViewPostReplieUnion.ThreadViewPost(reply),
                    navigator = navigator,
                    rootPostUri = postUri,
                    depth = 1,
                    depthLimit = MaxNestedDepth,
                    isLastReply = (index == visibleReplies.size - 1) && (visibleCount >= replyList.size)
                )
            }

            if (visibleCount < replyList.size) {
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val verticalX = 37.dp.toPx()
                            drawLine(
                                color = lineColor,
                                start = Offset(verticalX, 0f),
                                end = Offset(verticalX, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                        Box(modifier = Modifier.padding(start = 32.dp)) {
                            LoadingPost()
                        }
                    }
                }
            }
        }
    }
}


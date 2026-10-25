package io.github.turtlepaw.mindsky.routes.post

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.ReplyAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import app.bsky.feed.GetPostThreadQueryParams
import app.bsky.feed.GetPostThreadResponseThreadUnion
import app.bsky.feed.PostView
import app.bsky.feed.ThreadViewPost
import app.bsky.feed.ThreadViewPostParentUnion
import app.bsky.feed.ThreadViewPostReplieUnion
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.generated.destinations.RepliesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.fetch_and_cache.SingleLoadResult
import fyi.kittens.shared.R
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.components.post.PostComponent
import fyi.kittens.shared.ui.PostDensity
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.routes.settings.TopAppBarCommon
import sh.christian.ozone.api.AtUri
import kotlin.math.max
import kotlin.math.min

internal const val FullThreadDepth = 10
internal const val ReplyPageSize = 15
internal const val MaxNestedDepth = 3

@Composable
internal fun rememberThread(postUri: String, depth: Int): SingleLoadResult<ThreadViewPost> {
    val api = LocalMindskyApi.current
    return SingleCache.rememberLoad(
        fetcher = {
            val result = api.getPostThread(
                GetPostThreadQueryParams(
                    uri = AtUri(postUri),
                    depth = depth.toLong(),
                    parentHeight = 80L
                )
            ).requireResponse().thread

            (result as? GetPostThreadResponseThreadUnion.ThreadViewPost)?.value
                ?: throw Exception("Post not found or blocked")
        },
        serializer = ThreadViewPost.serializer(),
        identifier = "${postUri}_thread_depth_$depth",
    )
}

@Destination<RootGraph>
@Composable
fun Replies(navigator: DestinationsNavigator, postUri: String, focusReplyUri: String? = null) {
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
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    val resolvedFocusUri = remember(replyList, focusReplyUri) {
        focusReplyUri ?: findLastReplyUri(replyList)
    }
    val focusIndex = remember(replyList, resolvedFocusUri) {
        resolvedFocusUri?.let { findTopLevelIndex(replyList, it) } ?: -1
    }

    val baseCount = remember(replyList.size) {
        min(replyList.size, ReplyPageSize)
    }
    var visibleCount by remember(replyList.size, focusIndex) {
        mutableIntStateOf(
            if (focusIndex >= 0) max(baseCount, focusIndex + 1) else baseCount
        )
    }
    val visibleReplies = remember(replyList, visibleCount) { replyList.take(visibleCount) }
    val shouldLoadMore by remember(listState, visibleCount, replyList.size) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleIndex >= visibleCount && visibleCount < replyList.size
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            visibleCount = min(visibleCount + ReplyPageSize, replyList.size)
        }
    }

    LaunchedEffect(focusIndex, visibleCount, replyList.size, parents.size) {
        if (focusIndex in 0 until visibleCount) {
            listState.scrollToItem(focusIndex + parents.size + 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(
                navigator,
                R.string.replies,
                MaterialTheme.typography.titleMedium
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.Top,
            state = listState
        ) {
            itemsIndexed(parents, key = { _, it -> it.post.uri.atUri }) { index, parent ->
                Box {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val verticalX = 37.dp.toPx()
                        val centerY = 37.dp.toPx()
                        if (index > 0) {
                            drawLine(
                                color = lineColor,
                                start = Offset(verticalX, 0f),
                                end = Offset(verticalX, centerY),
                                strokeWidth = 2.dp.toPx()
                            )
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
                    if (parents.isNotEmpty() || replyList.isNotEmpty()) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val verticalX = 37.dp.toPx()
                            val centerY = 37.dp.toPx()
                            if (parents.isNotEmpty()) {
                                drawLine(
                                    color = lineColor,
                                    start = Offset(verticalX, 0f),
                                    end = Offset(verticalX, centerY),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            if (replyList.isNotEmpty()) {
                                drawLine(
                                    color = lineColor,
                                    start = Offset(verticalX, centerY),
                                    end = Offset(verticalX, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                    }
                    if (post.value != null) {
                        PostComponent(
                            post.value!!,
                            navigator,
                            enabled = false,
                            density = PostDensity.Expanded,
                            showSeparator = false
                        ) {}
                    } else if (post.isLoading || (threadResult.isLoading && thread == null)) {
                        LoadingPost()
                    } else {
                        Text("Failed to load post")
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

@Composable
internal fun MoreRepliesRow(
    replyCount: Int,
    onClick: () -> Unit,
    isLastReply: Boolean = false
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            val strokeWidth = 2.dp.toPx()
            val color = lineColor
            val verticalX = 37.dp.toPx()
            val elbowY = size.height / 2
            val horizontalEndX = 54.dp.toPx()

            // Vertical line (parent's thread)
            val verticalEndY = if (isLastReply) elbowY else size.height
            drawLine(
                color = color,
                start = Offset(verticalX, 0f),
                end = Offset(verticalX, verticalEndY),
                strokeWidth = strokeWidth
            )

            // Horizontal connector
            drawLine(
                color = color,
                start = Offset(verticalX, elbowY),
                end = Offset(horizontalEndX, elbowY),
                strokeWidth = strokeWidth
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(42.dp))
            Row(
                modifier = Modifier
                    .clickable(onClick = onClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ReplyAll,
                    contentDescription = "More replies",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (replyCount > 0) "Read $replyCount more replies" else "Read more replies",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
internal fun PostReplies(
    replies: List<ThreadViewPostReplieUnion.ThreadViewPost>,
    navigator: DestinationsNavigator,
    rootPostUri: String,
    depth: Int,
    depthLimit: Int,
    isLastReply: Boolean
) {
    Column {
        replies.forEachIndexed { index, reply ->
            RenderReply(
                reply = reply,
                navigator = navigator,
                rootPostUri = rootPostUri,
                depth = depth,
                depthLimit = depthLimit,
                isLastReply = isLastReply && (index == replies.size - 1)
            )
        }
    }
}

@Composable
internal fun RenderReply(
    reply: ThreadViewPostReplieUnion.ThreadViewPost,
    navigator: DestinationsNavigator,
    rootPostUri: String,
    depth: Int,
    depthLimit: Int,
    isLastReply: Boolean = false
) {
    val childReplies = reply.value.replies.orEmpty()
        .filterIsInstance<ThreadViewPostReplieUnion.ThreadViewPost>()

    val lineColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Thread indicator drawn BEHIND everything
        if(depth > 1) Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            val strokeWidth = 2.dp.toPx()
            val color = lineColor

            val verticalX = 37.dp.toPx()  // X position of vertical line (align with parent avatar center)
            val elbowY = 37.dp.toPx()      // Y position where elbow bends (align with child avatar center)
            val horizontalEndX = 69.dp.toPx()  // Where horizontal line ends (at child avatar center)

            // Vertical line from top to elbow
            val verticalEndY = if (isLastReply) elbowY else size.height
            drawLine(
                color = color,
                start = Offset(verticalX, 0f),
                end = Offset(verticalX, verticalEndY),
                strokeWidth = strokeWidth
            )

            // Horizontal line (elbow) extending to the avatar
            drawLine(
                color = color,
                start = Offset(verticalX, elbowY),
                end = Offset(horizontalEndX, elbowY),
                strokeWidth = strokeWidth
            )
        }

        // The actual post content on top
        Row {
            if(depth > 1) Spacer(
                modifier = Modifier
                    .width(32.dp)
            )
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    if (childReplies.isNotEmpty()) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val verticalX = 37.dp.toPx()
                            drawLine(
                                color = lineColor,
                                start = Offset(verticalX, 37.dp.toPx()),
                                end = Offset(verticalX, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                    PostComponent(
                        postView = reply.value.post,
                        navigator = navigator,
                        density = PostDensity.Regular,
                        showReply = false,
                        showSeparator = childReplies.isEmpty()
                    ) {
                        navigator.navigate(
                            FullsizePostDestination(reply.value.post.uri.atUri)
                        )
                    }
                }

                if (childReplies.isNotEmpty()) {
                    if (depth >= depthLimit) {
                        val focusUri = reply.value.post.uri.atUri
                        MoreRepliesRow(
                            replyCount = childReplies.size,
                            onClick = {
                                navigator.navigate(
                                    FullsizePostDestination(
                                        postUri = focusUri,
                                        //focusReplyUri = null
                                    )
                                )
                            },
                            isLastReply = true // Since this is the only thing under the parent here
                        )
                    } else {
                        PostReplies(
                            replies = childReplies,
                            navigator = navigator,
                            rootPostUri = rootPostUri,
                            depth = depth + 1,
                            depthLimit = depthLimit,
                            isLastReply = true
                        )
                    }
                }
            }
        }
    }
}

private fun findLastReplyUri(replies: List<ThreadViewPost>): String? {
    var lastUri: String? = null
    for (reply in replies) {
        val nested = reply.replies.orEmpty()
            .filterIsInstance<ThreadViewPostReplieUnion.ThreadViewPost>()
            .map { it.value }
        val nestedUri = findLastReplyUri(nested)
        lastUri = nestedUri ?: reply.post.uri.atUri
    }
    return lastUri
}

private fun findTopLevelIndex(replies: List<ThreadViewPost>, targetUri: String): Int {
    replies.forEachIndexed { index, reply ->
        if (containsReply(reply, targetUri)) {
            return index
        }
    }
    return replies.lastIndex
}

private fun containsReply(reply: ThreadViewPost, targetUri: String): Boolean {
    return reply.post.uri.atUri == targetUri || reply.replies.orEmpty()
        .filterIsInstance<ThreadViewPostReplieUnion.ThreadViewPost>()
        .any { containsReply(it.value, targetUri) }
}

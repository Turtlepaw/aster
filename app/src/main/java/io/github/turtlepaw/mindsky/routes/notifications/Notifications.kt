package io.github.turtlepaw.mindsky.routes.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import app.bsky.feed.Post
import app.bsky.feed.PostView
import app.bsky.notification.ListNotificationsNotification
import app.bsky.notification.ListNotificationsQueryParams
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.InfiniteCache
import fyi.kittens.shared.R
import fyi.kittens.shared.ui.Avatar
import fyi.kittens.shared.ui.LoadMore
import io.github.turtlepaw.mindsky.components.ErrorMessage
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.GenericTopBar
import fyi.kittens.shared.ui.PostDensity
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalNotificationCountProvider
import io.github.turtlepaw.mindsky.di.LocalScrollToTop
import io.github.turtlepaw.mindsky.routes.Loading
import io.github.turtlepaw.mindsky.utils.ApiUtils.fetchChunkedPosts
import kotlinx.coroutines.launch
import sh.christian.ozone.api.AtUri

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun Notifications(navigator: DestinationsNavigator) {
    val api = LocalMindskyApi.current
    val feedModel = LocalFeedModel.current
    val context = LocalContext.current
    val notifications =
        remember(
            api
        ) {
            InfiniteCache<ListNotificationsNotification>(
                fetcher = { cursor ->
                    val result = api.listNotifications(
                        ListNotificationsQueryParams(
                            limit = 50,
                            cursor = cursor,
                        )
                    ).requireResponse()

                    result.cursor to result.notifications
                },
                serializer = ListNotificationsNotification.serializer(),
                identifier = "notifications_cache",
                context = context
            )
        }
            .load()

    val scrollToTopHandler = LocalScrollToTop.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    DisposableEffect(listState) {
        val handler: () -> Unit = {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
                notifications.refresh?.invoke()
            }
            Unit
        }
        scrollToTopHandler.value = handler
        onDispose {
            if (scrollToTopHandler.value === handler) {
                scrollToTopHandler.value = null
            }
        }
    }

    val notificationItems = remember(notifications.value) {
        buildNotificationItems(notifications.value ?: emptyList())
    }
    val postLookup = remember { mutableStateMapOf<String, PostView>() }
    var isPostsLoading by remember { mutableStateOf(false) }
    var postsError by remember { mutableStateOf<String?>(null) }

    val notificationCount = LocalNotificationCountProvider.current
    LaunchedEffect(Unit) {
        notificationCount.refresh?.let { it() }
    }

    LaunchedEffect(notificationItems) {
        postsError = null
        val targetUris = notificationItems
            .mapNotNull { notificationPostUri(it) }
            .filter { it.isPostUri() }
            .distinctBy { it.atUri }

        val missing = mutableListOf<Pair<AtUri, String>>()
        for (uri in targetUris) {
            if (postLookup.containsKey(uri.atUri)) continue
            val cached = feedModel.getCachedPost(uri)
            if (cached != null) {
                postLookup[uri.atUri] = cached
            } else {
                missing.add(uri to uri.atUri)
            }
        }

        if (missing.isEmpty()) {
            isPostsLoading = false
            return@LaunchedEffect
        }

        isPostsLoading = true
        try {
            val fetched = api.fetchChunkedPosts(missing)
            val fetchedPosts = fetched.map { it.second }
            fetchedPosts.forEach { post ->
                postLookup[post.uri.atUri] = post
            }
            feedModel.addCachedPosts(fetchedPosts)
        } catch (e: Exception) {
            postsError = e.message
        } finally {
            isPostsLoading = false
        }
    }

    Scaffold(
        topBar = {
            GenericTopBar(
                title = stringResource(R.string.notifications),
                listState = listState,
                //navigator = navigator
            )
        },
    ) { paddingValues ->
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = notifications.isLoading,
            onRefresh = {
                coroutineScope.launch {
                    notifications.refresh?.invoke()
                }
            },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = notifications.isLoading,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = paddingValues.calculateTopPadding())
                        .zIndex(5f)
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0.5f)
            ) {
                item {
                    Spacer(modifier = Modifier.height(130.dp))
                }
                if (notifications.isLoading && notifications.value.isNullOrEmpty()) {
                    Loading()
                } else if (notifications.error != null) {
                    item {
                        Text("Error: ${notifications.error}")
                    }
                } else {
                    items(
                        notificationItems,
                        key = { item -> notificationKey(item) }
                    ) { item ->
                        val targetUri = notificationPostUri(item)
                        val post = targetUri?.let { postLookup[it.atUri] }
                        val onClick = {
                            if (post != null) navigator.navigate(FullsizePostDestination(post.uri.atUri))
                        }

                        val snippetContent: (@Composable () -> Unit)? = if (targetUri != null) {
                            {
                                when {
                                    post != null -> {
                                        PostSnippet(post)
                                    }

                                    isPostsLoading -> {
                                        Text(
                                            "Loading post\u2026",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }

                                    postsError != null -> {
                                        ErrorMessage()
                                    }

                                    else -> {
                                        ErrorMessage()
                                    }
                                }
                            }
                        } else {
                            null
                        }

                        val modifier = Modifier
                            .fillMaxWidth()
                            .run {
                                if (item.isRead) {
                                    this
                                } else {
                                    this.background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(0.15f)
                                    )
                                }
                            }
                            .clickable { onClick() }
                            .padding(horizontal = 14.dp)
                            .padding(vertical = 6.dp)

                        when (item) {
                            is NotificationItem.GroupedPostActivity -> {
                                val message = notificationMessage(
                                    reasonKey = item.reasonKey,
                                    reasonLabel = item.reasonLabel,
                                    names = item.notifications.takeAsString(3)
                                )
                                NotificationStack(
                                    reasonKey = item.reasonKey,
                                    reasonLabel = item.reasonLabel,
                                    modifier = modifier,
                                    avatarContent = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            item.notifications.take(5).forEach { notification ->
                                                Avatar(
                                                    avatarUrl = notification.author.avatar?.uri,
                                                    contentDescription = notification.author.displayName
                                                        ?: notification.author.handle.handle,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clickable {
                                                            navigator.navigate(
                                                                ProfileDestination(
                                                                    notification.author.did.did
                                                                )
                                                            )
                                                        }
                                                )
                                            }
                                            if (item.notifications.size > 5) {
                                                Text(
                                                    "+${item.notifications.size - 5}",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    modifier = Modifier.padding(4.dp)
                                                )
                                            }
                                        }
                                    },
                                    message = message,
                                    snippetContent = snippetContent
                                )
                            }

                            is NotificationItem.Single -> {
                                val reason = reasonInfo(item.notification.reason)
                                val authorName = item.notification.author.displayName
                                    ?: item.notification.author.handle.handle
                                val message = notificationMessage(
                                    reasonKey = reason.key,
                                    reasonLabel = reason.label,
                                    names = authorName
                                )

                                if (reason.key == "reply" && post != null) {
                                    PostComponent(
                                        postView = post,
                                        navigator = navigator,
                                        density = PostDensity.Regular,
                                        showActions = true,
                                        showReply = true,
                                        showLabels = false,
                                        showSeparator = false,
                                        modifier = if (item.isRead) {
                                            Modifier
                                        } else {
                                            Modifier.background(
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                    0.15f
                                                )
                                            )
                                        }
                                    ) {
                                        navigator.navigate(FullsizePostDestination(post.uri.atUri))
                                    }
                                } else {

                                    NotificationStack(
                                        reasonKey = reason.key,
                                        reasonLabel = reason.label,
                                        modifier = modifier,
                                        avatarContent = {
                                            Avatar(
                                                avatarUrl = item.notification.author.avatar?.uri,
                                                contentDescription = item.notification.author.displayName
                                                    ?: item.notification.author.handle.handle,
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clickable {
                                                        navigator.navigate(
                                                            ProfileDestination(
                                                                item.notification.author.did.did
                                                            )
                                                        )
                                                    }
                                            )
                                        },
                                        message = message,
                                        snippetContent = snippetContent
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth(),
                            thickness = 0.2.dp
                        )
                    }

                    if (notifications.hasMore) {
                        item {
                            LoadMore(notifications.value?.size ?: 0) {
                                coroutineScope.launch {
                                    notifications.loadMore?.invoke()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class NotificationItem {
    data class GroupedPostActivity(
        val reasonKey: String,
        val reasonLabel: String,
        val subject: AtUri,
        val notifications: List<ListNotificationsNotification>,
    ) : NotificationItem()

    data class Single(val notification: ListNotificationsNotification) : NotificationItem()

    val isRead: Boolean
        get() = when (this) {
            is GroupedPostActivity -> notifications.all { it.isRead }
            is Single -> notification.isRead
        }
}

private val groupedReasons = setOf("like", "repost")

fun buildNotificationItems(
    notifications: List<ListNotificationsNotification>
): List<NotificationItem> {
    val grouped = mutableMapOf<String, MutableList<ListNotificationsNotification>>()
    notifications.forEach { notification ->
        val reason = reasonInfo(notification.reason)
        val subject = notification.reasonSubject
        if (reason.key in groupedReasons && subject != null) {
            val key = "${reason.key}:${subject.atUri}"
            if (!grouped.containsKey(key)) {
                grouped[key] = mutableListOf()
            }
            grouped[key]!!.add(notification)
        }
    }

    val emitted = mutableSetOf<String>()
    val result = mutableListOf<NotificationItem>()
    notifications.forEach { notification ->
        val reason = reasonInfo(notification.reason)
        val subject = notification.reasonSubject
        val key = if (reason.key in groupedReasons && subject != null) {
            "${reason.key}:${subject.atUri}"
        } else {
            null
        }

        if (key != null) {
            if (emitted.add(key)) {
                val list = grouped[key].orEmpty()
                result.add(
                    NotificationItem.GroupedPostActivity(
                        reasonKey = reason.key,
                        reasonLabel = reason.label,
                        subject = subject!!,
                        notifications = list,
                    )
                )
            }
        } else {
            result.add(NotificationItem.Single(notification))
        }
    }

    return result
}

private fun notificationKey(item: NotificationItem): String = when (item) {
    is NotificationItem.GroupedPostActivity -> "${item.reasonKey}:${item.subject.atUri}"
    is NotificationItem.Single -> item.notification.uri.atUri
}

private fun notificationPostUri(item: NotificationItem): AtUri? {
    return when (item) {
        is NotificationItem.GroupedPostActivity -> item.subject.takeIf { it.isPostUri() }
        is NotificationItem.Single -> notificationPostUri(item.notification)
    }
}

private fun notificationPostUri(notification: ListNotificationsNotification): AtUri? {
    val reasonKey = reasonInfo(notification.reason).key
    val reasonSubject = notification.reasonSubject
    val uri = notification.uri
    val subjectPost = reasonSubject?.takeIf { it.isPostUri() }
    val uriPost = uri.takeIf { it.isPostUri() }

    return when (reasonKey) {
        "like", "repost" -> subjectPost
        else -> uriPost ?: subjectPost
    }
}

private fun notificationMessage(reasonKey: String, reasonLabel: String, names: String): String {
    return when (reasonKey) {
        "like" -> "$names liked your post."
        "repost" -> "$names reposted your post."
        "follow" -> "$names followed you."
        "mention" -> "$names mentioned you."
        "reply" -> "$names replied to your post."
        "quote" -> "$names quoted your post."
        else -> "$names ${reasonLabel.humanizeReason()}."
    }
}

private fun String.humanizeReason(): String {
    return replace("_", " ")
        .replace("-", " ")
        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
        .lowercase()
}

private data class ReasonInfo(val key: String, val label: String)

private fun reasonInfo(reason: Any): ReasonInfo {
    val rawValue = reason.toString()
    val extracted = if (rawValue.contains("value=")) {
        rawValue.substringAfter("value=").substringBefore(")")
    } else {
        rawValue.substringAfterLast(".")
    }
    return ReasonInfo(
        key = extracted.lowercase(),
        label = extracted
    )
}

private fun AtUri.isPostUri(): Boolean = atUri.contains("app.bsky.feed.post")

@Composable
private fun NotificationStack(
    reasonKey: String,
    reasonLabel: String,
    modifier: Modifier = Modifier,
    avatarContent: @Composable () -> Unit,
    message: String,
    snippetContent: (@Composable () -> Unit)?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        modifier = modifier.padding(vertical = 4.dp)
    ) {
        NotificationIcon(reasonKey, reasonLabel)
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            avatarContent()
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium
            )
            snippetContent?.invoke()
        }
    }
}

@Composable
private fun NotificationIcon(reasonKey: String, reasonLabel: String) {
    val icon = when (reasonKey) {
        "like" -> Icons.Rounded.Favorite
        "repost" -> Icons.Rounded.Repeat
        "follow" -> Icons.Rounded.PersonAdd
        "mention" -> Icons.Rounded.AlternateEmail
        "reply" -> Icons.AutoMirrored.Rounded.Reply
        "quote" -> Icons.Rounded.FormatQuote
        else -> Icons.Rounded.Notifications
    }
    Icon(
        imageVector = icon,
        contentDescription = reasonLabel,
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun PostSnippet(post: PostView) {
    val text = postTextForSnippet(post)
    val snippet = if (text.isNullOrBlank()) "Post content unavailable" else text
    Text(
        text = snippet,
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun postTextForSnippet(post: PostView): String? {
    return try {
        post.record.decodeAs<Post>().text
    } catch (e: Exception) {
        Log.w("Notifications", "Failed to decode post text: ${post.uri.atUri}", e)
        null
    }
}

fun List<ListNotificationsNotification>.takeAsString(n: Int): String {
    return take(n).joinToString(", ") { it.author.displayName ?: it.author.handle.handle } +
            if (size > n) " and ${size - n} others" else ""
}

package io.github.turtlepaw.mindsky.components.post

import android.content.ClipData
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.bsky.bookmark.CreateBookmarkRequest
import app.bsky.bookmark.DeleteBookmarkRequest
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.embed.RecordWithMediaViewMediaUnion
import app.bsky.embed.GalleryViewItemUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Like
import app.bsky.feed.Post
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.Repost
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.DeleteRecordRequest
import com.atproto.repo.StrongRef
import com.ramcosta.composedestinations.generated.destinations.ComposerDestination
import com.ramcosta.composedestinations.generated.destinations.FullscreenVideoDestination
import com.ramcosta.composedestinations.generated.destinations.ImageDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.generated.destinations.UserListDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.R
import io.github.turtlepaw.mindsky.auth.SessionManager
import fyi.kittens.shared.ui.Avatar
import fyi.kittens.shared.ui.ContentAware
import fyi.kittens.shared.ui.ExpandableContent
import fyi.kittens.shared.ui.PostDensity
import fyi.kittens.shared.ui.PostStructure
import io.github.turtlepaw.mindsky.components.ConfirmationSheet
import fyi.kittens.shared.ui.embeds.FeedGraphDisplay
import fyi.kittens.shared.ui.embeds.GalleryEmbed
import fyi.kittens.shared.ui.embeds.ImagesEmbed
import fyi.kittens.shared.ui.embeds.InformationalEmbed
import fyi.kittens.shared.ui.embeds.LinkEmbed
import fyi.kittens.shared.ui.embeds.StarterPack
import fyi.kittens.shared.ui.embeds.VideoEmbed
import fyi.kittens.shared.ui.getDefinitionOf
import fyi.kittens.shared.ui.rememberLoadingColor
import io.github.turtlepaw.mindsky.components.post.embeds.QuotePost
import io.github.turtlepaw.mindsky.components.sheets.CodeSheet
import io.github.turtlepaw.mindsky.components.sheets.rememberSheetState
import io.github.turtlepaw.mindsky.components.sheets.toPrettyJson
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalPostState
import io.github.turtlepaw.mindsky.di.OPTIMISTIC_LIKE_URI
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import io.github.turtlepaw.mindsky.routes.UserListType
import io.github.turtlepaw.mindsky.routes.post.toImageData
import io.github.turtlepaw.mindsky.routes.post.ImageData
import io.github.turtlepaw.mindsky.routes.post.ImageDataStore
import io.github.turtlepaw.mindsky.utils.sanitize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.math.max

@Composable
fun ThreadedPostComponent(
    thread: List<FeedViewPost>,
    navigator: DestinationsNavigator,
    density: PostDensity = PostDensity.Regular,
    enabled: Boolean = true,
    onClick: (FeedViewPost) -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column {
        thread.forEachIndexed { index, post ->
            val isThreadChild = index > 0
            val showThreadIndicator = index < thread.size - 1
            Box {
                if (isThreadChild || showThreadIndicator) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val verticalX = 37.dp.toPx()
                        val centerY =
                            if (density == PostDensity.Compact) 26.dp.toPx() else 37.dp.toPx()
                        if (isThreadChild) {
                            drawLine(
                                color = lineColor,
                                start = Offset(verticalX, 0f),
                                end = Offset(verticalX, centerY),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                        if (showThreadIndicator) {
                            drawLine(
                                color = lineColor,
                                start = Offset(verticalX, centerY),
                                end = Offset(verticalX, size.height),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }
                }
                PostComponent(
                    postView = post.post,
                    navigator = navigator,
                    reason = post.reason,
                    onClick = { onClick(post) },
                    enabled = enabled,
                    density = density,
                    showSeparator = index == thread.size - 1,
                    showReply = false,
                )
            }
        }
    }
}

@Composable
fun PostComponent(
    postView: FeedViewPost,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    discoveryContext: @Composable () -> Unit = {},
    showActions: Boolean = true,
    density: PostDensity = PostDensity.Regular,
    enabled: Boolean = true,
    showHeadlineDetails: Boolean = false,
    showReply: Boolean = true,
    showSeparator: Boolean = true,
    showLabels: Boolean = true,
    showHandle: Boolean? = null,
    showThreadIndicator: Boolean = false,
    isThreadChild: Boolean = false,
    isExpandable: Boolean = false,
    onClick: () -> Unit
) {
    PostComponent(
        postView = postView.post,
        navigator = navigator,
        modifier = modifier,
        reason = postView.reason,
        discoveryContext = discoveryContext,
        showActions = showActions,
        density = density,
        enabled = enabled,
        showHeadlineDetails = showHeadlineDetails,
        showReply = showReply,
        showSeparator = showSeparator,
        showLabels = showLabels,
        showHandle = showHandle,
        showThreadIndicator = showThreadIndicator,
        isThreadChild = isThreadChild,
        isExpandable = isExpandable,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepostSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onQuoteClick: () -> Unit,
    onRepostClick: () -> Unit,
    isReposted: Boolean
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    if (isOpen) {
        ModalBottomSheet(
            onDismissRequest = {
                onDismiss()
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                onRepostClick()
                            }
                        }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Repeat",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text(if (isReposted) "Remove repost" else "Repost")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                onQuoteClick()
                            }
                        }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = "Chat bubble",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Text("Quote post")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostComponent(
    postView: PostView,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    reason: FeedViewPostReasonUnion? = null,
    discoveryContext: @Composable () -> Unit = {},
    showActions: Boolean = true,
    density: PostDensity = PostDensity.Regular,
    enabled: Boolean = true,
    showHeadlineDetails: Boolean = false,
    showReply: Boolean = true,
    showSeparator: Boolean = true,
    showLabels: Boolean = true,
    showHandle: Boolean? = null,
    showThreadIndicator: Boolean = false,
    isThreadChild: Boolean = false,
    isExpandable: Boolean = false,
    readOnly: Boolean = false,
    onClick: () -> Unit,
) {
    val author = postView.author
    val primaryColor = MaterialTheme.colorScheme.primary
    val postRecord = try {
        Log.d("PostDebug", "Raw record JSON: ${postView.record}")
        postView.record.decodeAs<Post>()
    } catch (e: Exception) {
        Log.e("PostDebug", "Failed to decode JSON: ${postView.record}", e)
        null
    }

    if (postRecord == null) {
        Log.w(
            "PostDebug",
            "Skipping post render; record could not be decoded: ${postView.uri.atUri}"
        )
        return
    }
    val session = SessionManager(LocalContext.current).getSession()
    val api = LocalMindskyApi.current
    val feedModel = LocalFeedModel.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var isRepostSheetOpen by remember { mutableStateOf(false) }
    var isDeleteConfirmationOpen by remember { mutableStateOf(false) }
    var showHeartOverlay by remember { mutableStateOf(false) }
    val developerMode by rememberPreference(AppPrefs.DeveloperMode)
    val showRichTextIcons by rememberPreference(AppPrefs.ShowRichTextIcons)
    val via = null
    val postState = LocalPostState.current
    val viewerIsAuthor = author.did.did == session?.did

    val viewerLike = postView.viewer?.like
    val viewerRepost = postView.viewer?.repost
    val bookmarked = postView.viewer?.bookmarked ?: false

    val currentState = postState.syncFromViewer(
        uri = postView.uri,
        likeUri = viewerLike,
        repostUri = viewerRepost,
        bookmarked = bookmarked
    )

    val bookmarkedText = stringResource(R.string.bookmarked)
    val removedBookmarkText = stringResource(R.string.removed_bookmark)

    val likeUri = currentState.likeUri
    val repostUri = currentState.repostUri
    val baseLikeCount = postView.likeCount ?: 0
    val likeDelta = when {
        viewerLike != null && likeUri == null -> -1
        viewerLike == null && likeUri != null -> 1
        else -> 0
    }
    val displayLikeCount = max(0, baseLikeCount + likeDelta)
    val baseRepostCount = postView.repostCount ?: 0
    val repostDelta = when {
        viewerRepost != null && repostUri == null -> -1
        viewerRepost == null && repostUri != null -> 1
        else -> 0
    }
    val displayRepostCount = max(0, baseRepostCount + repostDelta)
    val baseBookmarkCount = postView.bookmarkCount ?: 0
    val bookmarkDelta = when {
        bookmarked && currentState.bookmarked == false -> -1
        !bookmarked && currentState.bookmarked == true -> 1
        else -> 0
    }
    val displayBookmarkCount = max(0, baseBookmarkCount + bookmarkDelta)
    val hasImpressions = displayLikeCount > 0 || displayRepostCount > 0 || displayBookmarkCount > 0

    val likeAction: () -> Unit = {
        val currentLikeUri = postState.getLike(postView.uri)
        val isCurrentlyLiked = currentLikeUri != null

        haptics.performHapticFeedback(HapticFeedbackType.LongPress)

        if (!postState.isInFlightLike(postView.uri)) {
            coroutineScope.launch {
                postState.setInFlightLike(postView.uri, true)
                val userDid = session?.did
                val collection = Nsid("app.bsky.feed.like")

                if (isCurrentlyLiked) {
                    postState.setLike(postView.uri, null)
                    try {
                        if (userDid != null && currentLikeUri != OPTIMISTIC_LIKE_URI) {
                            api.deleteRecord(
                                DeleteRecordRequest(
                                    collection = collection,
                                    repo = Did(userDid),
                                    rkey = currentLikeUri.getRkey(),
                                )
                            ).requireResponse()
                        }
                    } catch (e: Exception) {
                        Log.e("PostLike", "Failed to delete like record", e)
                        postState.setLike(postView.uri, currentLikeUri)
                    } finally {
                        postState.setInFlightLike(postView.uri, false)
                    }
                } else {
                    postState.setLike(postView.uri, OPTIMISTIC_LIKE_URI)
                    try {
                        val likeRecord = Like(
                            StrongRef(postView.uri, postView.cid),
                            via = via,
                            createdAt = Clock.System.now()
                        )

                        val recordJson = BlueskyJson.encodeToJsonElement(
                            Like.serializer(),
                            likeRecord
                        ).jsonObject.toMutableMap().apply {
                            put("\$type", JsonPrimitive("app.bsky.feed.like"))
                        }

                        if (userDid != null) {
                            val data = api.createRecord(
                                CreateRecordRequest(
                                    record = BlueskyJson.encodeAsJsonContent(JsonObject(recordJson)),
                                    repo = Did(userDid),
                                    collection = collection,
                                )
                            ).requireResponse()

                            if (postState.getLike(postView.uri) == OPTIMISTIC_LIKE_URI) {
                                postState.setLike(postView.uri, data.uri)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("PostLike", "Failed to create like record", e)
                        if (postState.getLike(postView.uri) == OPTIMISTIC_LIKE_URI) {
                            postState.setLike(postView.uri, null)
                        }
                    } finally {
                        postState.setInFlightLike(postView.uri, false)
                    }
                }
            }
        }
    }
//        if(reason is FeedViewPostReasonUnion.ReasonRepost){
//        StrongRef(reason.value)
//    }

    RepostSheet(
        isOpen = isRepostSheetOpen,
        onDismiss = { isRepostSheetOpen = false },
        onQuoteClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            navigator.navigate(
                ComposerDestination(
                    targetQuoteUri = postView.uri.atUri
                )
            )

        },
        onRepostClick = {
            coroutineScope.launch {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val collection = Nsid("app.bsky.feed.repost")
                val currentRepostUri = postState.getRepost(postView.uri)
                try {
                    if (currentRepostUri != null) {
                        postState.setReposted(postView.uri, null)
                        api.deleteRecord(
                            DeleteRecordRequest(
                                collection = collection,
                                repo = Did(session!!.did),
                                rkey = currentRepostUri.getRkey(),
                            )
                        ).requireResponse()
                    } else {
                        val repostRecord = Repost(
                            subject = StrongRef(postView.uri, postView.cid),
                            via = via,
                            createdAt = Clock.System.now()
                        )

                        val recordJson =
                            BlueskyJson.encodeToJsonElement(
                                Repost.serializer(),
                                repostRecord
                            ).jsonObject.toMutableMap().apply {
                                put("\$type", JsonPrimitive("app.bsky.feed.repost"))
                            }

                        val repost = api.createRecord(
                            CreateRecordRequest(
                                record = BlueskyJson.encodeAsJsonContent(JsonObject(recordJson)),
                                repo = Did(session!!.did),
                                collection = collection,
                            )
                        ).requireResponse()
                        postState.setReposted(postView.uri, repost.uri)
                    }
                } catch (error: Error) {
                    error.printStackTrace()
                }
            }
        },
        isReposted = currentState.repostUri != null
    )

    ConfirmationSheet(
        isOpen = isDeleteConfirmationOpen,
        onDismiss = { isDeleteConfirmationOpen = false },
        onConfirm = {
            coroutineScope.launch {
                try {
                    api.deleteRecord(
                        DeleteRecordRequest(
                            collection = Nsid("app.bsky.feed.post"),
                            repo = Did(session!!.did),
                            rkey = postView.uri.getRkey(),
                        )
                    ).requireResponse()

                    // Notify the ViewModel to refresh feeds and profile
                    feedModel.notifyPostDeleted(postView.uri)
                } catch (e: Exception) {
                    Toast
                        .makeText(
                            context,
                            "Failed to delete post",
                            Toast.LENGTH_SHORT
                        )
                        .show()
                    e.printStackTrace()
                }
            }
        },
        title = stringResource(R.string.delete_post_title),
        description = stringResource(R.string.delete_post_description),
        confirmText = stringResource(R.string.delete),
        dismissText = stringResource(R.string.cancel),
        isDestructive = true
    )

    val codeSheetState = rememberSheetState()
    CodeSheet(codeSheetState, postView.toPrettyJson())

    if (showHeartOverlay) {
        LaunchedEffect(Unit) {
            delay(400)
            showHeartOverlay = false
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        PostStructure(
            showThreadIndicator = showThreadIndicator,
            isThreadChild = isThreadChild,
            metadata = {
                Column(
                    modifier = Modifier.padding()
                ) {
                    discoveryContext()

                    if (reason is FeedViewPostReasonUnion.ReasonRepost) {
                        Row(
                            horizontalArrangement = spacedBy(8.dp),
                            modifier = Modifier
                                .padding(bottom = 4.dp)
                                .clip(
                                    MaterialTheme.shapes.small
                                )
                                .clickable(
                                    enabled = session?.did != reason.value.by.did.did
                                ) {
                                    navigator.navigate(
                                        ProfileDestination(reason.value.by.did.did)
                                    )
                                }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Repeat,
                                contentDescription = "Repeat",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                            Text(
                                text = "Reposted by ${if (session?.did == reason.value.by.did.did) "you" else reason.value.by.displayName ?: reason.value.by.handle.handle}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.W600,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                ),
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else if (reason is FeedViewPostReasonUnion.ReasonPin) {
                        Row(
                            horizontalArrangement = spacedBy(8.dp),
                            modifier = Modifier
                                .padding(bottom = 14.dp, start = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Push Pin",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Pinned post",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                ),
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            avatar = {
                Avatar(
                    modifier = it,
                    avatarUrl = author.avatar?.uri,
                    contentDescription = author.displayName ?: author.handle.handle,
                ) {
                    navigator.navigate(
                        ProfileDestination(author.did.did)
                    )
                }
            },
            headline = {
                PostHeadline(
                    timestamp = postRecord.createdAt,
                    author = author,
                    density = density,
                    showLabels = showLabels,
                    showHandle = showHandle,
                    showHeadlineDetails = showHeadlineDetails,
                    isBookmarked = currentState.bookmarked == true
                )
            },
            impressions = {
                FlowRow(it, horizontalArrangement = spacedBy(24.dp)) {
                    if (displayLikeCount > 0) {
                        PostAction.Label(
                            label = "likes",
                            value = displayLikeCount
                        ) {
                            if (!readOnly) navigator.navigate(
                                UserListDestination(
                                    identifier = postView.uri.atUri.sanitize() + "_userlist_likes",
                                    type = UserListType.Likes(postView.uri.atUri)
                                )
                            )
                        }
                    }
                    if (displayRepostCount > 0) {
                        PostAction.Label(
                            label = "reposts",
                            value = displayRepostCount
                        ) {
                            if (!readOnly) navigator.navigate(
                                UserListDestination(
                                    identifier = postView.uri.atUri.sanitize() + "_userlist_reposts",
                                    type = UserListType.Reposts(postView.uri.atUri)
                                )
                            )
                        }
                    }
                    if (displayBookmarkCount > 0) {
                        PostAction.Label(
                            label = "bookmarks",
                            value = displayBookmarkCount,
                        ) {
                            // Bookmarks are private, so we don't navigate to a list
                        }
                    }
                }
            },
            showImpressions = hasImpressions,
            actions = {
                if (showActions) {
                    Row(
                        modifier = it,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = spacedBy(24.dp)) {
                            PostAction.Button(
                                label = postView.replyCount,
                                icon = Icons.Rounded.ChatBubbleOutline,
                                contentDescription = "Chat Bubble",
                            ) { }
                            PostAction.Button(
                                label = displayRepostCount,
                                icon = Icons.Rounded.Repeat,
                                contentDescription = "Repeat",
                                isHighlighted = repostUri != null,
                            ) {
                                if (!readOnly) isRepostSheetOpen = true
                            }
                            PostAction.Button(
                                label = displayLikeCount,
                                icon = if (likeUri != null) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Heart",
                                isHighlighted = likeUri != null,
                                highlightColor = Color(0xFFFF2D55),
                            ) {
                                if (!readOnly) likeAction()
                            }
                        }

                        val groupInteractionSource = remember { MutableInteractionSource() }
                        var expanded by remember { mutableStateOf(false) }

                        val text = stringResource(R.string.more)
                        Box() {
                            // Icon button should have a tooltip associated with it for a11y.
                            TooltipBox(
                                positionProvider =
                                    TooltipDefaults.rememberTooltipPositionProvider(
                                        TooltipAnchorPosition.Above
                                    ),
                                tooltip = {
                                    PlainTooltip(
                                        Modifier.semantics() {
                                            // TODO(b/496338253): Remove this modifier once bug where tooltip text is
                                            //  not announced by a11y screen readers is resolved.
                                            liveRegion = LiveRegionMode.Assertive
                                            paneTitle = text
                                        }
                                    ) {
                                        Text(text)
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                PostAction.Button(
                                    icon = Icons.Rounded.MoreHoriz,
                                    contentDescription = text,
                                ) {
                                    if (!readOnly) expanded = true
                                }
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }) {
                                val clipboardManager = LocalClipboard.current
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_text)) },
                                    leadingIcon =
                                        {
                                            Icon(
                                                Icons.Rounded.ContentCopy,
                                                contentDescription = stringResource(R.string.copy_text)
                                            )
                                        },
                                    onClick = {
                                        coroutineScope.launch {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            clipboardManager.setClipEntry(
                                                ClipEntry(
                                                    ClipData.newPlainText(
                                                        "Post content",
                                                        postRecord.text
                                                    )
                                                )
                                            )

                                            Toast.makeText(
                                                context,
                                                "Copied to clipboard",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            expanded = false
                                        }
                                    }
                                )

                                val baseUrl by rememberPreference(AppPrefs.BaseUrl)
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_link_to_post)) },
                                    leadingIcon =
                                        {
                                            Icon(
                                                Icons.Rounded.Share,
                                                contentDescription = stringResource(R.string.share)
                                            )
                                        },
                                    onClick = {
                                        coroutineScope.launch {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            clipboardManager.setClipEntry(
                                                ClipEntry(
                                                    ClipData.newPlainText(
                                                        "Link to post",
                                                        "$baseUrl/profile/${author.did.did}/post/${postView.uri.getRkey()}"
                                                    )
                                                )
                                            )

                                            Toast.makeText(
                                                context,
                                                "Copied to clipboard",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            expanded = false
                                        }
                                    }
                                )
                                if (viewerIsAuthor) DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.delete))
                                    },
                                    leadingIcon =
                                        {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = null,
                                            )
                                        },
                                    onClick = {
                                        isDeleteConfirmationOpen = true
                                        expanded = false
                                    }
                                )

                                val isBookmarked = currentState.bookmarked == true
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (isBookmarked) stringResource(R.string.remove_bookmark)
                                            else stringResource(R.string.bookmark)
                                        )
                                    },
                                    leadingIcon =
                                        {
                                            Icon(
                                                if (isBookmarked) Icons.Rounded.Bookmark
                                                else Icons.Rounded.BookmarkBorder,
                                                contentDescription = null,
                                            )
                                        },
                                    onClick = {
                                        coroutineScope.launch {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (isBookmarked) {
                                                api.deleteBookmark(
                                                    DeleteBookmarkRequest(
                                                        uri = postView.uri
                                                    )
                                                ).requireResponse()
                                                postState.setBookmarked(postView.uri, false)
                                                feedModel.notifyBookmarked(postView.uri, false)
                                            } else {
                                                api.createBookmark(
                                                    CreateBookmarkRequest(
                                                        uri = postView.uri,
                                                        cid = postView.cid
                                                    )
                                                ).requireResponse()
                                                postState.setBookmarked(postView.uri, true)
                                                feedModel.notifyBookmarked(postView.uri, true)
                                            }

                                            Toast.makeText(
                                                context,
                                                if (isBookmarked) removedBookmarkText
                                                else bookmarkedText,
                                                Toast.LENGTH_SHORT
                                            ).show()

                                            expanded = false
                                        }
                                    }
                                )

                                if (developerMode) DropdownMenuItem(
                                    text = {
                                        Text("Raw JSON")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Build,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        coroutineScope.launch {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            expanded = false
                                            codeSheetState.onOpen()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            density = density,
            onClick = null,
            createdAt = postRecord.createdAt,
            showSeparator = showSeparator,
            modifier = modifier
                .clip(MaterialTheme.shapes.large)
                .then(
                    if (enabled) {
                        Modifier.combinedClickable(
                            onClick = { onClick() },
                            onDoubleClick = {
                                if (postState.getLike(postView.uri) == null) {
                                    likeAction()
                                }
                                showHeartOverlay = true
                            }
                        )
                    } else Modifier
                )
        ) {
            if (density == PostDensity.Expanded) Labelers(author.labels)
            if (postRecord.reply != null && showReply) {
                Row {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Reply,
                        contentDescription = "Reply",
                        modifier = Modifier
                            .size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                    )
                    Text(
                        text = "Reply",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val localLabels = LocalLabelManager.current
            val definitions by localLabels.labelersDefinitionFlow.collectAsState()

            val prefs = LocalPreferences.current

            val videoSoundDefault by rememberPreference(AppPrefs.VideoSoundEnabled)
            val onMuteChanged: (state: Boolean) -> Unit = {
                prefs.set(AppPrefs.VideoSoundEnabled, it)
            }

            val postContent: @Composable () -> Unit = {
                if (postRecord.text.isNotBlank()) {
                    RichText(
                        text = postRecord.text,
                        facets = postRecord.facets,
                        navigator = navigator,
                        linkColor = primaryColor,
                        showIcons = showRichTextIcons
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (postView.embed) {
                    is PostViewEmbedUnion.ImagesView -> {
                        ImagesEmbed(
                            (postView.embed as PostViewEmbedUnion.ImagesView).value,
                        ) {
                            ImageDataStore.put(
                                it.fullsize.uri,
                                (postView.embed as PostViewEmbedUnion.ImagesView).value.images.map { image -> image.toImageData() })
                            navigator.navigate(
                                ImageDestination(
                                    imageUrl = it.fullsize.uri,
                                    alt = it.alt,
                                    postUri = postView.uri.atUri
                                )
                            )
                        }
                    }

                    is PostViewEmbedUnion.RecordWithMediaView -> {
                        val data = (postView.embed as PostViewEmbedUnion.RecordWithMediaView).value

                        when (val media = data.media) {
                            is RecordWithMediaViewMediaUnion.ImagesView -> {
                                ImagesEmbed(media.value) {
                                    ImageDataStore.put(
                                        it.fullsize.uri,
                                        media.value.images.map { image -> image.toImageData() })
                                    navigator.navigate(
                                        ImageDestination(
                                            imageUrl = it.fullsize.uri,
                                            alt = it.alt,
                                            postUri = postView.uri.atUri
                                        )
                                    )
                                }
                            }

                            is RecordWithMediaViewMediaUnion.VideoView -> {
                                VideoEmbed(
                                    videoUrl = media.value.playlist.uri,
                                    thumbnail = media.value.thumbnail?.uri,
                                    defaultIsMuted = videoSoundDefault,
                                    onMute = onMuteChanged,
                                    onClick = { currentPosition ->
                                        navigator.navigate(
                                            FullscreenVideoDestination(
                                                videoUrl = media.value.playlist.uri,
                                                postUri = postView.uri.atUri,
                                                position = currentPosition
                                            )
                                        )
                                    }
                                )
                            }

                            is RecordWithMediaViewMediaUnion.GalleryView -> {
                                GalleryEmbed(media.value) {
                                    ImageDataStore.put(
                                        it.fullsize.uri,
                                        media.value.items.mapNotNull { item -> (item as? GalleryViewItemUnion.ViewImage)?.value }
                                            .map { image ->
                                                ImageData(
                                                    image.fullsize.uri,
                                                    image.alt
                                                )
                                            })
                                    navigator.navigate(
                                        ImageDestination(
                                            imageUrl = it.fullsize.uri,
                                            alt = it.alt
                                        )
                                    )
                                }
                            }

                            is RecordWithMediaViewMediaUnion.ExternalView -> {
                                LinkEmbed(media.value)
                            }

                            else -> {}
                        }

                        RecordView(data.record.record, navigator)
                    }

                    is PostViewEmbedUnion.GalleryView -> {
                        GalleryEmbed(
                            gallery = (postView.embed as PostViewEmbedUnion.GalleryView).value,
                        ) {
                            ImageDataStore.put(
                                it.fullsize.uri,
                                (postView.embed as PostViewEmbedUnion.GalleryView).value.items.mapNotNull { item -> (item as? GalleryViewItemUnion.ViewImage)?.value }
                                    .map { image -> ImageData(image.fullsize.uri, image.alt) })
                            navigator.navigate(
                                ImageDestination(
                                    imageUrl = it.fullsize.uri,
                                    alt = it.alt
                                )
                            )
                        }
                    }

                    is PostViewEmbedUnion.RecordView -> {
                        val recordEmbed = (postView.embed as PostViewEmbedUnion.RecordView).value
                        RecordView(recordEmbed.record, navigator)
                    }

                    // This is an assumed structure for video embeds.
                    // Replace with the actual structure if different.
                    is PostViewEmbedUnion.VideoView -> {
                        val externalEmbed = (postView.embed as PostViewEmbedUnion.VideoView)
                        // Assuming the video URL is in externalEmbed.external.uri
                        // You might need to adjust this based on the actual data structure
                        VideoEmbed(
                            videoUrl = externalEmbed.value.playlist.uri,
                            thumbnail = externalEmbed.value.thumbnail?.uri,
                            defaultIsMuted = videoSoundDefault,
                            onMute = onMuteChanged,
                            onClick = { currentPosition ->
                                navigator.navigate(
                                    FullscreenVideoDestination(
                                        videoUrl = externalEmbed.value.playlist.uri,
                                        postUri = postView.uri.atUri,
                                        position = currentPosition
                                    )
                                )
                            }
                        )
                    }

                    is PostViewEmbedUnion.ExternalView -> {
                        val externalEmbed =
                            (postView.embed as PostViewEmbedUnion.ExternalView).value

                        LinkEmbed(externalEmbed)
                    }

                    else -> {}
                }
            }

            ContentAware(
                post = postView,
                labelDefinitions = definitions
            ) {
                if (isExpandable) {
                    ExpandableContent {
                        postContent()
                    }
                } else {
                    postContent()
                }
            }
        }

        if (showHeartOverlay) {
            val scale by animateFloatAsState(
                targetValue = 1.2f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                ),
                label = "OverlayHeartScale"
            )
            val alpha by animateFloatAsState(
                targetValue = 0f,
                animationSpec = tween(350),
                label = "OverlayHeartAlpha"
            )
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF2D55),
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
            )
        }
    }
}

@Composable
fun RecordView(record: RecordViewRecordUnion, navigator: DestinationsNavigator) {
    when (record) {
        is RecordViewRecordUnion.ViewRecord -> {
            QuotePost(record.value, navigator)
        }

        is RecordViewRecordUnion.FeedGeneratorView -> {
            FeedGraphDisplay(record.value)
        }

        is RecordViewRecordUnion.GraphListView -> {
            FeedGraphDisplay(record.value)
        }

        is RecordViewRecordUnion.GraphStarterPackViewBasic -> {
            StarterPack(record.value)
        }
//                    is RecordViewRecordUnion.LabelerLabelerView -> TODO()
        is RecordViewRecordUnion.Unknown -> {
            InformationalEmbed(R.string.unknown_embed)
        }

        is RecordViewRecordUnion.ViewBlocked -> {
            InformationalEmbed(R.string.blocked)
        }

        is RecordViewRecordUnion.ViewDetached -> {
            InformationalEmbed(R.string.detached_by_author)
        }

        is RecordViewRecordUnion.ViewNotFound -> {
            InformationalEmbed(R.string.embed_not_found)
        }

        else -> {}
    }
}

fun AtUri.getRkey(): RKey {
    return RKey(this.atUri.substringAfterLast("/"))
}

@Composable
fun LoadingPost() {
    val color = rememberLoadingColor()

    PostStructure(
        headline = {
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth(0.5f)
                    .height(10.dp)
                    .background(
                        color,
                        MaterialTheme.shapes.small
                    )
            )
        },
        avatar = {
            Box(
                modifier = it
                    .background(
                        color,
                        CircleShape
                    )
            )
        },
        metadata = {},
        actions = {},
        impressions = {},
        createdAt = Clock.System.now(),
        content = {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth(if (it == 2) 0.8f else 0.9f)
                        .height(9.dp)
                        .background(
                            color,
                            MaterialTheme.shapes.small
                        )
                )
            }
        },
    )
}

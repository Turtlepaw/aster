package io.github.turtlepaw.mindsky.components.post

import android.content.ClipData
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
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
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Like
import app.bsky.feed.Post
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.Repost
import coil3.compose.AsyncImage
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.DeleteRecordRequest
import com.atproto.repo.StrongRef
import com.ramcosta.composedestinations.generated.destinations.ImageDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.generated.destinations.UserListDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.components.ConfirmationSheet
import io.github.turtlepaw.mindsky.components.post.embeds.FeedGraphDisplay
import io.github.turtlepaw.mindsky.components.post.embeds.InformationalEmbed
import io.github.turtlepaw.mindsky.components.post.embeds.LinkEmbed
import io.github.turtlepaw.mindsky.components.post.embeds.QuotePost
import io.github.turtlepaw.mindsky.components.post.embeds.StarterPack
import io.github.turtlepaw.mindsky.components.post.embeds.VideoEmbed
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalPostState
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.routes.UserListType
import io.github.turtlepaw.mindsky.utils.sanitize
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import kotlin.math.max

@Composable
fun PostComponent(
    postView: FeedViewPost,
    navigator: DestinationsNavigator,
    density: PostDensity = PostDensity.Regular,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    PostComponent(
        postView = postView.post,
        navigator = navigator,
        reason = postView.reason,
        onClick = onClick,
        enabled = enabled,
        density = density,
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
    showReply: Boolean = true,
    showSeparator: Boolean = true,
    showLabels: Boolean = true,
    showHandle: Boolean = true,
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var isRepostSheetOpen by remember { mutableStateOf(false) }
    var isDeleteConfirmationOpen by remember { mutableStateOf(false) }
    val developerMode by AppPrefs.DeveloperMode.stateFlow.collectAsState()
    val via = null
    val postState = LocalPostState.current
    val viewerIsAuthor = author.did.did == session?.did

    val viewerLike = postView.viewer?.like
    val viewerRepost = postView.viewer?.repost

    val currentState = postState.syncFromViewer(
        uri = postView.uri,
        likeUri = viewerLike,
        repostUri = viewerRepost
    )

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
    val hasImpressions = displayLikeCount > 0 || displayRepostCount > 0
//        if(reason is FeedViewPostReasonUnion.ReasonRepost){
//        StrongRef(reason.value)
//    }

    RepostSheet(
        isOpen = isRepostSheetOpen,
        onDismiss = { isRepostSheetOpen = false },
        onQuoteClick = {},
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
                        val repost = api.createRecord(
                            CreateRecordRequest(
                                record = Json.encodeAsJsonContent(
                                    Repost(
                                        subject = StrongRef(postView.uri, postView.cid),
                                        via = via,
                                        createdAt = Clock.System.now()
                                    )
                                ),
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

    PostStructure(
        metadata = {
            Column(
                modifier = Modifier.padding()
            ) {
                discoveryContext()

                if (reason is FeedViewPostReasonUnion.ReasonRepost) {
                    Row(
                        horizontalArrangement = spacedBy(8.dp),
                        modifier = Modifier
                            .padding(bottom = 5.dp)
                            .padding(start = 25.dp)
                            .run {
                                if (session?.did == reason.value.by.did.did) this
                                else clickable {
                                    navigator.navigate(
                                        ProfileDestination(reason.value.by.did.did)
                                    )
                                }
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
                showHandle = showHandle
            )
        },
        impressions = {
            FlowRow(it, horizontalArrangement = spacedBy(24.dp)) {
                if (displayLikeCount > 0) {
                    PostAction.Label(
                        label = "likes",
                        value = displayLikeCount
                    ) {
                        navigator.navigate(
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
                        navigator.navigate(
                            UserListDestination(
                                identifier = postView.uri.atUri.sanitize() + "_userlist_reposts",
                                type = UserListType.Reposts(postView.uri.atUri)
                            )
                        )
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
                            isRepostSheetOpen = true
                        }
                        PostAction.Button(
                            label = displayLikeCount,
                            icon = if (likeUri != null) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Heart",
                            isHighlighted = likeUri != null,
                        ) {
                            coroutineScope.launch {
                                val session = SessionManager(context).getSession()
                                val collection = Nsid("app.bsky.feed.like")
                                val vibrator =
                                    ContextCompat.getSystemService(context, Vibrator::class.java)!!
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    vibrator.vibrate(
                                        VibrationEffect.createPredefined(
                                            if (likeUri != null) VibrationEffect.EFFECT_TICK
                                            else VibrationEffect.EFFECT_DOUBLE_CLICK
                                        )
                                    )
                                } else {
                                    vibrator.vibrate(50)
                                }
                                val currentLikeUri = postState.getLike(postView.uri)
                                if (currentLikeUri != null) {
                                    postState.setLike(postView.uri, null)
                                    api.deleteRecord(
                                        DeleteRecordRequest(
                                            collection = collection,
                                            repo = Did(session!!.did),
                                            rkey = currentLikeUri.getRkey(),
                                        ),
                                    ).requireResponse()
                                } else {
                                    val data = api.createRecord(
                                        CreateRecordRequest(
                                            record = Json.encodeAsJsonContent(
                                                Like(
                                                    StrongRef(postView.uri, postView.cid),
                                                    via = via,
                                                    createdAt = Clock.System.now()
                                                ),
                                            ),
                                            repo = Did(session!!.did),
                                            collection = collection,
                                        )
                                    ).requireResponse()
                                    postState.setLike(postView.uri, data.uri)
                                }
                            }
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
                                expanded = true
                            }
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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

                            val baseUrl by AppPrefs.BaseUrl.stateFlow.collectAsState()
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

                            if (developerMode) DropdownMenuItem(
                                text = {
                                    Text("Copy Raw JSON")
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
                                        clipboardManager.setClipEntry(
                                            ClipEntry(
                                                ClipData.newPlainText(
                                                    "Post JSON",
                                                    postView.record.toString()
                                                )
                                            )
                                        )

                                        Toast.makeText(
                                            context,
                                            "Copied raw JSON to clipboard",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        expanded = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        density = density,
        onClick = if (enabled) onClick else null,
        createdAt = postRecord.createdAt,
        showSeparator = showSeparator,
        modifier = modifier
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

        RichText(
            text = postRecord.text,
            facets = postRecord.facets,
            navigator = navigator,
            linkColor = primaryColor
        )

        when (postView.embed) {
            is PostViewEmbedUnion.ImagesView -> {
                val imagesEmbed =
                    (postView.embed as PostViewEmbedUnion.ImagesView).value
                val images = imagesEmbed.images // Store for easier access
                if (images.isNotEmpty()) {
                    val columnCount = if (images.size == 1) 1 else 2
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 800.dp),
                        horizontalArrangement = spacedBy(8.dp),
                        verticalArrangement = spacedBy(8.dp)
                    ) {
                        items(images) { image ->
                            val imageModifier = if (images.size == 1) {
                                Modifier
                                    .fillMaxSize() // Fill the 200dp high cell for a single image
                                    .padding(4.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            } else {
                                Modifier
                                    .aspectRatio(1f) // Square for multiple images in 2 columns
                                    .clip(MaterialTheme.shapes.medium)
                            }
                            AsyncImage(
                                model = image.thumb.uri,
                                contentDescription = image.alt
                                    ?: "Post image", // Use alt text
                                contentScale = ContentScale.Crop,
                                modifier = imageModifier
                                    .background(MaterialTheme.colorScheme.error)
                                    .clickable {
                                        navigator.navigate(
                                            ImageDestination(
                                                imageUrl = image.fullsize.uri,
                                                alt = image.alt
                                            )
                                        )
                                    }
                            )
                        }
                    }
                }
            }

            is PostViewEmbedUnion.RecordView -> {
                val recordEmbed = (postView.embed as PostViewEmbedUnion.RecordView).value
                when (val record = recordEmbed.record) {
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

            // This is an assumed structure for video embeds.
            // Replace with the actual structure if different.
            is PostViewEmbedUnion.VideoView -> {
                val externalEmbed = (postView.embed as PostViewEmbedUnion.VideoView)
                // Assuming the video URL is in externalEmbed.external.uri
                // You might need to adjust this based on the actual data structure
                VideoEmbed(externalEmbed.value.playlist.uri, externalEmbed.value.thumbnail?.uri)
            }

            is PostViewEmbedUnion.ExternalView -> {
                val externalEmbed = (postView.embed as PostViewEmbedUnion.ExternalView).value

                LinkEmbed(externalEmbed)
            }

            else -> {}
        }
    }
}

fun AtUri.getRkey(): RKey {
    return RKey(this.atUri.substringAfterLast("/"))
}

@Composable
fun rememberLoadingColor(): Color {
    val infiniteTransition = rememberInfiniteTransition()

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    return MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
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

package fyi.kittens.bsky.presentation.components.post

import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.embed.RecordWithMediaViewMediaUnion
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Like
import app.bsky.feed.Post
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.DeleteRecordRequest
import com.atproto.repo.StrongRef
import fyi.kittens.shared.R
import fyi.kittens.bsky.auth.SessionManager
import fyi.kittens.bsky.presentation.Screen
import fyi.kittens.shared.di.LocalMindskyApi
import fyi.kittens.shared.di.LocalPostState
import fyi.kittens.shared.getRkey
import fyi.kittens.shared.ui.Avatar
import fyi.kittens.shared.ui.PostDensity
import fyi.kittens.shared.ui.PostStructure
import fyi.kittens.shared.ui.PostStructureStyle
import fyi.kittens.shared.ui.RichText
import fyi.kittens.shared.ui.embeds.EmbedStructureStyle
import fyi.kittens.shared.ui.embeds.FeedGraphDisplay
import fyi.kittens.shared.ui.embeds.GalleryEmbed
import fyi.kittens.shared.ui.embeds.ImagesEmbed
import fyi.kittens.shared.ui.embeds.InformationalEmbed
import fyi.kittens.shared.ui.embeds.LinkEmbed
import fyi.kittens.shared.ui.embeds.LinkEmbedStyle
import fyi.kittens.shared.ui.embeds.LocalEmbedStructureStyle
import fyi.kittens.shared.ui.embeds.StarterPack
import fyi.kittens.shared.ui.embeds.VideoEmbed
import kotlinx.coroutines.launch
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
import kotlin.time.ExperimentalTime

@Composable
fun PostComponent(
    postView: FeedViewPost,
    navigator: NavController,
    modifier: Modifier = Modifier,
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
        modifier = modifier
    )
}


@OptIn(ExperimentalTime::class)
@Composable
fun PostComponent(
    postView: PostView,
    navigator: NavController,
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
    val session by SessionManager(LocalContext.current).sessionFlow.collectAsState()
    val api = LocalMindskyApi.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var isRepostSheetOpen by remember { mutableStateOf(false) }
    var isDeleteConfirmationOpen by remember { mutableStateOf(false) }
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
                            .clip(
                                MaterialTheme.shapes.small
                            )
                            .clickable(
                                enabled = session?.did != reason.value.by.did.did
                            ) {
                                navigator.navigate(
                                    Screen.Profile.createRoute(reason.value.by.did.did)
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Reposted by ${if (session?.did == reason.value.by.did.did) "you" else reason.value.by.displayName ?: reason.value.by.handle.handle}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            ),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1
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
                    Screen.Profile.createRoute(author.did.did)
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
                modifier = Modifier
                    .clip(
                        MaterialTheme.shapes.small
                    )
                    .clickable {
                        navigator.navigate(
                            Screen.Profile.createRoute(author.did.did)
                        )
                    }
            )
        },
        impressions = {
            FlowRow(it, horizontalArrangement = spacedBy(24.dp)) {
                if (displayLikeCount > 0) {
                    PostAction.Label(
                        label = "likes",
                        value = displayLikeCount
                    ) {
/*                        navigator.navigate(
                            UserListDestination(
                                identifier = postView.uri.atUri.sanitize() + "_userlist_likes",
                                type = UserListType.Likes(postView.uri.atUri)
                            )
                        )*/
                    }
                }
                if (displayRepostCount > 0) {
                    PostAction.Label(
                        label = "reposts",
                        value = displayRepostCount
                    ) {
/*                        navigator.navigate(
                            UserListDestination(
                                identifier = postView.uri.atUri.sanitize() + "_userlist_reposts",
                                type = UserListType.Reposts(postView.uri.atUri)
                            )
                        )*/
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
                            label = displayLikeCount,
                            icon = if (likeUri != null) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Heart",
                            isHighlighted = likeUri != null,
                        ) {
                            coroutineScope.launch {
                                val collection = Nsid("app.bsky.feed.like")
                                val vibrator =
                                    ContextCompat.getSystemService(context, Vibrator::class.java)!!
                                vibrator.vibrate(
                                    VibrationEffect.createPredefined(
                                        if (likeUri != null) VibrationEffect.EFFECT_TICK
                                        else VibrationEffect.EFFECT_DOUBLE_CLICK
                                    )
                                )
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
                                    val likeRecord = Like(
                                        StrongRef(postView.uri, postView.cid),
                                        via = via,
                                        createdAt = Clock.System.now()
                                    )

                                    val recordJson =
                                        BlueskyJson.encodeToJsonElement(
                                            Like.serializer(),
                                            likeRecord
                                        ).jsonObject.toMutableMap().apply {
                                            put("\$type", JsonPrimitive("app.bsky.feed.like"))
                                        }

                                    val data = api.createRecord(
                                        CreateRecordRequest(
                                            record = BlueskyJson.encodeAsJsonContent(
                                                JsonObject(
                                                    recordJson
                                                )
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
                }
            }
        },
        density = density,
        onClick = if (enabled) onClick else null,
        createdAt = postRecord.createdAt,
        showSeparator = showSeparator,
        modifier = modifier,
        style = PostStructureStyle(
            avatarSize = 30.dp,
            separatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            headlineSpacing = 5.dp,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            ),
            showExpandedSeparator = false,
            separatorPadding = 20.dp,
            expandedSeparatorPadding = 12.dp
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

        val content = @Composable {
            Column {
                if (!postRecord.text.isBlank()) {
                    RichText(
                        text = postRecord.text,
                        facets = postRecord.facets,
                        onMentionClick = {},
                        linkColor = primaryColor,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        )
                    )
                }

                if (postView.embed != null) {
                    Spacer(Modifier.height(4.dp))
                }

                CompositionLocalProvider(
                    LocalEmbedStructureStyle provides EmbedStructureStyle.createStyle(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                ) {
                when (postView.embed) {
                    is PostViewEmbedUnion.ImagesView -> {
                        ImagesEmbed(
                            (postView.embed as PostViewEmbedUnion.ImagesView).value,
                            false
                        ) {
                            // @TODO
                        }
                    }

                    is PostViewEmbedUnion.RecordWithMediaView -> {
                        val data = (postView.embed as PostViewEmbedUnion.RecordWithMediaView).value

                        when (val media = data.media) {
                            is RecordWithMediaViewMediaUnion.ImagesView -> {
                                ImagesEmbed(media.value, false) {
                                    // @TODO
                                }
                            }

                            is RecordWithMediaViewMediaUnion.VideoView -> {
                                VideoEmbed(
                                    videoUrl = media.value.playlist.uri,
                                    thumbnail = media.value.thumbnail?.uri
                                )
                            }

                            is RecordWithMediaViewMediaUnion.GalleryView -> {
                                GalleryEmbed(media.value) {
                                    // @TODO
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
                            // @TODO
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
                            thumbnail = externalEmbed.value.thumbnail?.uri
                        )
                    }

                    is PostViewEmbedUnion.ExternalView -> {
                        val externalEmbed =
                            (postView.embed as PostViewEmbedUnion.ExternalView).value

                        LinkEmbed(
                            externalEmbed,
                            LinkEmbedStyle(
                                titleStyle = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                                ),
                                descriptionStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                ),
                                domainStyle = MaterialTheme.typography.bodyExtraSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                spacing = 4.dp
                            )
                        )
                    }

                    else -> {}
                }
                }
            }
        }

        if (density == PostDensity.Expanded) {
            content()
        } else {
            Spacer(Modifier.height(4.dp))
            ExpandableContent {
                content()
            }
        }
    }
}


@Composable
fun ExpandableContent(
    collapsedHeight: Dp = 100.dp,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isCollapsible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        SubcomposeLayout(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .then(if (!expanded && isCollapsible) Modifier.clipToBounds() else Modifier)
        ) { constraints ->
            val placeables = subcompose(Unit, content)
            val contentPlaceable = placeables.first().measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            )

            val actualHeight = contentPlaceable.height
            val collapsedHeightPx = collapsedHeight.roundToPx()
            val currentlyCollapsible = actualHeight > collapsedHeightPx

            if (isCollapsible != currentlyCollapsible) {
                isCollapsible = currentlyCollapsible
            }

            val displayHeight = if (!expanded && currentlyCollapsible) {
                collapsedHeightPx
            } else {
                actualHeight
            }

            layout(contentPlaceable.width, displayHeight) {
                contentPlaceable.placeRelative(0, 0)
            }
        }

        if (!expanded && isCollapsible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .clip(
                        MaterialTheme.shapes.small
                    )
                    .clickable {
                        expanded = true
                    },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Expand"
                )
                Text(
                    "Show more",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun RecordView(record: RecordViewRecordUnion, navController: NavController) {
    when (record) {
        is RecordViewRecordUnion.ViewRecord -> {
            QuotePost(
                record = record.value,
                navigator = navController
            )
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

@OptIn(ExperimentalTime::class)
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

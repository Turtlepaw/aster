package io.github.turtlepaw.mindsky.components.post.embeds

import androidx.compose.runtime.Composable
import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import com.atproto.repo.StrongRef
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.toPostViewEmbedUnion
import fyi.kittens.shared.ui.embeds.EmbedStructure
import io.github.turtlepaw.mindsky.components.post.PostComponent
import fyi.kittens.shared.ui.PostDensity
import kotlinx.serialization.json.Json

@Composable
fun QuotePost(
    record: RecordViewRecord,
    navigator: DestinationsNavigator,
    isExpandable: Boolean = false
) {
    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "\$type"

    }
    val value = PostView(
        record = record.value,
        uri = record.uri,
        cid = record.cid,
        author = record.author,
        viewer = null,
        quoteCount = record.quoteCount,
        embed = record.embeds?.firstOrNull()?.toPostViewEmbedUnion(),
        bookmarkCount = null,
        replyCount = record.replyCount,
        repostCount = record.repostCount,
        likeCount = record.likeCount,
        indexedAt = record.indexedAt,
        labels = record.labels,
        threadgate = null,
        debug = null,
    )

    EmbedStructure(onClick = {}) {
        PostComponent(
            value,
            navigator,
            showActions = false,
            density = PostDensity.Compact,
            showLabels = false,
            isExpandable = isExpandable
        ) {
            navigator.navigate(FullsizePostDestination(value.uri.atUri))
        }
    }
}

@Composable
fun QuotePost(
    record: PostView,
    enabled: Boolean = true,
    showHandle: Boolean? = null,
    showHeadlineDetails: Boolean = false,
    isExpandable: Boolean = false,
    navigator: DestinationsNavigator
) {
    EmbedStructure(onClick = {}, enabled = enabled) {
        PostComponent(
            record,
            navigator,
            showActions = false,
            density = PostDensity.Compact,
            showLabels = false,
            enabled = enabled,
            showHandle = showHandle,
            showHeadlineDetails = showHeadlineDetails,
            isExpandable = isExpandable
        ) {
            navigator.navigate(FullsizePostDestination(record.uri.atUri))
        }
    }
}
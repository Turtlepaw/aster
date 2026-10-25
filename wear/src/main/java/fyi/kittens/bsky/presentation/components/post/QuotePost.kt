package fyi.kittens.bsky.presentation.components.post

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import app.bsky.embed.RecordViewRecord
import app.bsky.feed.PostView
import fyi.kittens.bsky.presentation.Screen
import fyi.kittens.shared.toPostViewEmbedUnion
import fyi.kittens.shared.ui.PostDensity
import fyi.kittens.shared.ui.embeds.EmbedStructure
import kotlinx.serialization.json.Json

@Composable
fun QuotePost(record: RecordViewRecord, navigator: NavController) {
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
            showLabels = false
        ) {
            navigator.navigate(Screen.PostDetails.createRoute(value.uri.atUri))
        }
    }
}

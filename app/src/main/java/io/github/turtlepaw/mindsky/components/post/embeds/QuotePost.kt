package io.github.turtlepaw.mindsky.components.post.embeds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.embed.RecordViewRecordUnion
import app.bsky.feed.GetPostsQueryParams
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import com.atproto.repo.GetRecordQueryParams
import com.atproto.repo.StrongRef
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.components.ErrorMessage
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.PostDensity
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import sh.christian.ozone.api.AtIdentifier
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey

@Composable
fun QuotePost(record: RecordViewRecord, navigator: DestinationsNavigator) {
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
            showLabels = false
        ) {
            navigator.navigate(FullsizePostDestination(value!!.uri.atUri))
        }
    }
}

fun RecordViewRecord.toStrongRef(): StrongRef {
    return StrongRef(
        uri = this.uri,
        cid = this.cid,
    )
}

fun RecordViewRecordEmbedUnion.toPostViewEmbedUnion(): PostViewEmbedUnion {
    return when (this) {
        is RecordViewRecordEmbedUnion.ImagesView -> PostViewEmbedUnion.ImagesView(this.value)
        is RecordViewRecordEmbedUnion.VideoView -> PostViewEmbedUnion.VideoView(this.value)
        is RecordViewRecordEmbedUnion.ExternalView -> PostViewEmbedUnion.ExternalView(this.value)
        is RecordViewRecordEmbedUnion.RecordView -> PostViewEmbedUnion.RecordView(this.value)
        is RecordViewRecordEmbedUnion.RecordWithMediaView -> PostViewEmbedUnion.RecordWithMediaView(this.value)
        is RecordViewRecordEmbedUnion.Unknown -> PostViewEmbedUnion.Unknown(this.value)
    }
}

package fyi.kittens.shared

import app.bsky.embed.RecordViewRecord
import app.bsky.embed.RecordViewRecordEmbedUnion
import app.bsky.feed.PostViewEmbedUnion
import com.atproto.repo.StrongRef
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.RKey

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
        is RecordViewRecordEmbedUnion.GalleryView -> PostViewEmbedUnion.GalleryView(this.value)
        is RecordViewRecordEmbedUnion.Unknown -> PostViewEmbedUnion.Unknown(this.value)
    }
}

fun AtUri.getRkey(): RKey {
    return RKey(this.atUri.substringAfterLast("/"))
}

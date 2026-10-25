package fyi.kittens.shared

import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.ReplyRefParentUnion
import kotlin.time.ExperimentalTime

object FeedTuner {
    @Deprecated("Use List<FeedViewPost>.cleanReplies() instead", replaceWith = ReplaceWith("feed.cleanReplies()"))
    fun cleanReplies(feed: List<FeedViewPost>): List<FeedViewPost> {
        // Filter out replies that are not top-level posts
        return feed.filter { post ->
            post.reply == null
        }
    }

    @OptIn(ExperimentalTime::class)
    fun groupThreads(feed: List<FeedViewPost>): List<List<FeedViewPost>> {
        val postsByUri = feed.associateBy { it.post.uri.atUri }
        val childrenByParentUri = mutableMapOf<String, MutableList<FeedViewPost>>()
        val syntheticParents = mutableMapOf<String, FeedViewPost>()

        for (item in feed) {
            val parent = when (val p = item.reply?.parent) {
                is ReplyRefParentUnion.PostView -> p.value
                else -> null
            }
            if (parent != null) {
                val parentUri = parent.uri.atUri
                if (!postsByUri.containsKey(parentUri)) {
                    syntheticParents.getOrPut(parentUri) { FeedViewPost(post = parent) }
                }
                childrenByParentUri.getOrPut(parentUri) { mutableListOf() }.add(item)
            }
        }

        val threads = mutableListOf<List<FeedViewPost>>()
        val processedUris = mutableSetOf<String>()

        fun collect(post: FeedViewPost, thread: MutableList<FeedViewPost>) {
            if (processedUris.contains(post.post.uri.atUri)) return
            thread.add(post)
            processedUris.add(post.post.uri.atUri)
            childrenByParentUri[post.post.uri.atUri]
                ?.forEach { collect(it, thread) }
        }

        for (item in feed) {
            val uri = item.post.uri.atUri
            if (processedUris.contains(uri)) continue

            val parentUri = when (val p = item.reply?.parent) {
                is ReplyRefParentUnion.PostView -> p.value.uri.atUri
                else -> null
            }

            if (parentUri == null || (!postsByUri.containsKey(parentUri) && !syntheticParents.containsKey(parentUri))) {
                val thread = mutableListOf<FeedViewPost>()
                collect(item, thread)
                threads.add(thread)
            }
        }

        for (synthetic in syntheticParents.values) {
            if (processedUris.contains(synthetic.post.uri.atUri)) continue
            val thread = mutableListOf<FeedViewPost>()
            collect(synthetic, thread)
            threads.add(thread)
        }

        return threads
    }
}

fun List<FeedViewPost>.cleanPins(): List<FeedViewPost> {
    return this.filter { it.reason != FeedViewPostReasonUnion.ReasonPin }
}
fun List<FeedViewPost>.cleanReplies(): List<FeedViewPost> {
    return this.filter { post ->
        post.reply == null
    }
}
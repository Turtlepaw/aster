package fyi.kittens.shared.ui.embeds

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

object VideoPlayerCache {
    private const val MAX_PLAYERS = 3
    private val players = LinkedHashMap<String, ExoPlayer>(MAX_PLAYERS, 0.75f, true)

    @Synchronized
    fun get(context: Context, url: String): ExoPlayer {
        players[url]?.let { return it }

        while (players.size >= MAX_PLAYERS) {
            val iterator = players.entries.iterator()
            val evicted = iterator.next()
            iterator.remove()
            evicted.value.release()
        }

        return ExoPlayer.Builder(context.applicationContext).build().also {
            players[url] = it
        }
    }

    @Synchronized
    fun remove(url: String) {
        players.remove(url)?.release()
    }
}

package io.github.turtlepaw.mindsky.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NowPlayingStore {
    private val _nowPlaying = MutableStateFlow<Track?>(null)
    val nowPlaying: StateFlow<Track?> = _nowPlaying.asStateFlow()

    fun setNowPlaying(context: Context, track: Track?) {
        _nowPlaying.value = track
    }
}

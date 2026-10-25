package io.github.turtlepaw.mindsky.media

import java.time.Instant

data class Artist(
    val name: String,
    val mbid: String? = null // MusicBrainz ID
)

data class Track(
    val name: String,
    val artists: List<Artist>,
    val album: String? = null,
    val recordingMbid: String? = null,
    val durationMs: Long = 0,
    val timestamp: Instant = Instant.now(),
    val serviceBaseUrl: String? = null,
    val url: String? = null,
    val albumArtUrl: String? = null
)

enum class ListenType {
    SINGLE,      // A finished listen
    IMPORT,      // Historical import
    PLAYING_NOW  // Currently playing
}

data class ListenSubmission(
    val listenType: ListenType,
    val tracks: List<Track>
)
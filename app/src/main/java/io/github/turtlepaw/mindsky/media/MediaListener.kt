package io.github.turtlepaw.mindsky.media

import android.app.Notification
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import io.github.turtlepaw.mindsky.MindskyApplication
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.db.Scrobble
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.TrackingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Locale

class NotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sessionManager: SessionManager
    private lateinit var lookupService: MediaLookupService
    private var currentTrack: Track? = null
    private var currentTrackKey: String? = null
    private var currentTrackStartedAtMs: Long? = null
    private var currentTrackPlayedMs: Long = 0
    private var lastPlaybackState: Int? = null

    companion object {
        private const val TAG = "NotificationListener"
        private const val MIN_LISTEN_MS = 30_000L
    }

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as MindskyApplication
        sessionManager = app.sessionManager
        lookupService = MediaLookupService(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        if (!TrackingPreferences.isTrackingEnabled(applicationContext)) {
            resetTrackState()
            lastPlaybackState = null
            publishNowPlayingState(null)
            return
        }

        val notification = sbn?.notification ?: return
        val extras = notification.extras

        // Get media session token
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(
                Notification.EXTRA_MEDIA_SESSION,
                MediaSession.Token::class.java
            ) ?: return
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(
                Notification.EXTRA_MEDIA_SESSION
            ) ?: return
        }

        // Create controller
        val controller = MediaController(this, token)

        // Read metadata
        val metadata = controller.metadata ?: return
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: return
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)

        // Get album art - try all possible keys
        var albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        if (albumArt == null) {
            albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        }
        if (albumArt == null) {
            albumArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        }

        var albumArtUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
        if (albumArtUri == null) {
            albumArtUri = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
        }
        if (albumArtUri == null) {
            albumArtUri = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        }

        // Save bitmap to cache if available but no URI
        var albumArtUrl: String? = albumArtUri
        if (albumArtUrl == null && albumArt != null) {
            try {
                // Save bitmap to internal cache
                val fileName = "album_art_${System.currentTimeMillis()}.jpg"
                val file = File(applicationContext.cacheDir, fileName)
                FileOutputStream(file).use { out ->
                    albumArt.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                albumArtUrl = file.absolutePath
                Log.d(TAG, "Saved album art bitmap to: $albumArtUrl")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save album art bitmap", e)
            }
        }

        Log.d(TAG, "Album art URL: $albumArtUrl, Bitmap available: ${albumArt != null}")

        // Check if playing
        val playbackState = controller.playbackState
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val nowMs = System.currentTimeMillis()

        if (!isSongMetadata(metadata, album)) {
            Log.d(TAG, "Skipping non-song media: $title")
            if (lastPlaybackState == PlaybackState.STATE_PLAYING) {
                finishCurrentTrack(nowMs)
            } else {
                resetTrackState()
                publishNowPlayingState(null)
            }
            lastPlaybackState = playbackState?.state
            return
        }

        Log.d(TAG, "Now playing: $title by $artist from $album. Playing: $isPlaying")

        // Create track object
        val track = Track(
            name = title,
            artists = listOf(Artist(name = artist)),
            album = album,
            durationMs = duration,
            timestamp = Instant.now(),
            albumArtUrl = albumArtUrl
        )

        val trackKey = trackIdentity(track)
        val wasPlaying = lastPlaybackState == PlaybackState.STATE_PLAYING

        // Handle playback state changes
        when {
            isPlaying -> {
                if (!wasPlaying) {
                    // Started playing or resumed
                    Log.d(TAG, "Track started playing")
                    if (currentTrackKey != null && currentTrackKey != trackKey) {
                        finishCurrentTrack(nowMs)
                    }
                    if (currentTrackKey != trackKey) {
                        currentTrack = track
                        currentTrackKey = trackKey
                        currentTrackPlayedMs = 0
                    } else {
                        currentTrack = track
                    }
                    currentTrackStartedAtMs = nowMs
                    handleNowPlaying(track)
                } else if (currentTrackKey != null && currentTrackKey != trackKey) {
                    // Track changed while still playing
                    Log.d(TAG, "Track changed while playing")
                    finishCurrentTrack(nowMs)
                    currentTrack = track
                    currentTrackKey = trackKey
                    currentTrackPlayedMs = 0
                    currentTrackStartedAtMs = nowMs
                    handleNowPlaying(track)
                } else if (currentTrack == null) {
                    currentTrack = track
                    currentTrackKey = trackKey
                    currentTrackStartedAtMs = nowMs
                }
            }
            !isPlaying && wasPlaying -> {
                // Stopped playing (could be track finished)
                Log.d(TAG, "Track stopped playing")
                finishCurrentTrack(nowMs)
            }
        }

        lastPlaybackState = playbackState?.state
    }

    private fun trackIdentity(track: Track): String {
        val nameKey = track.name.trim().lowercase(Locale.ROOT)
        val artistKey = track.artists.joinToString("|") { it.name.trim().lowercase(Locale.ROOT) }
        val albumKey = track.album?.trim()?.lowercase(Locale.ROOT) ?: ""
        val durationKey = if (track.durationMs > 0) track.durationMs.toString() else ""
        val recordingKey = track.recordingMbid?.trim()?.lowercase(Locale.ROOT) ?: ""
        return listOf(nameKey, artistKey, albumKey, durationKey, recordingKey).joinToString("||")
    }

    private fun finishCurrentTrack(nowMs: Long) {
        val track = currentTrack
        if (track == null) {
            resetTrackState()
            publishNowPlayingState(null)
            return
        }

        currentTrackStartedAtMs?.let { start ->
            val elapsed = (nowMs - start).coerceAtLeast(0)
            currentTrackPlayedMs += elapsed
        }
        val playedMs = currentTrackPlayedMs
        resetTrackState()
        publishNowPlayingState(null)

        if (playedMs >= MIN_LISTEN_MS) {
            handleTrackFinished(track)
        } else {
            Log.d(TAG, "Skipping submit for ${track.name}: played ${playedMs}ms")
        }
    }

    private fun resetTrackState() {
        currentTrack = null
        currentTrackKey = null
        currentTrackStartedAtMs = null
        currentTrackPlayedMs = 0
    }

    private fun isSongMetadata(metadata: MediaMetadata, album: String?): Boolean {
        val mediaType = resolveContentType(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI))
        if (mediaType?.startsWith("video/") == true) {
            return false
        }
        if (mediaType?.startsWith("audio/") == true) {
            return true
        }

        val albumArtist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val genre = metadata.getString(MediaMetadata.METADATA_KEY_GENRE)
        val trackNumber = metadata.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER)

        return !album.isNullOrBlank() ||
                !albumArtist.isNullOrBlank() ||
                !genre.isNullOrBlank() ||
                trackNumber > 0
    }

    private fun resolveContentType(mediaUri: String?): String? {
        if (mediaUri.isNullOrBlank()) {
            return null
        }

        val uri = Uri.parse(mediaUri)
        return try {
            applicationContext.contentResolver.getType(uri)
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read media type for $mediaUri", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid media URI $mediaUri", e)
            null
        }
    }

    /**
     * Handle "Now Playing" - ephemeral status update
     */
    private fun handleNowPlaying(track: Track) {
        serviceScope.launch {
            try {
                // Hydrate track metadata using lookup service
                val hydratedTrack = lookupService.hydrateTrack(track)
                
                NowPlayingStore.setNowPlaying(applicationContext, hydratedTrack)
                
                val app = applicationContext as MindskyApplication
                val scrobblingDid = app.preferenceManager.get(AppPrefs.ScrobblingDid)
                
                if (scrobblingDid.isBlank()) {
                    Log.w(TAG, "No scrobbling DID set, skipping now playing update")
                    return@launch
                }

                val session = sessionManager.getSession(scrobblingDid) ?: run {
                    Log.w(TAG, "No session for $scrobblingDid, skipping now playing update")
                    return@launch
                }

                // Use context and session for DPoP authentication
                val pdsClient = PdsClient(applicationContext, session)
                pdsClient.publishPlayingNow(session.did, hydratedTrack)

                Log.d(TAG, "Published now playing: ${hydratedTrack.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish now playing", e)
            }
        }
    }

    private fun publishNowPlayingState(track: Track?) {
        serviceScope.launch {
            try {
                NowPlayingStore.setNowPlaying(applicationContext, track)
                
                if (track == null) {
                    val app = applicationContext as MindskyApplication
                    val scrobblingDid = app.preferenceManager.get(AppPrefs.ScrobblingDid)
                    if (scrobblingDid.isNotBlank()) {
                        val session = sessionManager.getSession(scrobblingDid)
                        if (session != null) {
                            val pdsClient = PdsClient(applicationContext, session)
                            pdsClient.clearPlayingNow(session.did)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update now playing cache", e)
            }
        }
    }

    /**
     * Handle track finished - submit to history and save to database
     */
    private fun handleTrackFinished(track: Track?) {
        if (track == null) return

        serviceScope.launch {
            try {
                // Hydrate track metadata using lookup service
                val hydratedTrack = lookupService.hydrateTrack(track)
                
                val app = applicationContext as MindskyApplication
                val scrobblingDid = app.preferenceManager.get(AppPrefs.ScrobblingDid)
                
                if (scrobblingDid.isBlank()) {
                    Log.w(TAG, "No scrobbling DID set, skipping track submission")
                    return@launch
                }

                val session = sessionManager.getSession(scrobblingDid) ?: run {
                    Log.w(TAG, "No session for $scrobblingDid, skipping track submission")
                    return@launch
                }

                // Submit to PDS first to get the record URI
                val pdsClient = PdsClient(applicationContext, session)

                pdsClient.submitPlay(session.did, hydratedTrack) { uri ->
                    Log.d(TAG, "Submitted play with URI: $uri")

                    // Save to local database with the record URI
                    serviceScope.launch(Dispatchers.IO) {
                        val scrobble = Scrobble(
                            userId = session.did,
                            trackName = hydratedTrack.name,
                            artistsJson = hydratedTrack.artists.joinToString { it.name },
                            album = hydratedTrack.album,
                            albumArtUrl = hydratedTrack.albumArtUrl,
                            recordingMbid = hydratedTrack.recordingMbid,
                            durationMs = hydratedTrack.durationMs,
                            timestamp = hydratedTrack.timestamp.toEpochMilli(),
                            serviceUrl = session.pdsUrl,
                            syncedToPds = true,
                            pdsRecordUri = uri
                        )
                        ObjectBox.store.boxFor(Scrobble::class.java).put(scrobble)
                        Log.d(TAG, "Saved to database: ${hydratedTrack.name}")
                    }
                }

                Log.d(TAG, "Submitted play: ${hydratedTrack.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit play", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Note: SupervisorJob will be cancelled when service is destroyed
    }
}

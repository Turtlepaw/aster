package io.github.turtlepaw.mindsky.media

import android.content.Context
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import org.json.JSONObject // Assuming org.json or similar for parsing
import java.io.File
import java.security.MessageDigest

data class MusicBrainzResult(
    val id: String,
    val title: String,
    val length: Long,
    val releases: List<MbRelease>,
    val isrcs: List<String>,
    val artistCredit: List<MbArtistCredit>
)

data class MbRelease(
    val id: String,
    val title: String,
    val date: String?,
    val country: String?
)

data class MbArtistCredit(
    val name: String,
    val artist: MbArtist
)

data class MbArtist(val id: String)

class MediaLookupService(private val context: Context) {
    private val logger = Logger.getLogger(MediaLookupService::class.java.name)
    private val baseUrl = "https://musicbrainz.org/ws/2/recording"

    // --- Cache Configuration ---
    private val cacheDir = File(context.cacheDir, "musicbrainz_api_cache")
    private val maxCacheSizeBytes = 10L * 1024 * 1024 // 10 MB limit
    private val cacheTtlMs = java.util.concurrent.TimeUnit.DAYS.toMillis(7) // 7 Days

    // Rate limiting: MusicBrainz allows ~1 req/sec
    private var lastRequestTime = 0L
    private val rateLimitLock = Any()

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        // Run cleanup asynchronously to avoid blocking UI thread on init
        Thread { pruneCache() }.start()
    }

    fun hydrateTrack(track: Track): Track {
        // 1. Build Search Query
        val queryParts = mutableListOf<String>()

        // Sanitize quotes for Lucene query syntax
        val safeName = track.name.replace("\"", "\\\"")
        queryParts.add("recording:\"$safeName\"")

        if (track.artists.isNotEmpty()) {
            val safeArtist = track.artists[0].name.replace("\"", "\\\"")
            queryParts.add("artist:\"$safeArtist\"")
        }

        if (!track.album.isNullOrBlank()) {
            val safeAlbum = track.album.replace("\"", "\\\"")
            queryParts.add("release:\"$safeAlbum\"")
        }

        val queryString = queryParts.joinToString(" AND ")

        // 2. Fetch Results
        val recordings = searchMusicBrainz(queryString)

        if (recordings.isEmpty()) {
            return track
        }

        // 3. Select Best Match
        val bestMatch = recordings.first()

        // 4. Return Hydrated Track
        // Update recording MBID and the main artist's MBID
        val updatedArtists = track.artists.mapIndexed { index, artist ->
            if (index == 0 && bestMatch.artistCredit.isNotEmpty()) {
                artist.copy(mbid = bestMatch.artistCredit[0].artist.id)
            } else {
                artist
            }
        }

        return track.copy(
            recordingMbid = bestMatch.id,
            artists = updatedArtists
            // Note: If you want to update the album name to the official one found:
            // album = bestMatch.releases.firstOrNull()?.title ?: track.album
        )
    }

    private fun searchMusicBrainz(query: String): List<MusicBrainzResult> {
        // 1. Check Disk Cache
        val cacheFile = getCacheFile(query)

        if (cacheFile.exists()) {
            val lastModified = cacheFile.lastModified()
            val isExpired = (System.currentTimeMillis() - lastModified) > cacheTtlMs

            if (!isExpired) {
                try {
                    val jsonString = cacheFile.readText()
                    // Simple validation to ensure file isn't empty/corrupt
                    if (jsonString.startsWith("{")) {
                        logger.info("Disk cache hit for: $query")
                        return parseMbResponse(jsonString)
                    }
                } catch (e: Exception) {
                    logger.warning("Failed to read cache file: ${e.message}")
                }
            } else {
                cacheFile.delete() // Remove expired
            }
        }

        // 2. Rate Limiting
        synchronized(rateLimitLock) {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastRequestTime
            if (timeSinceLast < 1100) {
                try {
                    Thread.sleep(1100 - timeSinceLast)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            lastRequestTime = System.currentTimeMillis()
        }

        // 3. Network Request
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "$baseUrl?query=$encodedQuery&fmt=json&inc=artists+releases+isrcs"

            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "BerylApp/1.0 ( contact@example.com )")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != 200) {
                logger.warning("MusicBrainz returned ${connection.responseCode}")
                return emptyList()
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }

            // 4. Save to Cache
            try {
                cacheFile.writeText(responseText)
            } catch (e: Exception) {
                logger.warning("Failed to write to cache: ${e.message}")
            }

            return parseMbResponse(responseText)

        } catch (e: Exception) {
            logger.severe("Failed to search MusicBrainz: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Parses the raw JSON response from MusicBrainz into our data classes.
     */
    /**
     * Parses the raw JSON response from MusicBrainz into our data classes.
     */
    private fun parseMbResponse(jsonString: String): List<MusicBrainzResult> {
        val results = mutableListOf<MusicBrainzResult>()
        try {
            val root = JSONObject(jsonString)
            val recordingsArray = root.optJSONArray("recordings") ?: return emptyList()

            for (i in 0 until recordingsArray.length()) {
                val recObj = recordingsArray.getJSONObject(i)

                // -- Parse Artist Credit --
                val artistCredits = mutableListOf<MbArtistCredit>()
                val acArray = recObj.optJSONArray("artist-credit")
                if (acArray != null) {
                    for (j in 0 until acArray.length()) {
                        val acObj = acArray.getJSONObject(j)
                        val artistObj = acObj.getJSONObject("artist")
                        artistCredits.add(
                            MbArtistCredit(
                                name = acObj.optString("name"),
                                artist = MbArtist(id = artistObj.getString("id"))
                            )
                        )
                    }
                }

                // -- Parse Releases --
                val releases = mutableListOf<MbRelease>()
                val relArray = recObj.optJSONArray("releases")
                if (relArray != null) {
                    for (k in 0 until relArray.length()) {
                        val relObj = relArray.getJSONObject(k)
                        releases.add(
                            MbRelease(
                                id = relObj.getString("id"),
                                title = relObj.optString("title"),
                                // FIX: Use takeIf for nullable strings instead of passing null default
                                date = relObj.optString("date").takeIf { it.isNotEmpty() },
                                country = relObj.optString("country").takeIf { it.isNotEmpty() }
                            )
                        )
                    }
                }

                // -- Parse ISRCs --
                val isrcList = mutableListOf<String>()
                val isrcArray = recObj.optJSONArray("isrcs")
                if (isrcArray != null) {
                    for (x in 0 until isrcArray.length()) {
                        isrcList.add(isrcArray.getString(x))
                    }
                }

                results.add(
                    MusicBrainzResult(
                        id = recObj.getString("id"),
                        title = recObj.optString("title"),
                        length = recObj.optLong("length", 0),
                        releases = releases,
                        isrcs = isrcList,
                        artistCredit = artistCredits
                    )
                )
            }
        } catch (e: Exception) {
            logger.severe("Error parsing MusicBrainz JSON: ${e.message}")
        }
        return results
    }

    /**
     * Generate a safe filename for the cache based on the query hash.
     */
    private fun getCacheFile(query: String): File {
        val digest = MessageDigest.getInstance("MD5")
        digest.update(query.toByteArray())
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.json")
    }

    /**
     * Simple LRU-style pruning. If cache is too big, delete oldest files.
     */
    private fun pruneCache() {
        try {
            val files = cacheDir.listFiles() ?: return
            var totalSize = files.sumOf { it.length() }

            if (totalSize > maxCacheSizeBytes) {
                // Sort by last modified (oldest first)
                val sortedFiles = files.sortedBy { it.lastModified() }

                for (file in sortedFiles) {
                    val size = file.length()
                    if (file.delete()) {
                        totalSize -= size
                    }
                    if (totalSize <= maxCacheSizeBytes) break
                }
                logger.info("Pruned cache. New size: ${totalSize / 1024} KB")
            }
        } catch (e: Exception) {
            logger.warning("Failed to prune cache: ${e.message}")
        }
    }
}
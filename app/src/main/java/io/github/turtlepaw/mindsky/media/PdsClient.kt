package io.github.turtlepaw.mindsky.media

import android.content.Context
import android.util.Log
import fyi.kittens.shared.MindskyHttpClient
import fyi.kittens.shared.auth.UserSession
import io.github.turtlepaw.mindsky.MindskyApplication
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.json.JSONObject
import java.time.Instant

class PdsClient(private val context: Context, private val session: UserSession) {
    private val httpClient: HttpClient by lazy {
        val app = context.applicationContext as MindskyApplication
        MindskyHttpClient.create(
            context = context,
            sessionProvider = app.sessionManager,
            hostUrlProvider = { session.pdsUrl }
        )
    }

    companion object {
        private const val TAG = "PdsClient"
        private const val LISTEN_COLLECTION = "io.github.turtlepaw.mindsky.listen"
    }

    suspend fun publishPlayingNow(did: String, track: Track) {
        try {
            // For now playing, we can use a record or an ephemeral status if available.
            // ATProto doesn't have a standard "playing now" yet, but we'll use a record.
            val record = buildJsonObject {
                put("${"$"}type", LISTEN_COLLECTION)
                put("name", track.name)
                putJsonArray("artists") {
                    track.artists.forEach { artist ->
                        add(buildJsonObject {
                            put("name", artist.name)
                            artist.mbid?.let { put("mbid", it) }
                        })
                    }
                }
                track.album?.let { put("album", it) }
                track.albumArtUrl?.let { put("albumArtUrl", it) }
                put("playingNow", true)
                put("createdAt", track.timestamp.toString())
            }

            // In some implementations, playing now is handled via putPreferences or a specific collection
            // We'll attempt to create a record in a special 'playing-now' key or similar
            // For now, we'll just log it as Mindsky might handle this differently.
            Log.d(TAG, "Publishing playing now: ${track.name} for $did")
            createRecord(did, LISTEN_COLLECTION, "playing", record)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish playing now", e)
        }
    }

    suspend fun submitPlay(did: String, track: Track, callback: (String) -> Unit) {
        try {
            val record = buildJsonObject {
                put("${"$"}type", LISTEN_COLLECTION)
                put("name", track.name)
                putJsonArray("artists") {
                    track.artists.forEach { artist ->
                        add(buildJsonObject {
                            put("name", artist.name)
                            artist.mbid?.let { put("mbid", it) }
                        })
                    }
                }
                track.album?.let { put("album", it) }
                track.albumArtUrl?.let { put("albumArtUrl", it) }
                track.recordingMbid?.let { put("recordingMbid", it) }
                put("durationMs", track.durationMs)
                put("createdAt", track.timestamp.toString())
            }

            val response = createRecord(did, LISTEN_COLLECTION, null, record)
            val uri = response.optString("uri")
            if (uri != null) {
                callback(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to submit play", e)
        }
    }

    suspend fun getPlayingNow(did: String): Track? {
        return try {
            val response = httpClient.post("/xrpc/com.atproto.repo.getRecord") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("repo", did)
                    put("collection", LISTEN_COLLECTION)
                    put("rkey", "playing")
                }.toString())
            }

            if (response.status.value != 200) return null

            val responseText = response.bodyAsText()
            val json = JSONObject(responseText)
            val record = json.optJSONObject("value") ?: return null

            // Parse record into Track
            val name = record.optString("name")
            val artists = record.optJSONArray("artists")?.let { arr ->
                List(arr.length()) { i ->
                    val obj = arr.getJSONObject(i)
                    Artist(
                        name = obj.getString("name"),
                        mbid = obj.optString("mbid", null)
                    )
                }
            } ?: emptyList()

            Track(
                name = name,
                artists = artists,
                album = record.optString("album", null),
                albumArtUrl = record.optString("albumArtUrl", null),
                timestamp = Instant.parse(record.getString("createdAt"))
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get playing now for $did", e)
            null
        }
    }

    suspend fun clearPlayingNow(did: String) {
        try {
            httpClient.post("/xrpc/com.atproto.repo.deleteRecord") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("repo", did)
                    put("collection", LISTEN_COLLECTION)
                    put("rkey", "playing")
                }.toString())
            }
            Log.d(TAG, "Cleared playing now for $did")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear playing now", e)
        }
    }

    private suspend fun createRecord(
        repo: String,
        collection: String,
        rkey: String?,
        record: JsonObject
    ): JSONObject {
        val body = buildJsonObject {
            put("repo", repo)
            put("collection", collection)
            rkey?.let { put("rkey", it) }
            put("record", record)
        }

        val response = httpClient.post("/xrpc/com.atproto.repo.createRecord") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }

        val responseText = response.bodyAsText()
        return JSONObject(responseText)
    }
}

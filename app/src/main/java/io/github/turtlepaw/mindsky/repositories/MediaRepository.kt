package io.github.turtlepaw.mindsky.repositories

import app.bsky.actor.ProfileViewDetailed
import com.atproto.repo.GetRecordQueryParams
import io.github.turtlepaw.mindsky.media.Artist
import io.github.turtlepaw.mindsky.media.Track
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonPrimitive
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.RKey
import java.time.Instant

object MediaRepository {
    private const val LISTEN_COLLECTION = "io.github.turtlepaw.mindsky.listen"

    suspend fun getPlayingNow(api: BlueskyApi, did: String): Track? {
        return try {
            val response = api.getRecord(
                GetRecordQueryParams(
                    repo = Did(did),
                    collection = Nsid(LISTEN_COLLECTION),
                    rkey = RKey("playing")
                )
            ).requireResponse()

            val record = response.value.value.jsonObject
            
            val artists = record["artists"]?.jsonArray?.map { artistElement ->
                val artistObj = artistElement.jsonObject
                Artist(
                    name = (artistObj["name"] as JsonPrimitive).content,
                    mbid = (artistObj["mbid"] as? JsonPrimitive)?.content
                )
            } ?: emptyList()

            Track(
                name = (record["name"] as JsonPrimitive).content,
                artists = artists,
                album = (record["album"] as? JsonPrimitive)?.content,
                albumArtUrl = (record["albumArtUrl"] as? JsonPrimitive)?.content,
                timestamp = (record["createdAt"] as? JsonPrimitive)?.content?.let { Instant.parse(it) } ?: Instant.now()
            )
        } catch (e: Exception) {
            // Record might not exist
            null
        }
    }
}

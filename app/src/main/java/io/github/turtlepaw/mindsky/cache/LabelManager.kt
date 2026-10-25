package io.github.turtlepaw.mindsky.cache

import android.content.Context
import app.bsky.actor.ProfileView
import app.bsky.labeler.GetServicesResponseViewUnion
import app.bsky.labeler.LabelerPolicies
import app.bsky.labeler.LabelerViewDetailed
import com.atproto.label.Label
import com.atproto.label.LabelValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.AtUri
import kotlin.time.Instant

open class LabelManager(context: Context) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val labelerCache: LabelerCache = LabelerCache(context)
    private val labelerDefinitionsCache: LabelerDefCache = LabelerDefCache(context)

    open val labelersFlow = labelerCache.labelersFlow
    open val labelersDefinitionFlow = labelerDefinitionsCache.definitionsFlow

    fun preWarmDefinitions(blueskyApi: BlueskyApi) {
        coroutineScope.launch {
            labelersFlow.collect {
                labelerDefinitionsCache.getDefinitions(
                    it,
                    blueskyApi
                )
            }
        }
    }

    fun forceRefreshDefinitions(blueskyApi: BlueskyApi) {
        coroutineScope.launch {
            labelerDefinitionsCache.getDefinitions(
                labelersFlow.value,
                blueskyApi,
                allowCache = false
            )
        }
    }

    suspend fun revalidateLabelers(blueskyApi: BlueskyApi) {
        labelerCache.fetchAndCacheLabelers(blueskyApi)
    }
}

class MockLabelManager(context: Context) : LabelManager(context) {
    val labelerDid = Did("did:plc:ar7232v22ky3c4v352y26vd2")

    override val labelersFlow = MutableStateFlow(listOf(labelerDid.did))

    override val labelersDefinitionFlow = MutableStateFlow<Map<String, GetServicesResponseViewUnion>>(
        mapOf(
            labelerDid.did to GetServicesResponseViewUnion.LabelerViewDetailed(
                LabelerViewDetailed(
                uri = AtUri("at://did:plc:ar7232v22ky3c4v352y26vd2/app.bsky.labeler.service/self"),
                cid = Cid("bafyreig5cdo4grj7hf53vfhcpxvhp7e2mq5gjka4j5kz7x3b5f6q2y4a7i"),
                creator = ProfileView(
                    did = labelerDid,
                    handle = Handle("mock-labeler.test"),
                    displayName = "Mock Labeler",
                    avatar = null,
                    associated = null,
                    indexedAt = Instant.fromEpochSeconds(1790260330L),
                    createdAt = Instant.fromEpochSeconds(1790260330L),
                    description = "Mock labeler for the Appearance preview.",
                    labels = null,
                    viewer = null
                ),
                policies = LabelerPolicies(
                    labelValues = listOf(LabelValue.GraphicMedia)
                ),
                likeCount = 0L,
                viewer = null,
                indexedAt = Instant.fromEpochSeconds(1790260330L),
                labels = null,
                reasonTypes = null,
                subjectTypes = null,
                subjectCollections = null
                )
            )
        )
    )

    val mockLabel = Label(
        ver = 1L,
        src = labelerDid,
        uri = Uri("at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.post/3l7qg4v3xjk2e"),
        cid = Cid("bafyreig5cdo4grj7hf53vfhcpxvhp7e2mq5gjka4j5kz7x3b5f6q2y4a7i"),
        `val` = "graphic-media",
        neg = false,
        cts = Instant.fromEpochSeconds(1790260330L)
    )
}
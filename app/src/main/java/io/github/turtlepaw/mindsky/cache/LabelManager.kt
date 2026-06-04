package io.github.turtlepaw.mindsky.cache

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.christian.ozone.BlueskyApi

class LabelManager(context: Context) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val labelerCache: LabelerCache = LabelerCache(context)
    private val labelerDefinitionsCache: LabelerDefCache = LabelerDefCache(context)

    val labelersFlow = labelerCache.labelersFlow
    val labelersDefinitionFlow = labelerDefinitionsCache.definitionsFlow

    suspend fun preWarmDefinitions(blueskyApi: BlueskyApi) {
        coroutineScope.launch {
            labelersFlow.collect {
                labelerDefinitionsCache.getDefinitions(
                    it,
                    blueskyApi
                )
            }
        }
    }

    suspend fun revalidateLabelers(blueskyApi: BlueskyApi) {
        labelerCache.fetchAndCacheLabelers(blueskyApi)
    }
}

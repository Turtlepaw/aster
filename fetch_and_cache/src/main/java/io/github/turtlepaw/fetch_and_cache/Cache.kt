package io.github.turtlepaw.fetch_and_cache

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.json.Json.Default.encodeToString
import java.io.File


data class LoadResult<V>(
    val value: V?,
    val isLoading: Boolean,
    val error: String?
)

class Cache<K, V>(
    private val fetcher: suspend (key: K) -> V,
    private val serializer: KSerializer<V>,
    private val identifier: String,
    context: Context
) {
    private val shared = CacheRegistry.getOrCreate<Map<String, V>>(identifier)

    init {
        if (!shared.isLoadedFromDisk) {
            val file = File(context.cacheDir, identifier)
            if (file.exists()) {
                try {
                    val mapSerializer =
                        MapSerializer(String.serializer(), serializer)
                    shared.value.value = decodeFromString(mapSerializer, file.readText())
                    shared.isLoadedFromDisk = true
                } catch (e: Exception) {
                    Log.e("Cache", "Error loading cache from disk for $identifier", e)
                }
            }
        }
    }

    suspend fun loadAsync(key: K, context: Context): LoadResult<V> {
        var value: V? = null
        var isLoading = true
        var error: String? = null

        try {
            value = fetcher(key)
            shared.mutex.withLock {
                val currentMap = shared.value.value ?: emptyMap()
                val updatedMap = currentMap + (key.toString() to value!!)
                shared.value.value = updatedMap
                persistResult(context, updatedMap)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message
        }
        isLoading = false

        return LoadResult(
            value = value,
            isLoading = isLoading,
            error = error
        )
    }

    @Composable
    fun load(key: K, enabled: Boolean = true): LoadResult<V> {
        val sharedMap by shared.value.collectAsState()
        var value: V? by remember(sharedMap, key) { mutableStateOf(sharedMap?.get(key.toString())) }
        var isLoading by remember(value) { mutableStateOf(value == null) }
        var error by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

        LaunchedEffect(key, enabled) {
            if (!enabled) return@LaunchedEffect
            if (value != null) return@LaunchedEffect
            
            try {
                val fetchedValue = fetcher(key)
                shared.mutex.withLock {
                    val currentMap = shared.value.value ?: emptyMap()
                    val updatedMap = currentMap + (key.toString() to fetchedValue!!)
                    shared.value.value = updatedMap
                    persistResult(context, updatedMap)
                }
                value = fetchedValue
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message
            }
            isLoading = false
        }

        return LoadResult(
            value = value,
            isLoading = isLoading,
            error = error
        )
    }

    private fun persistResult(context: Context, map: Map<String, V>) {
        try {
            val file = File(context.cacheDir, identifier)
            val mapSerializer =
                MapSerializer(String.serializer(), serializer)

            file.writeText(
                encodeToString(mapSerializer, map)
            )
        } catch (e: Exception) {
            Log.e("Cache", "Error persisting result for $identifier", e)
        }
    }
}
package io.github.turtlepaw.fetch_and_cache

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


data class CollectionCacheLoadResult<V>(
    val value: Map<String, V>,
    val isLoading: Boolean,
    val refresh: (suspend () -> Unit)?,
    val error: Throwable?
)

/**
 * Holds and persists a collection of values while fetching in background.
 */
class CollectionCache<V>(
    private val fetcher: suspend () -> Map<String, V>,
    private val serializer: KSerializer<V>,
    private val identifier: String,
    private val ttl: Duration = 1.minutes
) {
    private val shared = CacheRegistry.getOrCreate<Map<String, V>>(identifier)

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "\$type"
    }

    companion object {
        @Composable
        fun <V> rememberLoad(
            fetcher: suspend () -> Map<String, V>,
            serializer: KSerializer<V>,
            identifier: String,
            ttl: Duration = 1.minutes,
            enabled: Boolean = true
        ): CollectionCacheLoadResult<V> {
            val cache = remember { CollectionCache(fetcher, serializer, identifier, ttl) }
            return cache.load(enabled = enabled)
        }
    }

    private fun readDisk(context: Context): Map<String, V>? {
        val file = File(context.cacheDir, getIdentifier())
        if (!file.exists()) return null


        val mapSerializer =
            MapSerializer(String.serializer(), serializer)

        return json.decodeFromString(mapSerializer, file.readText())
    }

    fun getIdentifier() = identifier.hashCode().toString() + ".json"

    private fun writeDisk(context: Context, values: Map<String, V>) {
        val file = File(context.cacheDir, getIdentifier())
        file.parentFile?.mkdirs()
        val mapSerializer =
            MapSerializer(String.serializer(), serializer)


        file.writeText(
            json.encodeToString(mapSerializer, values)
        )
    }

    @Composable
    fun load(enabled: Boolean = true): CollectionCacheLoadResult<V> {
        val context = LocalContext.current

        val value by shared.value.collectAsState()
        val isLoading by shared.isLoading.collectAsState()
        val error by shared.error.collectAsState()

        val refresh: suspend () -> Unit = {
            shared.isLoading.value = true
            try {
                shared.mutex.withLock {
                    val fresh = fetcher()
                    shared.value.value = fresh
                    shared.lastFetched.value = System.currentTimeMillis()
                    writeDisk(context, fresh)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("CollectionCache", "Error refreshing $identifier", e)
                shared.error.value = e
            } finally {
                shared.isLoading.value = false
            }
        }

        LaunchedEffect(identifier) {
            if (shared.value.value != null) return@LaunchedEffect

            shared.mutex.withLock {
                if (shared.value.value != null) return@withLock
                
                // Disk second
                val file = File(context.cacheDir, getIdentifier())
                readDisk(context)?.let {
                    shared.value.value = it
                    shared.lastFetched.value = file.lastModified()
                }
            }
        }

        LaunchedEffect(identifier + "_refresh", enabled) {
            if (!enabled) return@LaunchedEffect

            val now = System.currentTimeMillis()
            if (shared.value.value != null && (now - shared.lastFetched.value) < ttl.inWholeMilliseconds) {
                return@LaunchedEffect
            }
            
            try {
                shared.mutex.withLock {
                    val fresh = fetcher()
                    shared.value.value = fresh
                    shared.lastFetched.value = System.currentTimeMillis()
                    writeDisk(context, fresh)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("CollectionCache", "Error fetching $identifier", e)
                shared.error.value = e
                shared.isLoading.value = false
                // try again in 15s
                delay(15.seconds)
                this.launch {
                    try {
                        shared.mutex.withLock {
                            val fresh = fetcher()
                            shared.value.value = fresh
                            shared.lastFetched.value = System.currentTimeMillis()
                            writeDisk(context, fresh)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("CollectionCache", "Error on retry for $identifier", e)
                        shared.error.value = e
                    }
                }
            } finally {
                shared.isLoading.value = false
            }
        }

        return CollectionCacheLoadResult(
            value = value ?: emptyMap(),
            isLoading = isLoading,
            refresh = refresh,
            error = error
        )
    }
}

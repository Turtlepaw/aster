package io.github.turtlepaw.fetch_and_cache

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
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
) {
    var memory = mutableMapOf<String, V>()
    private val mutex = Mutex()

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
        ): CollectionCacheLoadResult<V> {
            val cache = remember { CollectionCache(fetcher, serializer, identifier) }
            return cache.load()
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

    private fun writeDisk(context: Context) {
        val file = File(context.cacheDir, getIdentifier())
        file.parentFile?.mkdirs()
        val mapSerializer =
            MapSerializer(String.serializer(), serializer)


        file.writeText(
            json.encodeToString(mapSerializer, memory)
        )
    }

    @Composable
    fun load(): CollectionCacheLoadResult<V> {
        val context = LocalContext.current

        var value by remember { mutableStateOf<Map<String, V>>(memory.toMap()) }
        var isLoading by remember { mutableStateOf(memory.isEmpty()) }
        var error by remember { mutableStateOf<Throwable?>(null) }

        val refresh: suspend () -> Unit = {
            isLoading = true
            try {
                mutex.withLock {
                    val fresh = fetcher()
                    memory.clear()
                    memory.putAll(fresh)

                    value = memory.toMap()
                    writeDisk(context)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("CollectionCache", "Error refreshing $identifier", e)
                error = e
            } finally {
                isLoading = false
            }
        }

        LaunchedEffect(identifier) {
            // Memory first
            if (memory.isNotEmpty()) {
                value = memory.toMap()
                isLoading = false
            } else {
                // Disk second
                readDisk(context)?.let {
                    memory.putAll(it)
                    value = it
                    isLoading = false
                }
            }
        }

        LaunchedEffect(identifier + "_refresh") {
            try {
                mutex.withLock {
                    val fresh = fetcher()
                    memory.clear()
                    memory.putAll(fresh)

                    value = memory.toMap()
                    writeDisk(context)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("CollectionCache", "Error fetching $identifier", e)
                error = e
                isLoading = false
                // try again in 15s
                delay(15.seconds)
                this.launch {
                    try {
                        mutex.withLock {
                            val fresh = fetcher()
                            memory.clear()
                            memory.putAll(fresh)
                            value = memory.toMap()
                            writeDisk(context)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("CollectionCache", "Error on retry for $identifier", e)
                        error = e
                    }
                }
            } finally {
                isLoading = false
            }
        }

        return CollectionCacheLoadResult(
            value = value,
            isLoading = isLoading,
            refresh = refresh,
            error = error
        )
    }
}

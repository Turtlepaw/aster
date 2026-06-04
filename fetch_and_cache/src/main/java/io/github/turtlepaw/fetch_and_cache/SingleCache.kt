package io.github.turtlepaw.fetch_and_cache

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.KSerializer
import java.io.File
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

data class SingleLoadResult<V>(
    val value: V?,
    val isLoading: Boolean,
    val refresh: (suspend () -> Unit)?,
    val error: Throwable?
)

/**
 * Holds and persists a single value while fetching in background.
 */
class SingleCache<V>(
    private val fetcher: suspend () -> V,
    private val serializer: KSerializer<V>,
    private val identifier: String,
) {
    private var memory: V? = null
    private val mutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "\$type"
    }

    companion object {
        @Composable
        fun <V> rememberLoad(
            fetcher: suspend () -> V,
            serializer: KSerializer<V>,
            identifier: String,
        ): SingleLoadResult<V> {
            val cache = remember { SingleCache(fetcher, serializer, identifier) }
            return cache.load()
        }
    }

    private fun readDisk(context: Context): V? {
        val file = File(context.cacheDir, getIdentifier())
        if (!file.exists()) return null

        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: Exception) {
            Log.e("SingleCache", "Error reading disk for $identifier", e)
            null
        }
    }

    fun getIdentifier() = identifier.hashCode().toString() + ".json"

    private fun writeDisk(context: Context, value: V) {
        val file = File(context.cacheDir, getIdentifier())
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(serializer, value))
    }

    @Composable
    fun load(): SingleLoadResult<V> {
        val context = LocalContext.current

        var value by remember { mutableStateOf<V?>(memory) }
        var isLoading by remember { mutableStateOf(memory == null) }
        var error by remember { mutableStateOf<Throwable?>(null) }

        val refresh: suspend () -> Unit = {
            isLoading = true
            try {
                mutex.withLock {
                    val fresh = fetcher()
                    memory = fresh
                    value = fresh
                    writeDisk(context, fresh)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SingleCache", "Error refreshing $identifier", e)
                error = e
            } finally {
                isLoading = false
            }
        }

        LaunchedEffect(identifier) {
            // Memory first
            if (memory != null) {
                value = memory
                isLoading = false
            } else {
                // Disk second
                readDisk(context)?.let {
                    memory = it
                    value = it
                    isLoading = false
                }
            }
        }

        LaunchedEffect(identifier + "_refresh") {
            try {
                mutex.withLock {
                    val fresh = fetcher()
                    memory = fresh
                    value = fresh
                    writeDisk(context, fresh)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SingleCache", "Error fetching $identifier", e)
                error = e
                isLoading = false
                // try again in 15s
                delay(15.seconds)
                this.launch {
                    try {
                        mutex.withLock {
                            val fresh = fetcher()
                            memory = fresh
                            value = fresh
                            writeDisk(context, fresh)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("SingleCache", "Error on retry for $identifier", e)
                        error = e
                    }
                }
            } finally {
                isLoading = false
            }
        }

        return SingleLoadResult(
            value = value,
            isLoading = isLoading,
            refresh = refresh,
            error = error
        )
    }
}

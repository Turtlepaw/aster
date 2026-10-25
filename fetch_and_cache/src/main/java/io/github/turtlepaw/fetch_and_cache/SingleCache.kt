package io.github.turtlepaw.fetch_and_cache

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
    private val ttl: Duration = 1.minutes
) {
    private val shared = CacheRegistry.getOrCreate<V>(identifier)

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
            ttl: Duration = 1.minutes,
            enabled: Boolean = true
        ): SingleLoadResult<V> {
            val cache = remember { SingleCache(fetcher, serializer, identifier, ttl) }
            return cache.load(enabled = enabled)
        }
    }

    private suspend fun readDisk(context: Context): V? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, getIdentifier())
        if (!file.exists()) return@withContext null

        return@withContext try {
            json.decodeFromString(serializer, file.readText())
        } catch (e: Exception) {
            Log.e("SingleCache", "Error reading disk for $identifier", e)
            null
        }
    }

    fun getIdentifier() = identifier.hashCode().toString() + ".json"

    private suspend fun writeDisk(context: Context, value: V) = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, getIdentifier())
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(serializer, value))
    }

    @Composable
    fun load(enabled: Boolean = true): SingleLoadResult<V> {
        val context = LocalContext.current

        val value by shared.value.collectAsState()
        val isLoading by shared.isLoading.collectAsState()
        val error by shared.error.collectAsState()

        val refresh: suspend () -> Unit = {
            Log.d("SingleCache", "Refreshing $identifier")
            shared.isLoading.value = true
            try {
                shared.mutex.withLock {
                    val fresh = withTimeout(30.seconds) { fetcher() }
                    Log.d("SingleCache", "Fetcher finished for $identifier")
                    shared.value.value = fresh
                    shared.lastFetched.value = System.currentTimeMillis()
                    writeDisk(context, fresh)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.e("SingleCache", "Error refreshing $identifier", e)
                shared.error.value = e
            } finally {
                shared.isLoading.value = false
            }
        }

        LaunchedEffect(identifier, enabled) {
            if (!enabled) return@LaunchedEffect
            if (shared.value.value != null) return@LaunchedEffect

            shared.mutex.withLock {
                if (shared.value.value != null) return@withLock

                // Disk second
                val file = File(context.cacheDir, getIdentifier())
                val diskValue = readDisk(context)
                if (diskValue != null) {
                    Log.d("SingleCache", "Loading $identifier from disk")
                    shared.value.value = diskValue
                    shared.lastFetched.value = file.lastModified()
                }
            }
        }

        LaunchedEffect(identifier + "_refresh", enabled) {
            if (!enabled) return@LaunchedEffect

            val now = System.currentTimeMillis()
            if (shared.value.value != null && (now - shared.lastFetched.value) < ttl.inWholeMilliseconds) {
                Log.d("SingleCache", "Skipping refresh for $identifier (within TTL)")
                return@LaunchedEffect
            }

            Log.d("SingleCache", "Starting refresh for $identifier (hasValue=${value != null})")
            if (shared.value.value == null) {
                shared.isLoading.value = true
            }

            try {
                shared.mutex.withLock {
                    val fresh = withTimeout(30.seconds) { fetcher() }
                    Log.d("SingleCache", "Fetcher finished for $identifier")
                    shared.value.value = fresh
                    shared.lastFetched.value = System.currentTimeMillis()
                    writeDisk(context, fresh)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                Log.e("SingleCache", "Error fetching $identifier", e)
                shared.error.value = e
                shared.isLoading.value = false
                // try again in 15s
                delay(15.seconds)
                this.launch {
                    try {
                        shared.mutex.withLock {
                            val fresh = withTimeout(30.seconds) { fetcher() }
                            shared.value.value = fresh
                            shared.lastFetched.value = System.currentTimeMillis()
                            writeDisk(context, fresh)
                        }
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        Log.e("SingleCache", "Error on retry for $identifier", e)
                        shared.error.value = e
                    } finally {
                        shared.isLoading.value = false
                    }
                }
            } finally {
                shared.isLoading.value = false
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

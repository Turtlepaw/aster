package io.github.turtlepaw.fetch_and_cache

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer


data class InfiniteLoadResult<V>(
    val value: List<V>?,
    val loadMore: (suspend () -> Unit)?,
    val refresh: (suspend () -> Unit)?,
    val isLoading: Boolean,
    val hasMore: Boolean = true,
    val error: String?
)

@Serializable
data class InfiniteCacheState<V>(
    val cursor: String?,
    val values: List<V>
)

class InfiniteCache<V>(
    private val fetcher: suspend (key: String?) -> Pair<String?, List<V>>,
    private val serializer: KSerializer<V>,
    private val identifier: String,
    context: Context
) {
    private val shared = CacheRegistry.getOrCreate<List<V>>(identifier)

    private val json: Json get() = defaultJson

    companion object {
        private val defaultJson = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "\$type"
        }
    }

    init {
        if (!shared.isLoadedFromDisk) {
            val file = File(context.cacheDir, identifier)
            if (file.exists()) {
                try {
                    val text = file.readText()
                    val trimmed = text.dropWhile { it.isWhitespace() }
                    if (trimmed.startsWith("{")) {
                        val stateSerializer = InfiniteCacheState.serializer(serializer)
                        val state = json.decodeFromString(stateSerializer, text)
                        shared.value.value = state.values
                        shared.cursor.value = state.cursor
                    } else if (trimmed.startsWith("[")) {
                        val listSerializer = ListSerializer(serializer)
                        shared.value.value = json.decodeFromString(listSerializer, text)
                    }
                    shared.isLoadedFromDisk = true
                } catch (e: Exception) {
                    Log.e("InfiniteCache", "Error loading cache from disk for $identifier", e)
                }
            }
        }
    }

    suspend fun loadAsync(context: Context): InfiniteLoadResult<V> {
        shared.isLoading.value = true
        var error: String? = null

        try {
            val previousCursor = shared.cursor.value
            val result = fetcher(previousCursor)
            
            shared.mutex.withLock {
                val currentValues = shared.value.value ?: emptyList()
                val updatedValues = if (previousCursor == null) {
                    result.second
                } else {
                    currentValues + result.second
                }
                
                shared.value.value = updatedValues
                shared.cursor.value = result.first
                persistResult(context, updatedValues, result.first)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message
        } finally {
            shared.isLoading.value = false
        }

        return InfiniteLoadResult(
            value = shared.value.value,
            isLoading = shared.isLoading.value,
            hasMore = shared.cursor.value != null,
            loadMore = {
                loadAsync(context)
            },
            refresh = {
                refreshAsync(context)
            },
            error = error
        )
    }

    suspend fun refreshAsync(context: Context): InfiniteLoadResult<V> {
        shared.isLoading.value = true
        var error: String? = null

        try {
            val result = fetcher(null)
            
            shared.mutex.withLock {
                shared.value.value = result.second
                shared.cursor.value = result.first
                persistResult(context, result.second, result.first)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message
        } finally {
            shared.isLoading.value = false
        }

        return InfiniteLoadResult(
            value = shared.value.value,
            isLoading = shared.isLoading.value,
            hasMore = shared.cursor.value != null,
            loadMore = {
                loadAsync(context)
            },
            refresh = {
                refreshAsync(context)
            },
            error = error
        )
    }

    @Composable
    fun load(enabled: Boolean = true): InfiniteLoadResult<V> {
        val value by shared.value.collectAsState()
        val cursor by shared.cursor.collectAsState()
        val isLoading by shared.isLoading.collectAsState()
        val errorFlow by shared.error.collectAsState()
        val error = errorFlow?.message
        
        val context = LocalContext.current

        val refresh: suspend () -> Unit = {
            refreshAsync(context)
        }

        val loadMore: suspend () -> Unit = {
            loadAsync(context)
        }

        LaunchedEffect(enabled) {
            if (!enabled) return@LaunchedEffect

            val now = System.currentTimeMillis()
            if (value != null && (now - shared.lastFetched.value) < 60 * 1000L) {
                return@LaunchedEffect
            }
            
            try {
                val result = fetcher(null)
                shared.mutex.withLock {
                    shared.value.value = result.second
                    shared.cursor.value = result.first
                    shared.lastFetched.value = System.currentTimeMillis()
                    persistResult(context, result.second, result.first)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                shared.error.value = e
                Log.e("InfiniteCache", "Error fetching $identifier", e)
            }
        }

        return InfiniteLoadResult(
            value = value,
            isLoading = isLoading,
            hasMore = cursor != null || value.isNullOrEmpty(),
            loadMore = loadMore,
            refresh = refresh,
            error = error
        )
    }

    private fun persistResult(context: Context, values: List<V>, cursor: String?) {
        try {
            val file = File(context.cacheDir, identifier)
            file.writeText(
                json.encodeToString(
                    InfiniteCacheState.serializer(serializer),
                    InfiniteCacheState(cursor, values)
                )
            )
        } catch (e: Exception) {
            Log.e("InfiniteCache", "Error persisting result for $identifier", e)
        }
    }
}

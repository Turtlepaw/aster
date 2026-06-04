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
    var memory = mutableListOf<V>()
    val mutex = Mutex()
    val cursor = mutableStateOf<String?>(null)

    private val json: Json get() = defaultJson

    companion object {
        private val defaultJson = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "\$type"
        }
    }

    init {
        val file = File(context.cacheDir, identifier)
        if (file.exists()) {
            val text = file.readText()
            val trimmed = text.dropWhile { it.isWhitespace() }
            if (trimmed.startsWith("{")) {
                val stateSerializer = InfiniteCacheState.serializer(serializer)
                val state = json.decodeFromString(stateSerializer, text)
                memory = state.values.toMutableList()
                cursor.value = state.cursor
            } else if (trimmed.startsWith("[")) {
                val listSerializer =
                    ListSerializer(serializer)
                memory = json.decodeFromString(listSerializer, text).toMutableList()
            }
        }
    }

    suspend fun loadAsync(context: Context): InfiniteLoadResult<V> {
        var value: MutableList<V>? = memory
        var isLoading = true
        var error: String? = null

        try {
            val previousCursor = cursor.value
            val result = fetcher(previousCursor)
            cursor.value = result.first

            if (previousCursor == null) {
                value = result.second.toMutableList()
            } else {
                if(value.isNullOrEmpty()){
                    value = result.second.toMutableList()
                } else
                    value.addAll( result.second)
            }

            mutex.withLock {
                memory = value
                persistResult(context)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message
        }
        isLoading = false

        return InfiniteLoadResult(
            value = value,
            isLoading = isLoading,
            hasMore = cursor.value != null,
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
        var value: MutableList<V>? = null
        var isLoading = true
        var error: String? = null

        try {
            val result = fetcher(null)
            cursor.value = result.first
            value = result.second.toMutableList()

            mutex.withLock {
                memory = value
                persistResult(context)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            error = e.message
        }
        isLoading = false

        return InfiniteLoadResult(
            value = value,
            isLoading = isLoading,
            hasMore = cursor.value != null,
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
    fun load(defaultIsLoading: Boolean? = null): InfiniteLoadResult<V> {
        var value: List<V>? by remember(memory) { mutableStateOf(memory.toList()) }
        var isLoading by remember { mutableStateOf(defaultIsLoading ?: value.isNullOrEmpty()) }
        var hasMore by remember { mutableStateOf(cursor.value != null || value.isNullOrEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

        val refresh: suspend () -> Unit = {
            isLoading = true
            try {
                val result = fetcher(null)
                cursor.value = result.first
                hasMore = result.first != null

                val updated = result.second
                value = updated

                mutex.withLock {
                    memory = updated.toMutableList()
                    persistResult(context)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message
            }
            isLoading = false
        }

        val loadMore: suspend () -> Unit = {
            isLoading = true
            try {
                val previousCursor = cursor.value
                val result = fetcher(previousCursor)
                cursor.value = result.first
                hasMore = result.first != null

                val updated = (value ?: emptyList()) + result.second
                value = updated

                mutex.withLock {
                    memory = updated.toMutableList()
                    persistResult(context)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message
            }
            isLoading = false
        }

        LaunchedEffect(Unit) {
            try {
                val result = fetcher(null)
                cursor.value = result.first
                hasMore = result.first != null

                val updated = result.second
                value = updated

                mutex.withLock {
                    memory = updated.toMutableList()
                    persistResult(context)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                error = e.message
                Log.e("InfiniteCache", "Error fetching $identifier", e)
            }
            isLoading = false
        }

        return InfiniteLoadResult(
            value = value,
            isLoading = isLoading,
            hasMore = hasMore,
            loadMore = loadMore,
            refresh = refresh,
            error = error
        )
    }

    private fun persistResult(context: Context){
        val file = File(context.cacheDir, identifier)

        file.writeText(
            json.encodeToString(
                InfiniteCacheState.serializer(serializer),
                InfiniteCacheState(cursor.value, memory)
            )
        )
    }
}

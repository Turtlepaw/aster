package io.github.turtlepaw.fetch_and_cache

import android.content.Context
import androidx.compose.runtime.*
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
    val isLoading: Boolean,
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
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "\$type"
    }
    var memory = mutableListOf<V>()
    val mutex = Mutex()
    val cursor = mutableStateOf<String?>(null)

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
            error = e.message
        }
        isLoading = false

        return InfiniteLoadResult(
            value = value,
            isLoading = isLoading,
            loadMore = {
                loadAsync(context)
            },
            error = error
        )
    }

    @Composable
    fun load(): InfiniteLoadResult<V> {
        var value: List<V>? by remember { mutableStateOf(memory.toList()) }
        var isLoading by remember { mutableStateOf(value.isNullOrEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            try {
                val previousCursor = cursor.value
                val result = fetcher(previousCursor)
                cursor.value = result.first

                val updated =
                    if (previousCursor == null)
                        result.second
                    else if (value.isNullOrEmpty())
                        result.second
                    else
                        value!! + result.second
                value = updated

                mutex.withLock {
                    memory = updated.toMutableList()
                    persistResult(context)
                }
            } catch (e: Exception) {
                error = e.message
            }
            isLoading = false
        }

        return InfiniteLoadResult(
            value = value,
            isLoading = isLoading,
            loadMore = {
                loadAsync(context)
            },
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

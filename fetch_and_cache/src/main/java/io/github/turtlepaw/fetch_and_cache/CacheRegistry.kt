package io.github.turtlepaw.fetch_and_cache

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object CacheRegistry {
    private val registry = mutableMapOf<String, SharedCacheState<*>>()
    private val mutex = Mutex()

    @Suppress("UNCHECKED_CAST")
    fun <V> getOrCreate(identifier: String): SharedCacheState<V> = synchronized(this) {
        registry.getOrPut(identifier) { SharedCacheState<V>() } as SharedCacheState<V>
    }

    class SharedCacheState<V> {
        val value = MutableStateFlow<V?>(null)
        val isLoading = MutableStateFlow(false)
        val error = MutableStateFlow<Throwable?>(null)
        val lastFetched = MutableStateFlow<Long>(0)
        val mutex = Mutex()
        var isLoadedFromDisk = false
        val cursor = MutableStateFlow<String?>(null)
    }
}

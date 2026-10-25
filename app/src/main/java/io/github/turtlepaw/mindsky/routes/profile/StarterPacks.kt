package io.github.turtlepaw.mindsky.routes.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bsky.graph.GetActorStarterPacksQueryParams
import app.bsky.graph.GetStarterPacksQueryParams
import app.bsky.graph.StarterPackViewBasic
import app.bsky.graph.Starterpack
import fyi.kittens.shared.di.LocalSessionProvider
import fyi.kittens.shared.ui.LoadMore
import fyi.kittens.shared.ui.embeds.EmbedStructure
import io.github.turtlepaw.fetch_and_cache.InfiniteCache
import io.github.turtlepaw.fetch_and_cache.InfiniteLoadResult
import io.github.turtlepaw.fetch_and_cache.SingleCache
import kotlinx.coroutines.launch
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import sh.christian.ozone.api.Did

@Composable
fun rememberStarterPacks(identifier: Did, enabled: Boolean): InfiniteLoadResult<StarterPackViewBasic> {
    val api = LocalMindskyApi.current
    val context = LocalContext.current

    return remember(identifier, api) {
        InfiniteCache(
            identifier = "starter_packs_${identifier.did}",
            serializer = StarterPackViewBasic.serializer(),
            fetcher = { cursor ->
                val result = api.getActorStarterPacks(
                    GetActorStarterPacksQueryParams(
                        actor = identifier,
                        cursor = cursor
                    )
                ).requireResponse()

                result.cursor to result.starterPacks
            },
            context = context
        )
    }.load(enabled = enabled)
}

fun LazyListScope.starterPacks(
    starterPacks: InfiniteLoadResult<StarterPackViewBasic>,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    if (starterPacks.isLoading && starterPacks.value.isNullOrEmpty()) {
        items(8) {
            LoadingListItem()
        }
    } else if (starterPacks.error != null) {
        item {
            Text(
                "Error: ${starterPacks.error}",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error
            )
        }
    } else if (starterPacks.value != null) {
        if (starterPacks.value!!.isEmpty()) {
            item {
                Text(
                    "No starter packs found",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            items(starterPacks.value!!, key = { it.uri.atUri }) {
                StarterPackItem(it)
            }

            if (starterPacks.hasMore) {
                item {
                    LoadMore(starterPacks.value!!.size) {
                        coroutineScope.launch {
                            starterPacks.loadMore?.invoke()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StarterPackItem(starterPack: StarterPackViewBasic) {
    val pack = starterPack.record.decodeAs<Starterpack>()

    ItemStructure(
        icon = {
            Box(
                modifier = it
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.shapes.small
                    )
            ){
                Icon(
                    Icons.Rounded.Category,
                    contentDescription = "Category",
                    modifier = Modifier.padding(8.dp)
                )
            }
        },
        title = {
            Text(
                pack.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        subtitle = {
            Text(
                "Starter pack by @${starterPack.creator.handle}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    )
}
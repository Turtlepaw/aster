package io.github.turtlepaw.mindsky.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.SearchActorsQueryParams
import app.bsky.actor.SearchActorsResponse
import app.bsky.actor.SearchActorsTypeaheadQueryParams
import app.bsky.unspecced.GetTrendingTopicsQueryParams
import app.bsky.unspecced.GetTrendingTopicsResponse
import app.bsky.unspecced.GetTrendsQueryParams
import app.bsky.unspecced.GetTrendsResponse
import com.atproto.admin.SearchAccountsQueryParams
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FeedGeneratorDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.Cache
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import kotlinx.coroutines.launch
import sh.christian.ozone.api.AtUri
import kotlin.collections.emptyList

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Search(navigator: DestinationsNavigator) {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
                placeholder = {
                    Text(modifier = Modifier.clearAndSetSemantics {}, text = "Search")
                },
                leadingIcon = {
                    if (searchBarState.currentValue == SearchBarValue.Expanded) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    searchBarState.animateToCollapsed()
                                }
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    } else {
                        Icon(Icons.Rounded.Search, stringResource(R.string.search))
                    }
                },
                trailingIcon = {
                    if (textFieldState.text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                textFieldState.setTextAndPlaceCursorAtEnd("")
                            }
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.clear_search)
                            )
                        }
                    }
                },
            )
        }

    val api = LocalMindskyApi.current
    var actors by remember { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(textFieldState.text) {
        if (textFieldState.text.isBlank()) {
            actors = emptyList()
            return@LaunchedEffect
        }

        val response = api.searchActorsTypeahead(
            SearchActorsTypeaheadQueryParams(
                q = textFieldState.text.toString(),
                limit = 6,
            )
        )

        try {
            actors = response.requireResponse().actors
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val context = LocalContext.current
    val trending = Cache<Unit?, GetTrendsResponse>(
        fetcher = {
            api.getTrendsUnspecced(
                GetTrendsQueryParams(
                    limit = 5,
                )
            ).requireResponse()
        },
        serializer = GetTrendsResponse.serializer(),
        identifier = "trends",
        context = context
    ).load(null)

    Scaffold { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                SearchBar(state = searchBarState, inputField = inputField)
            }
            if(trending.isLoading){
                item {
                    Text("Loading trending topics...")
                }
            } else if(trending.error != null){
                item {
                    Text("Failed to load trending topics: ${trending.error}")
                }
            } else if(trending.value != null){
                itemsIndexed(trending.value!!.trends) { index, topic ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val regex = """/profile/([^/]+)/feed/(\d+)""".toRegex()
                                val match = regex.find(topic.link)

                                val id = match?.groupValues?.get(2)

                                navigator.navigate(
                                    FeedGeneratorDestination(
                                        AtUri(
                                            "at://did:plc:qrz3lhbyuxbeilrc6nekdqme/app.bsky.feed.generator/${id}"
                                        )
                                    )
                                )
                            }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                        ) {
                            Text(
                                (index+1).toString()
                            )
                            Column() {
                                Text(
                                    text = topic.displayName
                                )

                                if(topic.category != null) Text(
                                    text = topic.category!!,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                )
                            }
                        }
//                        Box {
//                           if(topic.)
//                        }
                    }
                }
            }
        }
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
            LazyColumn() {
                items(actors) { actor ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                navigator.navigate(
                                    ProfileDestination(actor.did.did)
                                )
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start)
                    ) {
                        Avatar(
                            modifier = Modifier.size(30.dp),
                            avatarUrl = actor.avatar?.uri,
                            contentDescription = "${actor.displayName ?: actor.handle.handle}'s avatar",
                        )
                        Column {
                            Text(text = actor.displayName ?: actor.handle.handle)
                            Text(
                                text = "@${actor.handle.handle}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
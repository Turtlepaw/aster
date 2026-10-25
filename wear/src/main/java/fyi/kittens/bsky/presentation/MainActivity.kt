package fyi.kittens.bsky.presentation

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.bsky.actor.GetProfileQueryParams
import app.bsky.actor.GetProfileResponse
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.FeedViewPost
import fyi.kittens.bsky.MindskyWearApplication
import fyi.kittens.bsky.R
import fyi.kittens.bsky.presentation.components.post.LoadingPost
import fyi.kittens.bsky.presentation.components.post.PostComponent
import fyi.kittens.bsky.presentation.routes.FeedItem
import fyi.kittens.bsky.presentation.routes.FeedPickerScreen
import fyi.kittens.bsky.presentation.routes.FeedScreen
import fyi.kittens.bsky.presentation.routes.PostScreen
import fyi.kittens.bsky.presentation.routes.ProfileScreen
import fyi.kittens.bsky.presentation.routes.rememberPinnedFeeds
import fyi.kittens.bsky.presentation.routes.rememberSelectedFeed
import fyi.kittens.bsky.presentation.theme.MindskyTheme
import fyi.kittens.shared.di.LocalMindskyApi
import fyi.kittens.shared.di.LocalPostState
import fyi.kittens.shared.di.PostsState
import fyi.kittens.shared.ui.LoadMore
import fyi.kittens.shared.ui.PostDensity
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.fetch_and_cache.SingleLoadResult
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did

val LocalFeeds = compositionLocalOf<SingleLoadResult<List<Pair<String, FeedItem>>>> { error("No feeds provided") }
val LocalSession = compositionLocalOf<fyi.kittens.shared.auth.UserSession?> { error("No session provided") }
val LocalSelectedFeedIndex = compositionLocalOf<MutableState<Int?>> { error("No selection provided") }
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val application = applicationContext as MindskyWearApplication

            val session by application.sessionManager.sessionFlow.collectAsState()
            val api by application.blueskyApiFlow.collectAsState()

            val postState = remember { PostsState() }

            LaunchedEffect(session) {
                Log.d("MainActivity", "Session: $session")
            }

            MindskyTheme {
                AppScaffold {
                    val navController = rememberSwipeDismissableNavController()
                    if (session == null) {
                        NotSignedIn()
                    } else if (api == null) {
                        GreetingScreen(null)
                    } else {
                        val viewModel: WearFeedViewModel =
                            viewModel(
                                key = session?.did,
                                factory = object :
                                    androidx.lifecycle.ViewModelProvider.Factory {
                                    override fun <T : androidx.lifecycle.ViewModel> create(
                                        modelClass: Class<T>
                                    ): T {
                                        @Suppress("UNCHECKED_CAST")
                                        return WearFeedViewModel(api!!) as T
                                    }
                                }
                            )

                        val currentApi = rememberUpdatedState(api)
                        val feedsCache = rememberPinnedFeeds(session?.did) { currentApi.value }
                        val feeds = feedsCache.load()
                        val currentFeed = rememberSelectedFeed(session?.did)

                        LaunchedEffect(currentFeed.value, feeds.value) {
                            val index = currentFeed.value
                            val feedsList = feeds.value
                            Log.d("MainActivity", "LaunchedEffect: index=$index, feedsListSize=${feedsList?.size}")

                            if (index == null || feedsList == null) return@LaunchedEffect

                            val feed = if (index >= 0) {
                                feedsList.getOrNull(index)?.second ?: FeedItem.Following
                            } else {
                                FeedItem.Following
                            }

                            Log.d("MainActivity", "Loading feed: ${feed.getUri()}")
                            viewModel.loadFeed(feed)
                        }

                        LaunchedEffect(feeds.isLoading, feeds.value) {
                            Log.d("MainActivity", "Feeds: isLoading=${feeds.isLoading}, valueSize=${feeds.value?.size}")
                        }

                        CompositionLocalProvider(
                            LocalMindskyApi provides api!!,
                            LocalPostState provides postState,
                            LocalFeeds provides feeds,
                            LocalSession provides session,
                            LocalSelectedFeedIndex provides currentFeed
                        ) {
                            SwipeDismissableNavHost(
                                navController = navController,
                                startDestination = Screen.Feed.route
                            ) {
                                composable(Screen.Feed.route) {
                                    val currentFeeds = LocalFeeds.current
                                    if (currentFeeds.isLoading) {
                                        LoadingScreen("Loading feeds")
                                    } else if (currentFeeds.error != null) {
                                        ErrorScreen("Failed to load feeds: ${currentFeeds.error!!.message}")
                                    } else {
                                        FeedScreen(viewModel, navController)
                                    }
                                }
                                composable(Screen.PostDetails.route) {
                                    val uri = Uri.decode(it.arguments?.getString("uri")!!)
                                    val post =
                                        viewModel.feed.collectAsState().value.find { post -> post.post.uri.atUri == uri }

                                    PostScreen(post, navController)
                                }
                                composable(Screen.Profile.route) {
                                    val did = it.arguments?.getString("did")!!
                                    val currentApi = rememberUpdatedState(api)
                                    val user = remember(did) {
                                        SingleCache<ProfileViewDetailed>(
                                            fetcher = {
                                                currentApi.value!!.getProfile(
                                                    GetProfileQueryParams(
                                                        Did(did)
                                                    )
                                                ).requireResponse()
                                            },
                                            identifier = "profile:$did",
                                            serializer = ProfileViewDetailed.serializer()
                                        )
                                    }.load()

                                    if (user.isLoading) {
                                        LoadingScreen("Loading profile")
                                    } else if (user.error != null) {
                                        ErrorScreen("Failed to load profile: ${user.error!!.message}")
                                    } else {
                                        ProfileScreen(user.value!!)
                                    }
                                }
                                composable(Screen.FeedPicker.route) {
                                    FeedPickerScreen(navController)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotSignedIn() {
    ScreenScaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.not_signed_in),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ErrorScreen(error: String) {
    ScreenScaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                5.dp,
                alignment = Alignment.CenterVertically
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = "Warning"
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun GreetingScreen(greetingName: String?) {
    ScreenScaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            if (greetingName != null) Text(
                text = if (greetingName != "Guest") {
                    "Connecting as $greetingName..."
                } else {
                    stringResource(R.string.hello_world, greetingName)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun LoadingScreen(text: String? = null) {
    ScreenScaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            if (text != null) Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}
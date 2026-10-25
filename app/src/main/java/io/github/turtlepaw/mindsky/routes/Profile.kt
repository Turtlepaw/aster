package io.github.turtlepaw.mindsky.routes

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.bsky.feed.PostView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import io.github.turtlepaw.mindsky.components.sheets.AccountSheet
import io.github.turtlepaw.mindsky.components.media.NowPlayingProfile
import io.github.turtlepaw.mindsky.media.NowPlayingStore
import fyi.kittens.shared.ui.Avatar
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.db.Engagement
import io.github.turtlepaw.mindsky.db.Engagement_
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalProfileModel
import io.github.turtlepaw.mindsky.di.LocalScrollToTop
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.utils.ApiUtils.fetchChunkedPosts
import io.github.turtlepaw.mindsky.viewmodels.ProfileUiState
import io.github.turtlepaw.mindsky.viewmodels.rememberAuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun MyProfile(navigator: DestinationsNavigator) {
    var likes by remember { mutableStateOf<List<Pair<Engagement?, PostView>>?>(null) }
    val api = LocalMindskyApi.current
    val sessionManager = LocalSessionManager.current
    val session by sessionManager.sessionFlow.collectAsState()
    val listState = rememberLazyListState()
    val scrollToTopHandler = LocalScrollToTop.current
    val coroutineScope = rememberCoroutineScope()
    
    var isAccountSwitcherOpen by remember { mutableStateOf(false) }

    DisposableEffect(listState) {
        val handler: () -> Unit = {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
            }
            Unit
        }
        scrollToTopHandler.value = handler
        onDispose {
            if (scrollToTopHandler.value === handler) {
                scrollToTopHandler.value = null
            }
        }
    }

    LaunchedEffect(api, session) {
        val currentDid = session?.did ?: return@LaunchedEffect
        val box = ObjectBox.store.boxFor(Engagement::class.java)
        // Fetch only the most recent 40 likes to avoid overloading the API and memory
        val all = box.query(Engagement_.userDid.equal(currentDid))
            .orderDesc(Engagement_.createdAt)
            .build()
            .find(0, 40)

        Log.d("Profile", "Found ${all.size} likes (limited from total)")
        likes = api.fetchChunkedPosts(
            all.map { it to it.uri }
        )
    }

    val profileModel = LocalProfileModel.current
    val profileUiState by profileModel.uiState.collectAsState()
    val authViewModel = rememberAuthViewModel()
    val nowPlaying by NowPlayingStore.nowPlaying.collectAsState()

    AccountSheet(
        isOpen = isAccountSwitcherOpen,
        onAccountSwitch = {
            isAccountSwitcherOpen = false
        },
        onDismiss = { isAccountSwitcherOpen = false }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            state = listState,
        ) {
            item {
                when (profileUiState) {
                    is ProfileUiState.Loading -> {
                        LinearWavyProgressIndicator()
                    }

                    is ProfileUiState.Error -> {
                        Text("Error loading profile")
                    }

                    is ProfileUiState.Success -> {
                        val profile = (profileUiState as ProfileUiState.Success).profile
                        Row(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable {
                                    isAccountSwitcherOpen = true
                                }
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                20.dp,
                                Alignment.Start
                            )
                        ) {
                            Avatar(
                                modifier = Modifier.size(64.dp),
                                profile.avatar?.uri,
                                "${profile.handle} avatar",
                                clip = MaterialTheme.shapes.small
                            )

                            Column {
                                Text(
                                    text = profile.displayName ?: profile.handle.handle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@${profile.handle.handle}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap to switch account",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            item {
                NowPlayingProfile(nowPlaying)
            }
            item {
                Button(
                    onClick = {
                        authViewModel.signOut()
                    },
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Logout"
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Sign out")
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.medium
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Lightbulb,
                                contentDescription = "Favorite",
                            )
                            Text("Likes", style = MaterialTheme.typography.titleMedium)
                        }

                        Text(
                            "We're using your likes to generate a personalized feed for you. " + "\n\n" +
                                    "These posts below are liked posts and the numbers represent the relevancy of the post, and do not represent the quality of the post.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (likes != null) {
                items(likes!!) {
                    PostComponent(
                        it.second,
                        navigator,
                        reason = null,
//                        discoveryContext = { modifier ->
//                            PostInsightsContext(
//                                it.first?.embedding?.first()?.toDouble() ?: 0.0,
//                                InsightType.Vector,
//                                modifier
//                            )
                        //},
                    ) {
                        navigator.navigate(FullsizePostDestination(it.second.uri.atUri))
                    }
                }
            } else {
                Loading()
            }
        }
    }
}

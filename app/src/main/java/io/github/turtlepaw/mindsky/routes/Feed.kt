package io.github.turtlepaw.mindsky.routes

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import app.bsky.feed.GeneratorView
import app.bsky.graph.ListView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DownloadModelDestination
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import io.github.turtlepaw.mindsky.components.post.ThreadedPostComponent
import fyi.kittens.shared.FeedTuner
import com.ramcosta.composedestinations.generated.destinations.SettingsDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.generated.destinations.PdsListDestination
import io.github.turtlepaw.mindsky.components.AccountSwitcherSheet
import fyi.kittens.shared.R
import fyi.kittens.shared.ui.Avatar
import fyi.kittens.shared.ui.LoadMore
import io.github.turtlepaw.mindsky.components.sheets.AccountSheet
import io.github.turtlepaw.mindsky.components.TopBarBackground
import io.github.turtlepaw.mindsky.components.TopBarInteractiveElements
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.PostInsightsContext
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalProfileModel
import io.github.turtlepaw.mindsky.di.LocalScrollToTop
import io.github.turtlepaw.mindsky.logic.ModelDownloadWorker
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.replaceCurrent
import io.github.turtlepaw.mindsky.viewmodels.FeedViewModel
import io.github.turtlepaw.mindsky.viewmodels.ProfileUiState
import io.github.turtlepaw.mindsky.workers.FeedWorker
import io.github.turtlepaw.mindsky.workers.SignalProcessingWorker
import io.github.turtlepaw.mindsky.workers.WorkerManager.enqueueImmediateWorkers
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.AtUri
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

class FeedViewModelFactory(private val api: BlueskyApi, private val context: Context) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FeedViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FeedViewModel(api, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${'$'}{modelClass.name}")
    }
}

sealed class FeedDestination(open val title: String) {

    object Following : FeedDestination("Following")
    object ForYou : FeedDestination("For You")

    data class UserFeed(
        val id: String,
        override val title: String
    ) : FeedDestination(title)
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedWorkerProgressDisplay(feedWorkerInfo: WorkInfo?) {
    if (feedWorkerInfo != null && (feedWorkerInfo.state == WorkInfo.State.RUNNING || feedWorkerInfo.state == WorkInfo.State.ENQUEUED)) {
        val progressData = feedWorkerInfo.progress
        val stageNameString = progressData.getString("stage") ?: FeedWorker.WorkStage.STARTING.name
        val currentProgressInt = progressData.getInt("progress", 0)
        // Ensure progressFraction is correctly calculated (progress is 0-100)
        val animatedProgress by animateFloatAsState(
            targetValue = currentProgressInt / 100f,
            label = "feedWorkerProgress"
        )

        val displayStageName = try {
            FeedWorker.WorkStage.valueOf(stageNameString).displayName
        } catch (_: IllegalArgumentException) {
            try {
                SignalProcessingWorker.Stage.valueOf(stageNameString).displayName
            } catch (_: IllegalArgumentException) {
                stageNameString // Fallback if stage name is somehow not in enum
            }
        }

        // Determine if progress is indeterminate
        // It's indeterminate if ENQUEUED, or if RUNNING but no "progress" key yet, or if progress is 0 for a non-STARTING stage
        val isIndeterminate = feedWorkerInfo.state == WorkInfo.State.ENQUEUED ||
                (feedWorkerInfo.state == WorkInfo.State.RUNNING && !progressData.keyValueMap.containsKey(
                    "progress"
                )) ||
                (feedWorkerInfo.state == WorkInfo.State.RUNNING && currentProgressInt == 0 && stageNameString != FeedWorker.WorkStage.STARTING.name && stageNameString != FeedWorker.WorkStage.COMPLETE.name)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp) // Padding around the column
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.shapes.small
                )
                .padding(horizontal = 14.dp, vertical = 12.dp) // Padding inside the background
        ) {
            Text(
                text = if (feedWorkerInfo.state == WorkInfo.State.ENQUEUED) "Sync starting..." else "Sync: $displayStageName ${if (!isIndeterminate) "($currentProgressInt%)" else ""}",
                style = MaterialTheme.typography.bodySmall, // Slightly smaller text
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (isIndeterminate) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Serializable
sealed class FeedItem {
    @Serializable
    data class FeedGenerator(val generator: GeneratorView) : FeedItem()

    @Serializable
    data class List(val list: ListView) : FeedItem()

    @Serializable
    data class Following(val target: String? = "") : FeedItem()

    @Serializable
    data object ForYou : FeedItem()

    companion object {
        @Composable
        fun getName(feedItem: FeedItem): String {
            return when (feedItem) {
                is FeedGenerator -> feedItem.generator.displayName
                is List -> feedItem.list.name
                is Following -> stringResource(R.string.following)
                is ForYou -> "For You"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>(start = true)
@Composable
fun Feed(nav: DestinationsNavigator) {
    val context = LocalContext.current
    val viewModel = LocalFeedModel.current
    val api = LocalMindskyApi.current
    val scrollToTopHandler = LocalScrollToTop.current
    val coroutineScope = rememberCoroutineScope()

    var lastFetchTime by remember { mutableLongStateOf(0L) }

    val followingFeedData = viewModel.followingFeed.value
    val forYouFeedData = viewModel.forYouFeed.value
    val customFeedsData = viewModel.customFeeds

    val isFetchingFromViewModel =
        viewModel.isFetchingFeed.value || viewModel.isFetchingCustomFeeds.values.any { it }
    val error = viewModel.error.value

    val prefs = LocalPreferences.current
    val feeds = viewModel.userFeeds.value

    val logoAreaHeight = 40.dp
    val density = LocalDensity.current
    val statusBarHeightPx = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().let {
        with(density) { it.toPx() }
    }
    val logoAreaHeightPx = with(density) { logoAreaHeight.toPx() }

    val tabRowHeightPx = remember { mutableFloatStateOf(0f) }
    val headerOffsetHeightPx = remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember(tabRowHeightPx.floatValue, logoAreaHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val oldOffset = headerOffsetHeightPx.floatValue
                val newOffset = oldOffset + delta

                // Translate up to the height of the tab row and logo area
                headerOffsetHeightPx.floatValue = newOffset.coerceIn(-(tabRowHeightPx.floatValue + logoAreaHeightPx), 0f)
                val consumed = headerOffsetHeightPx.floatValue - oldOffset

                return Offset(x = 0f, y = consumed)
            }
        }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { feeds.size }
    )

    // Map to store LazyListState for each feed tab
    val listStates = remember {
        mutableMapOf<String, LazyListState>()
    }

    val currentFeedId = remember(pagerState.currentPage, feeds) {
        if (feeds.isNotEmpty() && pagerState.currentPage in feeds.indices) {
            feeds[pagerState.currentPage].first
        } else null
    }

    val currentListState = listStates[currentFeedId] ?: rememberLazyListState()

    DisposableEffect(currentListState) {
        val handler: () -> Unit = {
            coroutineScope.launch {
                currentListState.animateScrollToItem(0)
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

    LaunchedEffect(feeds) {
        if (feeds.isNotEmpty()) {
            val defaultFeed = prefs.get(AppPrefs.DefaultFeed)
            if (defaultFeed in feeds.indices) {
                pagerState.scrollToPage(defaultFeed)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (feeds.isNotEmpty()) {
            prefs.set(AppPrefs.DefaultFeed, pagerState.currentPage)
        }
    }

    val workManager = WorkManager.getInstance(context)
    val workInfos by workManager.getWorkInfosLiveData(
        WorkQuery.fromTags(
            FeedWorker::class.java.simpleName,
            SignalProcessingWorker::class.java.simpleName
        )
    ).observeAsState()
    val feedWorkerInfo = remember(workInfos) {
        workInfos?.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }

    LaunchedEffect(viewModel) {
        viewModel.fetchUserFeeds()
    }

    LaunchedEffect(pagerState.currentPage, feeds.size, viewModel) {
        if (feeds.isEmpty()) return@LaunchedEffect

        val safeSelectedDestination = pagerState.currentPage.coerceIn(feeds.indices)

        if (feeds.isNotEmpty()) {
            val feedId = feeds[safeSelectedDestination].first
            when (feeds[safeSelectedDestination].second) {
                is FeedItem.Following, FeedItem.ForYou -> viewModel.fetchFeed()
                is FeedItem.FeedGenerator, is FeedItem.List -> viewModel.fetchCustomFeed(
                    AtUri(feedId),
                    isRefresh = true
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val files = listOf(
            File(context.filesDir, ModelDownloadWorker.MODEL_FILENAME),
            File(context.filesDir, ModelDownloadWorker.TOKENIZER_FILENAME)
        )
        if (!files.all { it.exists() && it.length() > 0 }) {
            nav.replaceCurrent(DownloadModelDestination)
        }
    }

    Scaffold(
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            // LAYER 0: FIXED TOP GRADIENT
            // This is the gradient that the logo and tabs "disappear into"
            TopBarBackground(
                modifier = Modifier
                    .graphicsLayer {
                        // Fade in the gradient as the header collapses
                        // It becomes fully opaque once the scroll starts (10dp threshold)
                        alpha = (-headerOffsetHeightPx.floatValue / with(density) { 10.dp.toPx() }).coerceIn(0f, 1f)
                    }
                    .zIndex(10f)
            )

            // LAYER 1: FIXED PAGER (CONTENT)
            // It fills the whole screen, but we use a top spacer inside LazyColumn
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f),
                beyondViewportPageCount = 1
            ) { page ->
                val destination = feeds[page]
                val feedId = destination.first
                val pageListState = rememberSaveable(feedId, saver = LazyListState.Saver) {
                    LazyListState()
                }

                LaunchedEffect(pageListState) {
                    listStates[feedId] = pageListState
                }

                val threadedFollowingFeed = if (destination.second is FeedItem.Following && followingFeedData != null) {
                    remember(followingFeedData) {
                        FeedTuner.groupThreads(followingFeedData)
                    }
                } else null

                val customFeedData = customFeedsData[AtUri(feedId)]
                val threadedCustomFeed = if ((destination.second is FeedItem.FeedGenerator || destination.second is FeedItem.List) && customFeedData != null) {
                    remember(customFeedData) {
                        FeedTuner.groupThreads(customFeedData)
                    }
                } else null

                LazyColumn(
                    state = pageListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = paddingValues,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (error != null) Arrangement.Center else Arrangement.Top
                ) {
                    item {
                        // The top area is Logo (fixed height) + Tab Row
                        // We reserve this space at the top of the list.
                        val topSpacerHeight = with(LocalDensity.current) {
                            (logoAreaHeightPx + tabRowHeightPx.floatValue).toDp()
                        }
                        Spacer(modifier = Modifier.height(topSpacerHeight))
                    }
                    if (error != null) {
                        item {
                            Icon(
                                Icons.Rounded.Error,
                                contentDescription = "Error",
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .size(40.dp)
                            )
                        }
                        item {
                            Text(
                                text = "Failed to fetch your feed",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        item {
                            Box(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh,
                                        MaterialTheme.shapes.medium
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(0.2f),
                                        MaterialTheme.shapes.medium
                                    )
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = error,
                                )
                            }
                        }
                    } else {
                        if (viewModel.isFetchingUserFeeds.value && feeds.isEmpty()) {
                            Loading()
                        }

                        when (destination.second) {
                            is FeedItem.Following -> {
                                if (threadedFollowingFeed != null) {
                                    items(
                                        items = threadedFollowingFeed,
                                        key = { it.first().post.uri.atUri }
                                    ) { thread ->
                                        ThreadedPostComponent(thread, nav) { clickedPost ->
                                            nav.navigate(FullsizePostDestination(clickedPost.post.uri.atUri))
                                        }
                                    }
                                    if (followingFeedData != null && followingFeedData.isNotEmpty() && viewModel.hasMoreFollowing.value) {
                                        item {
                                            LoadMore(followingFeedData.size) {
                                                viewModel.loadMoreFollowingFeed()
                                            }
                                        }
                                    }
                                } else {
                                    Loading()
                                }
                            }

                            is FeedItem.ForYou -> {
                                if (!forYouFeedData.isNullOrEmpty()) {
                                    items(
                                        items = forYouFeedData,
                                        key = { it.post.uri.atUri }
                                    ) {
                                        PostComponent(it.post, nav, discoveryContext = {
                                            PostInsightsContext(
                                                it.score
                                            )
                                        }) {
                                            nav.navigate(FullsizePostDestination(it.post.uri.atUri))
                                        }
                                    }
                                    if (
                                        //forYouFeedData.isNotEmpty() && viewModel.hasMoreForYou.value
                                        false
                                        ) {
                                        item {
                                            LoadMore(forYouFeedData.size) {
                                                viewModel.loadMoreForYouFeeds()
                                            }
                                        }
                                    } else {
//                                        item {
//                                            Box(
//                                                modifier = Modifier.height(200.dp).background(
//                                                    Brush.verticalGradient(
//                                                        listOf(
//                                                            Color.Transparent,
//                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
//                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
//                                                        )
//                                                    )
//                                                )
//                                            ) {
//                                                Column(
//                                                    modifier = Modifier.fillMaxSize(),
//                                                    verticalArrangement = Arrangement.Center,
//                                                    horizontalAlignment = Alignment.CenterHorizontally
//                                                ) {
//                                                    Text("That's all for now!", style = MaterialTheme.typography.titleMedium)
//                                                    Text("Your for you feed will refresh tomorrow", style = MaterialTheme.typography.bodySmall)
//                                                }
//                                            }
//                                        }
                                    }
                                } else if (forYouFeedData != null && forYouFeedData.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "No posts available",
                                                style = MaterialTheme.typography.bodyLarge,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    }
                                    item {
                                        Button(
                                            onClick = {
                                                WorkManager.getInstance(context)
                                                    .enqueueImmediateWorkers()
                                            }
                                        ) {
                                            Text("Generate For You Feed")
                                        }
                                    }
                                } else {
                                    Loading()
                                }
                            }

                            is FeedItem.FeedGenerator, is FeedItem.List -> {
                                if (!threadedCustomFeed.isNullOrEmpty()) {
                                    items(
                                        items = threadedCustomFeed,
                                        key = { it.first().post.uri.atUri }
                                    ) { thread ->
                                        ThreadedPostComponent(thread, nav) { clickedPost ->
                                            nav.navigate(FullsizePostDestination(clickedPost.post.uri.atUri))
                                        }
                                    }
                                    if (customFeedData != null && customFeedData.isNotEmpty() && viewModel.hasMoreCustomFeeds[AtUri(
                                            feedId
                                        )] == true
                                    ) {
                                        item {
                                            LoadMore(feedId + customFeedData.size) {
                                                viewModel.loadMoreCustomFeed(AtUri(feedId))
                                            }
                                        }
                                    } else {
                                        item {
                                            Spacer(modifier = Modifier.height(150.dp))
                                        }
                                    }
                                } else {
                                    Loading()
                                }
                            }
                        }
                    }
                }
            }

            // LAYER 2: TRANSLATING HEADER (Tabs & Progress)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        // Move this entire column based on scroll delta
                        translationY = headerOffsetHeightPx.floatValue
                    }
                    .zIndex(2f)
            ) {
                // Spacer for fixed logo area
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(logoAreaHeight)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    val slideDistancePx = with(LocalDensity.current) { 36.dp.toPx().toInt() }

                    AnimatedVisibility(
                        visible = feedWorkerInfo != null &&
                                feedWorkerInfo.state == WorkInfo.State.RUNNING,
                        enter = fadeIn() + slideInVertically(
                            initialOffsetY = { -slideDistancePx }
                        ),
                        exit = fadeOut() + slideOutVertically(
                            targetOffsetY = { slideDistancePx }
                        )
                    ) {
                        FeedWorkerProgressDisplay(feedWorkerInfo = feedWorkerInfo)
                    }

                    if (feeds.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned {
                                    // Capture height of everything that should scroll away
                                    tabRowHeightPx.floatValue = it.size.height.toFloat()
                                }
                        ) {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = pagerState.currentPage,
                                edgePadding = 0.dp,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                feeds.forEachIndexed { index, destination ->
                                    Tab(
                                        selected = pagerState.currentPage == index,
                                        onClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        },
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (destination.second is FeedItem.ForYou) {
                                                    Icon(
                                                        Icons.Rounded.AutoAwesome,
                                                        contentDescription = "Sparkles",
                                                        tint = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                Text(
                                                    FeedItem.getName(destination.second),
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    } else if (viewModel.isFetchingUserFeeds.value) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            Loading()
                        }
                    }
                }
            }

            // LAYER 3: FIXED TOP BAR (Mindsky Logo & Buttons)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = headerOffsetHeightPx.floatValue
                    }
                    .zIndex(4f)
            ) {
                Box(
                    modifier = Modifier
                        .statusBarsPadding()
                        .height(logoAreaHeight)
                ) {
                    TopBarInteractiveElements(
                        listState = currentListState,
                        onIconClick = {
                            val currentTime = System.currentTimeMillis()
                            if (isFetchingFromViewModel) {
                                Log.d("Feed", "Already fetching feed, click ignored.")
                            } else if (currentTime - lastFetchTime < 5000) {
                                Log.d("Feed", "Fetched too recently, click ignored. Cooldown active.")
                            } else {
                                lastFetchTime = currentTime
                                if (feeds.isNotEmpty() && pagerState.currentPage < feeds.size) {
                                    val currentFeed = feeds[pagerState.currentPage]
                                    val feedId = currentFeed.first
                                    when (currentFeed.second) {
                                        is FeedItem.Following -> viewModel.fetchFeed(
                                            isRefresh = true
                                        )

                                        is FeedItem.ForYou -> viewModel.refreshForYouFeed()

                                        is FeedItem.FeedGenerator, is FeedItem.List -> viewModel.fetchCustomFeed(
                                            AtUri(feedId),
                                            isRefresh = true
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )

                    TopBarButtons(headerOffsetHeightPx.floatValue, nav)
                }
            }

            // LAYER 4: THE FADE (Slides with Tabs)
//            Box(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .graphicsLayer {
//                        // Position below the stationary logo area, then move with scroll
//                        translationY = logoAreaHeightPx +
//                                statusBarHeightPx +
//                                headerOffsetHeightPx.floatValue +
//                                tabRowHeightPx.floatValue
//                    }
//                    .height(20.dp)
//                    .background(
//                        Brush.verticalGradient(
//                            colors = listOf(
//                                MaterialTheme.colorScheme.surface,
//                                Color.Transparent
//                            )
//                        )
//                    )
//                    .zIndex(3f)
//            )
        }
    }
}

@Composable
fun TopBarButtons(
    headerOffset: Float,
    navigator: DestinationsNavigator
) {
    val hapticFeedback = LocalHapticFeedback.current

    // Fade out buttons as the header collapses
    val alpha by animateFloatAsState(
        targetValue = if (headerOffset > -30f) 1f else 0f,
        label = "iconRowAlpha"
    )

    var isAccountSwitcherOpen by remember { mutableStateOf(false) }
    var isAccountSheetOpen by remember { mutableStateOf(false) }

    AccountSwitcherSheet(
        isOpen = isAccountSwitcherOpen,
        onDismiss = { isAccountSwitcherOpen = false },
        onAddAccount = {
            isAccountSwitcherOpen = false
            navigator.navigate(PdsListDestination(isOnboarding = false))
        }
    )

    AccountSheet(
        isOpen = isAccountSheetOpen,
        onAccountSwitch = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
            //isAccountSheetOpen = false
            isAccountSwitcherOpen = true
        },
        onDismiss = { isAccountSheetOpen = false }
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer { this.alpha = alpha }
            .zIndex(5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
            ) {
                ProfileAvatar(
                    modifier = Modifier
                        .size(30.dp),
                    onAvatarClick = {
                        isAccountSheetOpen = true
                    },
                    onAvatarLongClick = {
                        isAccountSwitcherOpen = true
                    }
                )
            }
            IconButton(onClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                navigator.navigate(SettingsDestination)
            }) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(25.dp)
                )
            }
        }
    }
}

@Composable
fun ProfileAvatar(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: () -> Unit = {},
) {
    val hapticFeedback = LocalHapticFeedback.current
    val profileModel = LocalProfileModel.current
    val profileUiState by profileModel.uiState.collectAsState()
    when (profileUiState) {
        ProfileUiState.Loading -> {
            Box(
                modifier = modifier
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = enabled,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onAvatarClick()
                        },
                        onLongClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAvatarLongClick()
                        }
                    )
            )
        }

        is ProfileUiState.Error -> {
            Box(
                modifier = modifier
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = enabled,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onAvatarClick()
                        },
                        onLongClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAvatarLongClick()
                        }
                    )
            )
        }

        is ProfileUiState.Success -> {
            val avatarUrl =
                (profileUiState as ProfileUiState.Success).profile.avatar?.uri
            Avatar(
                avatarUrl = avatarUrl,
                modifier = modifier
                    .clip(CircleShape)
                    .combinedClickable(
                        enabled = enabled,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onAvatarClick()
                        },
                        onLongClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAvatarLongClick()
                        }
                    ),
                contentDescription = "Avatar"
            )
        }
    }
}

fun LazyListScope.Loading() {
    items(8, key = { it }) {
        LoadingPost()
    }
}

package io.github.turtlepaw.mindsky

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.rememberNavController
import app.bsky.notification.GetUnreadCountQueryParams
import app.bsky.notification.GetUnreadCountResponse
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.FeedDestination
import com.ramcosta.composedestinations.generated.destinations.FeedGeneratorDestination
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.generated.destinations.InitialOnboardingDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import sh.christian.ozone.api.AtUri
import fyi.kittens.shared.ui.LocalSavedPreferences
import fyi.kittens.shared.ui.rememberSavedPreferences
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.mindsky.cache.ProfileCache
import io.github.turtlepaw.mindsky.components.BottomBar
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalNavController
import io.github.turtlepaw.mindsky.di.LocalNotificationCountProvider
import io.github.turtlepaw.mindsky.di.LocalPostState
import io.github.turtlepaw.mindsky.di.LocalProfileModel
import io.github.turtlepaw.mindsky.di.LocalProfileRepository
import io.github.turtlepaw.mindsky.di.LocalScrollToTop
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.di.PostsState
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.preferences.PreferenceProvider
import io.github.turtlepaw.mindsky.repositories.ProfileRepository
import io.github.turtlepaw.mindsky.routes.FeedViewModelFactory
import io.github.turtlepaw.mindsky.ui.theme.MindskyTheme
import io.github.turtlepaw.mindsky.viewmodels.AuthViewModel
import io.github.turtlepaw.mindsky.viewmodels.FeedViewModel
import io.github.turtlepaw.mindsky.viewmodels.ProfileViewModel
import io.github.turtlepaw.mindsky.viewmodels.ProfileViewModelFactory
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.getPreferenceFlow

object DefaultSlideFadeTransitions : NavHostAnimatedDestinationStyle() {
    private val fastOutExtraSlowIn = CubicBezierEasing(0.05f, 0f, 0.133333f, 1f)
    private val slideDistancePx = 96

    private val enterDuration = 230
    private val exitDuration = 155

    override val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        {
            fadeIn(
                animationSpec = tween(durationMillis = enterDuration, easing = LinearEasing)
            ) + slideInHorizontally(
                initialOffsetX = { slideDistancePx },
                animationSpec = tween(durationMillis = enterDuration, easing = fastOutExtraSlowIn)
            )
        }

    override val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        {
            fadeOut(
                animationSpec = tween(durationMillis = exitDuration, easing = LinearEasing)
            ) + slideOutHorizontally(
                targetOffsetX = { -slideDistancePx },
                animationSpec = tween(durationMillis = exitDuration, easing = fastOutExtraSlowIn)
            )
        }

    override val popEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        {
            fadeIn(
                animationSpec = tween(durationMillis = enterDuration, easing = LinearEasing)
            ) + slideInHorizontally(
                initialOffsetX = { -slideDistancePx },
                animationSpec = tween(durationMillis = enterDuration, easing = fastOutExtraSlowIn)
            )
        }

    override val popExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        {
//        scaleOut(
//            targetScale = 0.95f,
//        ) + slideOutHorizontally(
//            targetOffsetX = { slideDistancePx },
//            animationSpec = tween(durationMillis = exitDuration, easing = fastOutExtraSlowIn)
//        )
            fadeOut(
                animationSpec = tween(durationMillis = exitDuration, easing = LinearEasing)
            ) + slideOutHorizontally(
                targetOffsetX = { slideDistancePx },
                animationSpec = tween(durationMillis = exitDuration, easing = fastOutExtraSlowIn)
            )
        }
}

sealed class DeepLinkDestination {
    data class Post(val uri: String) : DeepLinkDestination()
    data class Profile(val identity: String) : DeepLinkDestination()
    data class Feed(val uri: String) : DeepLinkDestination()
}

class MainActivity : ComponentActivity() {
    private lateinit var authViewModel: AuthViewModel
    private var pendingDeepLink by mutableStateOf<DeepLinkDestination?>(null)

    @OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        handleOAuthCallback(intent)
        pendingDeepLink = extractDeepLinkFromIntent(intent)

        val mindskyApplication = applicationContext as MindskyApplication
        val sessionManager = mindskyApplication.sessionManager

        // Add this: track whether the app is fully initialized
        var isAppReady by mutableStateOf(false)

        // Install splash screen with your condition
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !isAppReady
        }

        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val iconView = splashScreenViewProvider.iconView
            val animators = mutableListOf<Animator>(
                ObjectAnimator.ofFloat(
                    splashScreenViewProvider.view,
                    View.ALPHA,
                    1f,
                    0f
                )
            )

            if (iconView != null) {
                animators.add(
                    ObjectAnimator.ofFloat(
                        iconView,
                        View.SCALE_X,
                        1f,
                        1.3f
                    )
                )
                animators.add(
                    ObjectAnimator.ofFloat(
                        iconView,
                        View.SCALE_Y,
                        1f,
                        1.3f
                    )
                )
            }

            AnimatorSet().apply {
                interpolator = AccelerateInterpolator()
                duration = 300L
                playTogether(animators)
                doOnEnd { splashScreenViewProvider.remove() }
                start()
            }
        }

        setContent {
            val rememberedSessionManager = remember { sessionManager }
            val scrollToTopHandler = remember { mutableStateOf<(() -> Unit)?>(null) }
            val navController = rememberNavController()
            val navigator = navController.rememberDestinationsNavigator()
            val postState = remember { PostsState() }
            val notifications = rememberPermissionState(
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                if (!notifications.status.isGranted) {
                    notifications.launchPermissionRequest()
                }
            }

            val blueskyApi by mindskyApplication.blueskyApiFlow.collectAsState()
            val session by sessionManager.sessionFlow.collectAsState()
            val isInitialized by sessionManager.isInitialized.collectAsState()

            val startRoute = remember(isInitialized) {
                if (session != null) {
                    FeedDestination
                } else {
                    InitialOnboardingDestination()
                }
            }

            LaunchedEffect(pendingDeepLink, isInitialized, session) {
                val target = pendingDeepLink
                Log.d("MainActivity", "Pending deep link: $target, isInitialized: $isInitialized, session: $session")
                if (target != null && isInitialized && session != null) {
                    when (target) {
                        is DeepLinkDestination.Post -> {
                            navigator.navigate(FullsizePostDestination(postUri = target.uri))
                        }
                        is DeepLinkDestination.Profile -> {
                            navigator.navigate(ProfileDestination(did = target.identity))
                        }
                        is DeepLinkDestination.Feed -> {
                            navigator.navigate(FeedGeneratorDestination(feed = AtUri(target.uri)))
                        }
                    }
                    pendingDeepLink = null
                }
            }

            val savedPreferences = rememberSavedPreferences(blueskyApi, session?.did)
            val notificationCount = remember(blueskyApi, session?.did) {
                SingleCache(
                    fetcher = {
                        if (blueskyApi == null) throw IllegalStateException("BlueskyApi not initialized")
                        if (session?.did == null) throw IllegalStateException("Session not initialized")
                        blueskyApi!!.getUnreadCount(
                            GetUnreadCountQueryParams()
                        ).requireResponse()
                    },
                    serializer = GetUnreadCountResponse.serializer(),
                    identifier = "notification_count_${session?.did ?: "anonymous"}"
                )
            }.load(enabled = blueskyApi != null && session?.did != null)

            // Update isAppReady when all conditions are met
            LaunchedEffect(isInitialized, session, blueskyApi, savedPreferences.isLoading, savedPreferences.value, savedPreferences.error) {
                isAppReady = isInitialized && (
                    session == null || (
                        blueskyApi != null &&
                        (!savedPreferences.isLoading || savedPreferences.value != null || savedPreferences.error != null)
                    )
                )
            }

            PreferenceProvider {
                CompositionLocalProvider(
                    LocalSessionManager provides rememberedSessionManager,
                    LocalLabelManager provides mindskyApplication.labelManager,
                    LocalNavController provides navController,
                    LocalScrollToTop provides scrollToTopHandler,
                    LocalPostState provides postState,
                    LocalNotificationCountProvider provides notificationCount,
                ) {
                    MindskyTheme {
                        ProvidePreferenceLocals(
                            flow = LocalPreferences.current.getSharedPreferences()
                                .getPreferenceFlow()
                        ) {
                            val isLoading = !isInitialized || (
                                session != null &&
                                (blueskyApi == null || (savedPreferences.value == null && savedPreferences.error == null))
                            )

                            if (isLoading) {
                                // Show a loading screen while API is initializing
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.background),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (session != null && savedPreferences.error != null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "Failed to load preferences: ${savedPreferences.error?.message}",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = {
                                                coroutineScope.launch {
                                                    savedPreferences.refresh?.invoke()
                                                }
                                            }) {
                                                Text("Retry")
                                            }
                                        }
                                    } else {
                                        Text(
                                            "Mindsky is warming up...",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            } else {
                                val currentApi = blueskyApi ?: remember(session) {
                                    mindskyApplication.configureAuthenticatedApi(session)
                                }
                                val profileCache =
                                    remember(currentApi) { ProfileCache(this@MainActivity) }
                                val profileRepository = remember(currentApi) {
                                    ProfileRepository(
                                        currentApi,
                                        profileCache
                                    )
                                }
                                val viewModel: ProfileViewModel = viewModel(
                                    key = "ProfileViewModel_${currentApi.hashCode()}",
                                    factory = ProfileViewModelFactory(
                                        profileRepository,
                                        rememberedSessionManager
                                    )
                                )
                                val feedViewModel: FeedViewModel =
                                    viewModel(
                                        key = currentApi.hashCode().toString(),
                                        factory = FeedViewModelFactory(
                                            currentApi,
                                            this@MainActivity
                                        )
                                    )

                                CompositionLocalProvider(
                                    LocalMindskyApi provides currentApi,
                                    LocalProfileModel provides viewModel,
                                    LocalProfileRepository provides profileRepository,
                                    LocalFeedModel provides feedViewModel,
                                    LocalSavedPreferences provides (savedPreferences.value ?: emptyList())
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.background)
                                    ) {
                                        DestinationsNavHost(
                                            navGraph = NavGraphs.root,
                                            start = startRoute,
                                            defaultTransitions = DefaultSlideFadeTransitions,
                                            navController = navController
                                        )

                                        BottomBar(
                                            navController = navController,
                                            modifier = Modifier.align(Alignment.BottomCenter)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallback(intent)
        val deepLink = extractDeepLinkFromIntent(intent)
        Log.d("MainActivity", "Got ${deepLink}")
        if (deepLink != null) {
            pendingDeepLink = deepLink
        }
    }

    private fun extractDeepLinkFromIntent(intent: Intent?): DeepLinkDestination? {
        if (intent == null) return null
        Log.d("MainActivity", "Extracting deep link from intent: $intent (${intent.data})")

        val extraPostUri = intent.getStringExtra("post_uri") ?: intent.getStringExtra("uri")
        if (!extraPostUri.isNullOrBlank() && extraPostUri.contains("app.bsky.feed.post")) {
            return DeepLinkDestination.Post(extraPostUri)
        }

        val extraFeedUri = intent.getStringExtra("feed_uri")
        if (!extraFeedUri.isNullOrBlank() && extraFeedUri.contains("app.bsky.feed.generator")) {
            return DeepLinkDestination.Feed(extraFeedUri)
        }

        val extraDidOrHandle = intent.getStringExtra("did") ?: intent.getStringExtra("handle") ?: intent.getStringExtra("profile")
        if (!extraDidOrHandle.isNullOrBlank()) {
            return DeepLinkDestination.Profile(extraDidOrHandle)
        }

        val data = intent.data ?: return null

        if (data.scheme == "at") {
            val uriStr = data.toString()
            return when {
                uriStr.contains("/app.bsky.feed.post/") -> DeepLinkDestination.Post(uriStr)
                uriStr.contains("/app.bsky.feed.generator/") -> DeepLinkDestination.Feed(uriStr)
                else -> {
                    val identity = uriStr.removePrefix("at://").trimEnd('/')
                    if (identity.isNotBlank()) DeepLinkDestination.Profile(identity) else null
                }
            }
        }

        if (data.scheme == "aster" || data.scheme == "mindsky") {
            val host = data.host
            val uriParam = data.getQueryParameter("uri") ?: data.getQueryParameter("postUri") ?: data.getQueryParameter("feedUri")
            val didParam = data.getQueryParameter("did") ?: data.getQueryParameter("handle") ?: data.getQueryParameter("user")

            when {
                !uriParam.isNullOrBlank() -> {
                    return when {
                        uriParam.contains("app.bsky.feed.generator") -> DeepLinkDestination.Feed(uriParam)
                        else -> DeepLinkDestination.Post(uriParam)
                    }
                }
                !didParam.isNullOrBlank() -> {
                    return DeepLinkDestination.Profile(didParam)
                }
                host == "profile" || host == "user" -> {
                    val pathSegment = data.pathSegments.firstOrNull()
                    if (!pathSegment.isNullOrBlank()) {
                        return DeepLinkDestination.Profile(pathSegment)
                    }
                }
            }
        }

        if ((data.scheme == "https" || data.scheme == "http")) {
            val pathSegments = data.pathSegments
            if (pathSegments.size >= 2 && pathSegments[0] == "profile") {
                val handleOrDid = pathSegments[1]
                if (pathSegments.size >= 4) {
                    val subType = pathSegments[2]
                    val rkey = pathSegments[3]
                    when (subType) {
                        "post" -> return DeepLinkDestination.Post("at://$handleOrDid/app.bsky.feed.post/$rkey")
                        "feed" -> return DeepLinkDestination.Feed("at://$handleOrDid/app.bsky.feed.generator/$rkey")
                    }
                } else if (pathSegments.size == 2) {
                    return DeepLinkDestination.Profile(handleOrDid)
                }
            }
        }

        return null
    }

    private fun handleOAuthCallback(intent: Intent) {
        val code = intent.getStringExtra("oauth_code")
        val state = intent.getStringExtra("oauth_state")
        val error = intent.getStringExtra("oauth_error")

        if (error != null) {
            Log.e("MainActivity", "OAuth error: $error")
            authViewModel.handleOAuthError(error)
        } else if (code != null) {
            Log.d("MainActivity", "Handling OAuth callback with code: $code")
            authViewModel.handleOAuthCallback(code, state)
        }
    }
}

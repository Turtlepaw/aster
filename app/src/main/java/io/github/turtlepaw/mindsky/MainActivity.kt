package io.github.turtlepaw.mindsky

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.WorkManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.FeedDestination
import com.ramcosta.composedestinations.generated.destinations.InitialOnboardingDestination
import com.ramcosta.composedestinations.generated.destinations.MyProfileDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.spec.DestinationSpec
import com.ramcosta.composedestinations.spec.Direction
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import com.ramcosta.composedestinations.utils.route
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.cache.ProfileCache
import io.github.turtlepaw.mindsky.components.BottomBar
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalNavController
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
import io.github.turtlepaw.mindsky.workers.WorkerManager.enqueuePeriodicFeedWorkers
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

class MainActivity : ComponentActivity() {
    private lateinit var authViewModel: AuthViewModel

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        authViewModel = ViewModelProvider(this)[AuthViewModel::class.java]
        handleOAuthCallback(intent)

        // Get MindskyApplication instance
        val mindskyApplication = applicationContext as MindskyApplication

        val sessionManager =
            SessionManager(applicationContext) // SessionManager can still be local if preferred

        val startRoute = if (sessionManager.getSession() != null) {
            FeedDestination
        } else {
            InitialOnboardingDestination
        }

        setContent {
            val rememberedSessionManager = remember { sessionManager }
            val scrollToTopHandler = remember { mutableStateOf<(() -> Unit)?>(null) }
            val notifications = rememberPermissionState(
                android.Manifest.permission.POST_NOTIFICATIONS
            )

            LaunchedEffect(Unit) {
                if (!notifications.status.isGranted) {
                    notifications.launchPermissionRequest()
                }

                //WorkManager.getInstance(this@MainActivity).enqueuePeriodicFeedWorkers()
            }

            // Get API from MindskyApplication
            val blueskyApi = mindskyApplication.blueskyApi

            val profileCache = remember { ProfileCache(this@MainActivity) }
            val profileRepository = remember { ProfileRepository(blueskyApi, profileCache) }
            val viewModel: ProfileViewModel = viewModel(
                factory = ProfileViewModelFactory(
                    profileRepository,
                    rememberedSessionManager
                )
            )
            val feedViewModel: FeedViewModel =
                viewModel(factory = FeedViewModelFactory(blueskyApi, this))
            val navController = rememberNavController()
            val postState = remember { PostsState() }

            PreferenceProvider {
                CompositionLocalProvider(
                    LocalMindskyApi provides blueskyApi, // Use API from Application
                    LocalSessionManager provides rememberedSessionManager,
                    LocalProfileModel provides viewModel,
                    LocalProfileRepository provides profileRepository,
                    LocalFeedModel provides feedViewModel,
                    LocalLabelManager provides mindskyApplication.labelManager,
                    LocalNavController provides navController,
                    LocalScrollToTop provides scrollToTopHandler,
                    LocalPostState provides postState
                ) {
                    MindskyTheme {
                        ProvidePreferenceLocals(
                            flow = LocalPreferences.current.getSharedPreferences()
                                .getPreferenceFlow()
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallback(intent)
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

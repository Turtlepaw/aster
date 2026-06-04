package io.github.turtlepaw.mindsky.components

import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.bsky.notification.GetUnreadCountQueryParams
import app.bsky.notification.GetUnreadCountResponse
import com.ramcosta.composedestinations.generated.destinations.ComposerDestination
import com.ramcosta.composedestinations.generated.destinations.FeedDestination
import com.ramcosta.composedestinations.generated.destinations.InitialOnboardingDestination
import com.ramcosta.composedestinations.generated.destinations.MyProfileDestination
import com.ramcosta.composedestinations.generated.destinations.PdsListDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.generated.destinations.SearchDestination
import com.ramcosta.composedestinations.generated.destinations.NotificationsDestination
import com.ramcosta.composedestinations.spec.Direction
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalScrollToTop
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import kotlin.collections.contains

@Preview
@Composable
fun BottomBarPreview() {
    val navController = NavController(LocalContext.current)
    BottomBar(navController)
}

@Composable
fun BottomBar(navController: NavController, modifier: Modifier = Modifier) {
    val scrollToTopHandler = LocalScrollToTop.current
    val sessionManager = LocalSessionManager.current
    val session by sessionManager.sessionFlow.collectAsState()
    val haptics = LocalHapticFeedback.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val identity = backStackEntry?.arguments?.getString("identity")

    var isAccountSwitcherOpen by remember { mutableStateOf(false) }

    val isOnProfileRoute = currentRoute == ProfileDestination.route
    val isMyProfile = identity == session?.did
    //val isCompact = isOnProfileRoute && !isMyProfile
    val isCompact = false
    val api = LocalMindskyApi.current

    AccountSwitcherSheet(
        isOpen = isAccountSwitcherOpen,
        onDismiss = { isAccountSwitcherOpen = false },
        onAddAccount = {
            isAccountSwitcherOpen = false
            navController.navigate(PdsListDestination(isOnboarding = false))
        }
    )

    val notificationCount = SingleCache.rememberLoad(
        fetcher = {
            api.getUnreadCount(
                GetUnreadCountQueryParams()
            ).requireResponse()
        },
        serializer = GetUnreadCountResponse.serializer(),
        identifier = "notification_count"
    )

    val visibleRoutes = setOf(
        FeedDestination.route,
        ProfileDestination.route,
        SearchDestination.route,
        NotificationsDestination.route
    )

    LaunchedEffect(session) {
        if (session == null) {
            navController.navigate(InitialOnboardingDestination.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    val barWidth by animateFloatAsState(
        targetValue = if (isCompact) 0.4f else 0.9f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bar_width"
    )

    AnimatedVisibility(
        visible = currentRoute in visibleRoutes,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150)),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(bottom = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(barWidth)
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Nav section (animated between compact scroll-to-top and full nav)
                AnimatedContent(
                    targetState = isCompact,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                scaleIn(
                                    initialScale = 0.92f,
                                    animationSpec = tween(220, delayMillis = 90)
                                ))
                            .togetherWith(fadeOut(animationSpec = tween(90)))
                    },
                    label = "nav_content",
                    modifier = Modifier
                        .then(
                            if (isCompact) Modifier.wrapContentWidth() else Modifier.weight(1f)
                        )
                        .fillMaxHeight()
                ) { compact ->
                    if (compact) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.surfaceContainerHighest,
                                    CircleShape
                                )
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    scrollToTopHandler.value?.invoke()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = "Scroll to top",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        NavItems(
                            session = session,
                            currentRoute = currentRoute,
                            identity = session?.did ?: "",
                            navController = navController,
                            scrollToTopHandler = scrollToTopHandler,
                            unreadCount = notificationCount.value?.count ?: 0,
                            onLongPressProfile = { isAccountSwitcherOpen = true }
                        )
                    }
                }

                // Post button
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape
                        )
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            navController.navigate(ComposerDestination().route)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PostAdd,
                        stringResource(R.string.post),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private data class NavRouteMetadata(
    val label: String,
    val icon: ImageVector
)

private data class NavItem(
    val direction: Direction,
    val selectionRoutes: Set<String>,
    val metadata: NavRouteMetadata
)

@Composable
fun NavItems(
    session: UserSession?,
    currentRoute: String?,
    identity: String,
    navController: NavController,
    scrollToTopHandler: MutableState<(() -> Unit)?>,
    unreadCount: Long = 0,
    onLongPressProfile: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val items = listOf(
        NavItem(
            FeedDestination(),
            setOf(FeedDestination.route),
            NavRouteMetadata("Feed", Icons.Rounded.Home)
        ),
        NavItem(
            SearchDestination,
            setOf(SearchDestination.route),
            NavRouteMetadata("Search", Icons.Rounded.Search)
        ),
        NavItem(
            NotificationsDestination,
            setOf(NotificationsDestination.route),
            NavRouteMetadata("Notifications", Icons.Rounded.Notifications)
        ),
        NavItem(
            ProfileDestination(identity),
            setOf(ProfileDestination.route),
            NavRouteMetadata("Profile", Icons.Rounded.Person)
        ),
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(100.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                1.dp,
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(100.dp)
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = currentRoute in item.selectionRoutes
            val isProfile = item.direction.route == ProfileDestination.route
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(100.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(100.dp)
                    )
                    .combinedClickable(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (isSelected) {
                                scrollToTopHandler.value?.invoke()
                            } else {
                                navController.navigate(item.direction.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        onLongClick = if (isProfile) {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLongPressProfile()
                            }
                        } else null
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                val isNotifications = item.direction.route == NotificationsDestination.route
                if (isNotifications) {
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge {
                                    Text(
                                        if (unreadCount > 99) "99+" else unreadCount.toString()
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = item.metadata.icon,
                            contentDescription = item.metadata.label,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Icon(
                        imageVector = item.metadata.icon,
                        contentDescription = item.metadata.label,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

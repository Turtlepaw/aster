package io.github.turtlepaw.mindsky.routes.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bsky.actor.GetProfilesQueryParams
import app.bsky.actor.ProfileViewDetailed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.PdsListDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.CollectionCache
import io.github.turtlepaw.fetch_and_cache.CollectionCacheLoadResult
import fyi.kittens.shared.auth.UserSession
import fyi.kittens.shared.ui.Avatar
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.viewmodels.rememberAuthViewModel
import fyi.kittens.shared.R
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import sh.christian.ozone.api.Did

@Composable
fun rememberProfiles(sessions: List<UserSession>): CollectionCacheLoadResult<ProfileViewDetailed> {
    val api = LocalMindskyApi.current
    return remember(sessions) {
        CollectionCache(
            fetcher = {
                val result = api.getProfiles(
                    GetProfilesQueryParams(
                        actors = sessions.map { Did(it.did) }.toList()
                    )
                ).requireResponse()

                result.profiles.associateBy { it.did.did }
            },
            serializer = ProfileViewDetailed.serializer(),
            identifier = "profile_manager_profiles"
        )
    }.load()
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun AccountManagement(navigator: DestinationsNavigator) {
    val sessionManager = LocalSessionManager.current
    val authViewModel = rememberAuthViewModel()
    val currentSession by sessionManager.sessionFlow.collectAsState()
    val sessions = remember(currentSession) { sessionManager.getSessions() }
    val context = LocalContext.current
    val profiles = rememberProfiles(sessions)

    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(navigator, title = R.string.manage_accounts)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sessions) { session ->
                AccountItem(
                    session = session,
                    profile = profiles.value.getOrDefault(session.did, null),
                    isCurrent = session.did == currentSession?.did,
                    logoutEnabled = sessions.size > 1,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    onSwitch = {
                        authViewModel.switchAccount(session.did)
                        Toast.makeText(
                            context,
                            "Switched to ${session.handle}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onLogout = { authViewModel.signOut(session.did) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        navigator.navigate(PdsListDestination(isOnboarding = false))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text("Add another account")
                    }
                }
            }
        }
    }
}

@Composable
fun AccountItem(
    session: UserSession,
    profile: ProfileViewDetailed?,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    logoutEnabled: Boolean = true,
    onSwitch: () -> Unit,
    onLogout: () -> Unit
) {
    Surface(
        onClick = { if (!isCurrent) onSwitch() },
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
//        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
//        border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Avatar(
                    avatarUrl = profile?.avatar?.uri,
                    contentDescription = session.handle,
                    modifier = Modifier.size(48.dp),
                    clip = MaterialTheme.shapes.large
                )
                Column {
                    Text(
                        text = profile?.displayName ?: session.handle,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "@${profile?.handle?.handle ?: session.handle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Current account",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                if (logoutEnabled) IconButton(
                    onClick = onLogout,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = "Logout"
                    )
                }
            }
        }
    }
}

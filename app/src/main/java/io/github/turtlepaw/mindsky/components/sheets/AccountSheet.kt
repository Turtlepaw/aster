package io.github.turtlepaw.mindsky.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.generated.destinations.BookmarksDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import fyi.kittens.shared.R
import fyi.kittens.shared.ui.Avatar
import fyi.kittens.shared.ui.rememberLoadingColor
import io.github.turtlepaw.mindsky.di.LocalNavController
import io.github.turtlepaw.mindsky.di.LocalProfileModel
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.routes.profile.LoadingListItem
import io.github.turtlepaw.mindsky.routes.settings.rememberProfiles
import io.github.turtlepaw.mindsky.viewmodels.ProfileUiState
import io.github.turtlepaw.mindsky.viewmodels.rememberAuthViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountSheet(
    isOpen: Boolean,
    onAccountSwitch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val navController = LocalNavController.current
    val navigator = navController.toDestinationsNavigator()

    val sessionManager = LocalSessionManager.current
    val authViewModel = rememberAuthViewModel()
    val currentSession by sessionManager.sessionFlow.collectAsState()
    val sessions = sessionManager.getSessions()
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    val profiles = rememberProfiles(sessions)

    val profileModel = LocalProfileModel.current
    val profileUiState by profileModel.uiState.collectAsState()

    val items = 2
    val itemColor = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    )

    if (isOpen) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    item {
                        if(profileUiState is ProfileUiState.Error){
                            Text(
                                "Error loading profile",
                                modifier = Modifier.padding(24.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = items),
                            colors = itemColor,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                            onClick = {
                                onAccountSwitch()
                            },
                            content = {
                                val content = if(profileUiState is ProfileUiState.Loading)
                                    "Loading"
                                else (profileUiState as ProfileUiState.Success).profile.handle.handle
                                Text(
                                    text = content,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = "Tap to switch",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.9f)
                                )
                            },
                            leadingContent = {
                                if(profileUiState is ProfileUiState.Loading){
                                    val loadingColor = rememberLoadingColor()
                                    Box(
                                        modifier = Modifier.size(42.dp).background(
                                            loadingColor,
                                            CircleShape
                                        ),
                                    )
                                } else {
                                    val profile = (profileUiState as ProfileUiState.Success).profile
                                    Avatar(
                                        modifier = Modifier.size(42.dp),
                                        profile.avatar?.uri,
                                        "${profile.handle} avatar",
                                    )
                                }
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = "Chevron Right",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }

                    item {
                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(index = 1, count = items),
                            colors = itemColor,
                            onClick = {
                                onDismiss()
                                navigator.navigate(BookmarksDestination)
                            },
                            content = { Text(stringResource(R.string.bookmarks)) },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Bookmark,
                                    "Bookmark"
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAccessItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
//                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
//                    .border(
//                        1.dp,
//                        MaterialTheme.colorScheme.surfaceContainerHighest,
//                        CircleShape
//                    )
                ,
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

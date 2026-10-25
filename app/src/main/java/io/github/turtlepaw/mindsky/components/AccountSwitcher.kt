package io.github.turtlepaw.mindsky.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fyi.kittens.shared.auth.UserSession
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.routes.settings.AccountItem
import io.github.turtlepaw.mindsky.routes.settings.rememberProfiles
import io.github.turtlepaw.mindsky.viewmodels.rememberAuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSwitcherSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit
) {
    val sessionManager = LocalSessionManager.current
    val authViewModel = rememberAuthViewModel()
    val currentSession by sessionManager.sessionFlow.collectAsState()
    val sessions = sessionManager.getSessions()
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    val profiles = rememberProfiles(sessions)

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
                Text(
                    text = "Switch Account",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(sessions) { session ->
                        AccountItem(
                            session = session,
                            profile = profiles.value.getOrDefault(session.did, null),
                            modifier = Modifier.padding(horizontal = 24.dp),
                            isCurrent = session.did == currentSession?.did,
                            logoutEnabled = false,
                            onLogout = {},
                            onSwitch = {
                                authViewModel.switchAccount(session.did)
                                Toast.makeText(
                                    context,
                                    "Switched to ${session.handle}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                        )
                    }

                    item {
                        Surface(
                            onClick = onAddAccount,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Add, 
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    "Add another account", 
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

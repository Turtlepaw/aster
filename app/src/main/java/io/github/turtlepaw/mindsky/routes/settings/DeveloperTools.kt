package io.github.turtlepaw.mindsky.routes.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.InitialOnboardingDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import me.zhanghai.compose.preference.preference

import androidx.core.content.edit
import io.github.turtlepaw.mindsky.components.MaterialList
import io.github.turtlepaw.mindsky.components.MaterialListItem
import io.github.turtlepaw.mindsky.db.ObjectBox
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.logic.ranking.PostScore
import io.objectbox.kotlin.boxFor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun DeveloperTools(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val sessionManager = LocalSessionManager.current
    val session by sessionManager.sessionFlow.collectAsState()
    val api = LocalMindskyApi.current

    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(navigator, title = null)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Session Information",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "DID: ${session?.did ?: "Not logged in"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Handle: ${session?.handle ?: "N/A"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "PDS URL: ${session?.pdsUrl ?: "N/A"}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(
                                "Session Token",
                                session?.accessToken ?: "No token"
                            )
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Token copied", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Copy Session Token")
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(
                    text = "Actions",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                val labelDefinitions = LocalLabelManager.current
                MaterialList(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    items = listOf(
                        MaterialListItem(
                            onClick = {
                                labelDefinitions.forceRefreshDefinitions(api)
                            },
                            title = "Refresh labeler definitions"
                        ),
                        MaterialListItem(
                            onClick = {
                                navigator.navigate(InitialOnboardingDestination(force = true))
                            },
                            title = "Reset Onboarding"
                        ),
                        MaterialListItem(
                            onClick = {
                                ObjectBox.store.boxFor(PostScore::class.java).removeAll()
                            },
                            title = "Clear for you feed"
                        ),
                        MaterialListItem(
                            onClick = {
                                context.getSharedPreferences(
                                    "composer_drafts",
                                    Context.MODE_PRIVATE
                                ).edit {
                                    clear()
                                }
                                Toast.makeText(context, "Drafts cleared", Toast.LENGTH_SHORT).show()
                            },
                            title = "Clear Local Drafts"
                        )
                    )
                )
            }
        }
    }
}

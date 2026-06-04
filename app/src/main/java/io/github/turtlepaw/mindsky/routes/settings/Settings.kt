package io.github.turtlepaw.mindsky.routes.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AccountManagementDestination
import com.ramcosta.composedestinations.generated.destinations.AdvancedDestination
import com.ramcosta.composedestinations.generated.destinations.AppearanceDestination
import com.ramcosta.composedestinations.generated.destinations.DeveloperToolsDestination
import com.ramcosta.composedestinations.generated.destinations.InterestClustersDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import io.github.turtlepaw.mindsky.utils.StringComposable
import io.github.turtlepaw.mindsky.workers.WorkerManager.enqueueImmediateWorkers
import me.zhanghai.compose.preference.preference

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Settings(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val developerMode by rememberPreference(AppPrefs.DeveloperMode)
    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(navigator, R.string.settings_title)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
//            preference(
//                key = "interest_sources",
//                title = {
//                    R.string.interest_sources.StringComposable()
//                },
//                summary = {
//                    R.string.interest_sources_description.StringComposable()
//                },
//                onClick = {
//                    navigator.navigate(InterestSourcesDestination)
//                },
//                widgetContainer = {
//                    Icon(
//                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
//                        contentDescription = "Arrow forward",
//                        tint = MaterialTheme.colorScheme.onSurface,
//                        modifier = Modifier.padding(start = 8.dp, end = 16.dp)
//                    )
//                }
//            )
            preference(
                key = "interest_clusters",
                title = {
                    R.string.interest_clusters.StringComposable()
                },
                summary = {
                    R.string.interest_clusters_description.StringComposable()
                },
                onClick = {
                    navigator.navigate(InterestClustersDestination)
                },
                icon = {
                    Icon(
                        Icons.Rounded.Category,
                        contentDescription = "Category",
                    )
                },
                widgetContainer = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Arrow forward",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp, end = 16.dp)
                    )
                }
            )
            preference(
                key = "accounts",
                title = {
                    Text("Accounts")
                },
                summary = {
                    Text("Manage your logged in accounts")
                },
                onClick = {
                    navigator.navigate(AccountManagementDestination)
                },
                icon = {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = "Accounts",
                    )
                },
                widgetContainer = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Arrow forward",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp, end = 16.dp)
                    )
                }
            )
            preference(
                key = "appearance",
                title = {
                    R.string.appearance.StringComposable()
                },
                summary = {
                    R.string.appearance_description.StringComposable()
                },
                onClick = {
                    navigator.navigate(AppearanceDestination)
                },
                icon = {
                    Icon(
                        Icons.Rounded.Palette,
                        contentDescription = "Palette",
                    )
                },
                widgetContainer = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Arrow forward",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp, end = 16.dp)
                    )
                }
            )
            preference(
                key = "advanced",
                title = {
                    R.string.advanced.StringComposable()
                },
                summary = {
                    R.string.advanced_description.StringComposable()
                },
                onClick = {
                    navigator.navigate(AdvancedDestination)
                },
                icon = {
                    Icon(
                        Icons.Rounded.Build,
                        contentDescription = "Build",
                    )
                },
                widgetContainer = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Arrow forward",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp, end = 16.dp)
                    )
                }
            )
            if (developerMode) {
                preference(
                    key = "developer_tools",
                    title = {
                        Text("Developer Tools")
                    },
                    summary = {
                        Text("Access internal tools and debug information")
                    },
                    onClick = {
                        navigator.navigate(DeveloperToolsDestination)
                    },
                    icon = {
                        Icon(
                            Icons.Rounded.Build,
                            contentDescription = "Developer Tools",
                        )
                    },
                    widgetContainer = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Arrow forward",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp, end = 16.dp)
                        )
                    }
                )
            }
            item {
                Button(
                    onClick = {
                        WorkManager.getInstance(context).enqueueImmediateWorkers()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Launch embedding")
                }
            }
        }
    }
}

object TopAppBarCommon {
    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    fun withBack(navigator: DestinationsNavigator, title: Int?, titleStyle: TextStyle? = null) {
        TopAppBar(
            title = {
                if (title != null) Text(
                    stringResource(title),
                    style = titleStyle ?: LocalTextStyle.current
                )
            },
            navigationIcon = {
                BackButton(navigator)
            },
            //modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    fun withBack(navigator: DestinationsNavigator) {
        TopAppBar(
            title = {},
            navigationIcon = {
                BackButton(navigator)
            },
            //modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }

    @Composable
    fun BackButton(navigator: DestinationsNavigator) {
        IconButton(onClick = { navigator.navigateUp() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back"
            )
        }
    }
}
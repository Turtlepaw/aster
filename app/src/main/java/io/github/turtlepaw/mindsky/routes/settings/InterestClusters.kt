package io.github.turtlepaw.mindsky.routes.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import fyi.kittens.shared.R
import io.github.turtlepaw.mindsky.db.ObjectBox
import kotlinx.coroutines.launch
import io.github.turtlepaw.mindsky.logic.ranking.InterestCluster
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun InterestClusters(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val generativeModel = remember { Generation.getClient() }
    var isLoading by remember { mutableStateOf(false) }
    var clusters by remember { mutableStateOf(emptyList<InterestCluster>()) }
    var nanoStatus by remember { mutableStateOf<Int?>(null) }
    var nanoDownloadBytes by remember { mutableStateOf<Long?>(null) }
    var nanoDownloadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        nanoStatus = generativeModel.checkStatus()
        isLoading = true
        val box = ObjectBox.store.boxFor(InterestCluster::class.java)
        clusters = box.all.sortedByDescending { it.strength }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(navigator, R.string.interest_clusters)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            horizontalAlignment = if (isLoading) Alignment.CenterHorizontally else Alignment.Start
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Get smarter cluster labels with Gemini Nano",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Download Gemini Nano to generate more useful names for your interests.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        when (nanoStatus) {
                            FeatureStatus.AVAILABLE -> {
                                Text(
                                    text = "Gemini Nano is ready.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }

                            FeatureStatus.DOWNLOADING -> {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                )
                                Text(
                                    text = nanoDownloadBytes?.let {
                                        "Downloading... ${formatBytes(it)} downloaded"
                                    } ?: "Downloading...",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            FeatureStatus.DOWNLOADABLE -> {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            nanoDownloadError = null
                                            nanoStatus = FeatureStatus.DOWNLOADING
                                            try {
                                                generativeModel.download().collect { status ->
                                                    when (status) {
                                                        is DownloadStatus.DownloadProgress -> {
                                                            nanoDownloadBytes =
                                                                status.totalBytesDownloaded
                                                        }

                                                        DownloadStatus.DownloadCompleted -> {
                                                            nanoStatus = FeatureStatus.AVAILABLE
                                                        }

                                                        is DownloadStatus.DownloadFailed -> {
                                                            nanoStatus = FeatureStatus.DOWNLOADABLE
                                                            nanoDownloadError =
                                                                status.e.message ?: "Download failed"
                                                        }

                                                        else -> Unit
                                                    }
                                                }
                                            } catch (error: Exception) {
                                                nanoStatus = FeatureStatus.DOWNLOADABLE
                                                nanoDownloadError =
                                                    error.message ?: "Download failed"
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(top = 12.dp)
                                ) {
                                    Text("Download Gemini Nano")
                                }
                            }

                            FeatureStatus.UNAVAILABLE -> {
                                Text(
                                    text = "Gemini Nano is unavailable on this device.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }

                        nanoDownloadError?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    LoadingIndicator()
                }
            } else if (clusters.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.interest_clusters_empty),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                itemsIndexed(clusters) { index, it ->
                    SegmentedListItem(
                        onClick = {},
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = clusters.size),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        supportingContent = {
                            Text(
                                "Strength: ${it.strength}\nCenter Embedding: ${it.centerEmbedding}"
                            )
                        }
                    ){
                        Text(
                            text = it.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 16.dp).padding(horizontal = 12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.onSurface
                        )

                        Text("These interests are derived from your liked posts to create your for you feed.")
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String =
    if (bytes < 1024 * 1024) {
        "${bytes / 1024} KB"
    } else {
        "${bytes / (1024 * 1024)} MB"
    }
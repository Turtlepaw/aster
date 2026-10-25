package io.github.turtlepaw.mindsky.components.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.bsky.feed.GetFeedGeneratorResponse
import fyi.kittens.shared.ui.Avatar
import io.github.turtlepaw.mindsky.logic.ranking.PostScore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PostInsightsContext(
    score: PostScore,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var isOpen by remember { mutableStateOf(false) }

    if (isOpen) {
        ModalBottomSheet(
            onDismissRequest = { isOpen = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Post Insights",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val items = listOf(
                        "Matched Interest" to (score.matchedInterest ?: "Unknown"),
                        "Diversity" to score.diversity,
                        "Recency" to score.recency,
                        "Semantic Similarity" to score.semanticSimilarity,
                        "Engagement" to score.engagement,
                        "Final score" to score.finalScore
                    )

                    items.forEachIndexed { index, it ->
                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = items.size),
                            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                            onClick = {

                            },
                            supportingContent = {
                                Text(it.second.toString())
                            },
                            content = { Text(it.first) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                isOpen = false
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    Column {
        Row(
            modifier = modifier.padding(
                horizontal = 5.dp,
            ).padding(start = 10.dp, bottom = 10.dp).clickable {
                isOpen = true
            }.clip(MaterialTheme.shapes.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
//            Avatar(
//                contentDescription = feed.view.displayName,
//                avatarUrl = feed.view.avatar?.uri,
//                clip = MaterialTheme.shapes.small,
//                modifier = Modifier.size(20.dp)
//            )
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = "Matched interest",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Because you like ${score.matchedInterest}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

package io.github.turtlepaw.mindsky.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemElevation
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.Modifier.Companion

data class MaterialListItem(
    val onClick: () -> Unit,
    val modifier: Modifier = Modifier,
    val enabled: Boolean = true,
    val onLongClick: (() -> Unit)? = null,
    val onLongClickLabel: String? = null,
    val contentPadding: PaddingValues = ListItemDefaults.ContentPadding,
    val interactionSource: MutableInteractionSource? = null,
    val title: String,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MaterialList(
    items: List<MaterialListItem>,
    modifier: Modifier = Modifier,
    elevation: ListItemElevation = ListItemDefaults.elevation(),
    colors: ListItemColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        List(items.size) { index ->
            val item = items[index]
            ListItem(
                onClick = item.onClick,
                modifier = item.modifier,
                enabled = item.enabled,
                shapes = ListItemDefaults.segmentedShapes(
                    index = index,
                    count = items.size,
                ),
    //                leadingContent = item.leadingContent,
    //                trailingContent = item.trailingContent,
    //                overlineContent = item.overlineContent,
    //                supportingContent = item.supportingContent,
    //                verticalAlignment = item.verticalAlignment,
                onLongClick = item.onLongClick,
                onLongClickLabel = item.onLongClickLabel,
                colors = colors,
                elevation = elevation,
                contentPadding = item.contentPadding,
                interactionSource = item.interactionSource,
                content = {
                    Text(item.title)
                }
            )
        }
    }
}

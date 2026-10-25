package fyi.kittens.shared.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ExpandableContent(
    modifier: Modifier = Modifier,
    collapsedHeight: Dp = 100.dp,
    enableShowLess: Boolean = true,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isCollapsible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val collapsedHeightPx = with(density) { collapsedHeight.roundToPx() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
            ) {
                SubcomposeLayout(
                    modifier = Modifier.fillMaxWidth()
                ) { constraints ->
                    val contentMeasurables = subcompose("content", content)
                    val contentPlaceables = contentMeasurables.map {
                        it.measure(constraints.copy(maxHeight = Constraints.Infinity))
                    }
                    val contentHeight = contentPlaceables.maxOfOrNull { it.height } ?: 0
                    val contentWidth = contentPlaceables.maxOfOrNull { it.width } ?: constraints.minWidth

                    val shouldCollapse = contentHeight > collapsedHeightPx
                    if (isCollapsible != shouldCollapse) {
                        isCollapsible = shouldCollapse
                    }

                    val targetHeight = if (!expanded && shouldCollapse) {
                        collapsedHeightPx
                    } else {
                        contentHeight
                    }

                    layout(contentWidth, targetHeight) {
                        contentPlaceables.forEach {
                            it.place(0, 0)
                        }
                    }
                }
            }

            if (!expanded && isCollapsible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    color.copy(alpha = 0.6f),
                                    color
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { expanded = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Show more",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (expanded && isCollapsible && enableShowLess) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable { expanded = false }
                    .padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Collapse",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Show less",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

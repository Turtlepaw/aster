import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fyi.kittens.shared.Formatters
import fyi.kittens.shared.ui.LocalPostDensity
import fyi.kittens.shared.ui.PostDensity

object PostAction {
    @Composable
    fun Section(
        actions: @Composable (modifier: Modifier) -> Unit,
        discoveryContext: @Composable (modifier: Modifier) -> Unit
    ) {
        actions(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        discoveryContext(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .padding(horizontal = 4.dp)
        )
    }

    @Composable
    fun Button(
        label: Long?,
        icon: ImageVector,
        contentDescription: String,
        isHighlighted: Boolean = false,
        highlightColor: Color? = null,
        onClick: () -> Unit,
    ) {
        Button(
            label = label?.toFloat(),
            icon = icon,
            contentDescription = contentDescription,
            isHighlighted = isHighlighted,
            highlightColor = highlightColor,
            onClick = onClick
        )
    }

    @Composable
    fun Button(
        label: Float? = null,
        icon: ImageVector,
        contentDescription: String,
        isHighlighted: Boolean = false,
        highlightColor: Color? = null,
        onClick: () -> Unit,
    ) {
        val density = LocalPostDensity.current
        val activeHighlightColor = highlightColor ?: MaterialTheme.colorScheme.primary

        val scale = remember { Animatable(1f) }

        LaunchedEffect(isHighlighted) {
            if (isHighlighted) {
                scale.animateTo(
                    targetValue = 1.25f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                scale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            } else {
                scale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }

        Box(
            modifier = Modifier
                .sizeIn()
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val colour by animateColorAsState(
                    targetValue = if (isHighlighted) activeHighlightColor else MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.7f
                    ),
                    animationSpec = tween(120)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .size(if (density == PostDensity.Expanded) 20.dp else 18.dp)
                        .graphicsLayer {
                            scaleX = scale.value
                            scaleY = scale.value
                        },
                    tint = colour
                )
                if (label != null && label > 0) {
                    Text(
                        text = Formatters.formatNumberForLocale(label.toInt()),
                        style = (if (density == PostDensity.Expanded) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelLarge).copy(
                            fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                            color = colour,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    fun Label(
        label: String,
        value: Long?,
        onClick: () -> Unit,
    ) {
        Label(
            label = label,
            value = value?.toFloat(),
            onClick
        )
    }
    @Composable
    fun Label(
        label: String,
        value: Float?,
        onClick: () -> Unit,
    ) {
        if (value != null && value > 0) {
            Box(
                modifier = Modifier
                    .sizeIn()
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append(Formatters.formatNumberForLocale(value.toInt()))
                        }
                        append(" ")
                        withStyle(
                            style = SpanStyle(
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            append(label)
                        }
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
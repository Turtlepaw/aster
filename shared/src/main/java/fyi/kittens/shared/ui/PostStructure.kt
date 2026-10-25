package fyi.kittens.shared.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant as JavaInstant
import kotlin.time.Instant as KotlinInstant
import kotlin.time.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime

enum class PostDensity {
    Compact,
    Regular,
    Expanded
}

class PostStructureStyle(
    val headlineSpacing: Dp,
    val separatorColor: Color?,
    val avatarSize: Dp,
    val textStyle: TextStyle?,
    val showExpandedSeparator: Boolean,
    val expandedSeparatorPadding: Dp,
    val separatorPadding: Dp
)

val DefaultPostStructureStyle = PostStructureStyle(
    headlineSpacing = 12.dp,
    separatorColor = null,
    avatarSize = 42.dp,
    textStyle = null,
    showExpandedSeparator = true,
    expandedSeparatorPadding = 12.dp,
    separatorPadding = 0.dp
)

@OptIn(ExperimentalTime::class)
@Composable
fun PostStructure(
    headline: @Composable () -> Unit,
    avatar: @Composable (modifier: Modifier) -> Unit,
    metadata: @Composable () -> Unit,
    actions: @Composable (modifier: Modifier) -> Unit,
    impressions: @Composable (modifier: Modifier) -> Unit,
    modifier: Modifier = Modifier,
    showImpressions: Boolean = true,
    discoveryContext: @Composable (modifier: Modifier) -> Unit = {},
    density: PostDensity = PostDensity.Regular,
    onClick: (() -> Unit)? = null,
    createdAt: KotlinInstant,
    showSeparator: Boolean = true,
    showThreadIndicator: Boolean = false,
    isThreadChild: Boolean = false,
    style: PostStructureStyle = DefaultPostStructureStyle,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalPostDensity provides density
    ) {
        val isRegular = density == PostDensity.Regular
        val avatarSize = if (density == PostDensity.Compact) 20.dp else style.avatarSize
        val horizontalPadding = 16.dp
        val avatarTopPadding = 16.dp
        val lineColor = MaterialTheme.colorScheme.outlineVariant

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .then(modifier)
                .then(
                    if (onClick != null) {
                        Modifier
                            .clip(MaterialTheme.shapes.large)
                            .clickable { onClick() }
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding).padding(top = avatarTopPadding),
            ) {
                metadata()
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = spacedBy(if (density == PostDensity.Compact) 8.dp else style.headlineSpacing),
                    verticalAlignment = Alignment.Top,
                ) {
                    // Left side: Avatar
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        //Spacer(Modifier.height(avatarTopPadding))
                        avatar(Modifier.size(avatarSize))
                    }

                    // Right side: Name and Content (if regular)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        //Spacer(Modifier.height(avatarTopPadding))
                        headline()

                        if (isRegular) {
                            Spacer(Modifier.height(8.dp))
                            content()
                            Section(actions, discoveryContext)
                        }
                    }
                }
            }

            // Content for non-regular modes (starts below avatar, aligned with avatar)
            if (!isRegular) {
                Column(
                    modifier = Modifier.padding(horizontal = horizontalPadding)
                ) {
                    Spacer(Modifier.height(8.dp))
                    content()
                    if (density != PostDensity.Expanded) {
                        Section(actions, discoveryContext)
                    }
                }
            }

            if (density == PostDensity.Expanded) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        createdAt.toJavaInstant().toFormattedString(),
                        style = style.textStyle ?: MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            if (showSeparator && !showThreadIndicator) {
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (density == PostDensity.Expanded) style.expandedSeparatorPadding else style.separatorPadding
                        ),
                    thickness = 0.15.dp,
                    color = style.separatorColor ?: DividerDefaults.color
                )
            }

            if (density == PostDensity.Expanded && showImpressions) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    impressions(
                        Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    thickness = 0.15.dp,
                    color = style.separatorColor ?: DividerDefaults.color
                )
            }

            if (density == PostDensity.Expanded) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Section(actions, discoveryContext)
                }
                if (style.showExpandedSeparator) {
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 0.25.dp,
                        color = style.separatorColor ?: DividerDefaults.color
                    )
                }
            }
        }
    }
}

fun JavaInstant.toFormattedString(zoneId: ZoneId = ZoneId.systemDefault()): String {
    val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' h:mma")
    return this.atZone(zoneId)
        .toLocalDateTime()
        .format(formatter)
}


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

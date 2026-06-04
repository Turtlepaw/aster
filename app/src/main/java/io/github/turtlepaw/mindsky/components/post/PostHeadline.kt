package io.github.turtlepaw.mindsky.components.post

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.bsky.actor.ProfileViewBasic
import app.bsky.labeler.GetServicesResponseViewUnion
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.atproto.label.Label
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.auth.getPdsUrlFromDid
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.di.LocalNavController
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import io.github.turtlepaw.mindsky.utils.toRelativeTimeString
import kotlinx.coroutines.launch
import sh.christian.ozone.api.Did
import kotlin.time.Instant

private fun String.nullable(): String? {
    return if (isNullOrBlank()) {
        null
    } else {
        this
    }
}

@Composable
fun PostHeadline(
    timestamp: Instant,
    author: ProfileViewBasic,
    density: PostDensity,
    showLabels: Boolean = false,
    showHandle: Boolean = true
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                Column {
                    if (density == PostDensity.Expanded) {
                        Column {
                            AuthorNameWithBadges(author)
                            Text(
                                text = "@${author.handle.handle}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Row {
                            AuthorNameWithBadges(author)
                            if (author.verification?.verifications?.isNotEmpty() != true) {
                                Spacer(modifier = Modifier.size(5.dp))
                            }
                            if(showHandle) Text(
                                text = "@${author.handle.handle}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (showLabels && density != PostDensity.Expanded) Labelers(author.labels)
                }
            }

            Spacer(modifier = Modifier.width(5.dp))

            Text(
                text = timestamp.toRelativeTimeString(),
                style = MaterialTheme.typography.titleSmall.copy(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Labelers(labels: List<Label>?, large: Boolean = false) {
    if (labels == null) return

    val nav = LocalNavController.current
    val labelManager = LocalLabelManager.current
    val labelers by labelManager.labelersDefinitionFlow.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    var focusedLabel by remember { mutableStateOf<Label?>(null) }
    val scope = rememberCoroutineScope()

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (label in labels.filter { !hiddenLabels.contains(it.`val`) }) {
            val resolvedLabel = labelers[label.src.did]

            if (resolvedLabel != null) {
                LabelComponent(
                    label = resolvedLabel.getDisplayName(label.`val`),
                    avatar = resolvedLabel.getAvatar(),
                    onClick = {
                        focusedLabel = label
                    },
                    isLarge = large
                )
            }
        }
    }


    if (focusedLabel != null) {
        val resolvedLabel = labelers[focusedLabel!!.src.did]!!
        ModalBottomSheet(
            onDismissRequest = {
                focusedLabel = null
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    resolvedLabel.getDisplayName(
                        focusedLabel!!.`val`
                    ),
                    style = MaterialTheme.typography.titleLarge
                )

                val desciription = resolvedLabel.getDescription(
                    focusedLabel!!.`val`
                )

                if (desciription != null) {
                    Text(
                        desciription,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                val handle = resolvedLabel.getCreatorHandle() ?: "Unknown"

                val annotatedString = buildAnnotatedString {
                    append("Labeled by ")

                    addLink(
                        LinkAnnotation.Clickable(
                            tag = "handle",
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                hoveredStyle = SpanStyle(
                                    background = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ),
                                pressedStyle = SpanStyle(
                                    background = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            )
                        ) {
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                nav.navigate(
                                    ProfileDestination(focusedLabel!!.src.did).route
                                )
                            }
                        },
                        start = length,
                        end = length + "@$handle".length
                    )
                    append("@$handle")
                }

                Text(
                    text = annotatedString,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}

@Composable
private fun AuthorNameWithBadges(author: ProfileViewBasic) {
    FlowRow(verticalArrangement = Arrangement.Center) {
        Text(
            text = author.displayName?.nullable() ?: author.handle.handle,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (author.labels?.find { it.`val` == "bot" } != null) {
            Robot(Modifier.size(23.dp))
        }
        if (author.verification?.verifications?.isNotEmpty() == true) {
            Checkmark(Modifier.size(23.dp))
        }
    }
}

val hiddenLabels = listOf(
    "bot",
    "!no-unauthenticated"
)

@Composable
fun LabelComponent(
    label: String,
    avatar: String?,
    isLarge: Boolean = false,
    onClick: () -> Unit = { }
) {
    val showAvatar by rememberPreference(AppPrefs.ShowLabelerAvatars)
    Row(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .background(
                color = if (isLarge) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent,
                shape = if (isLarge) MaterialTheme.shapes.medium else CircleShape
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.1f),
                shape = if (isLarge) MaterialTheme.shapes.medium else CircleShape
            )
            .clip(if (isLarge) MaterialTheme.shapes.medium else CircleShape)
            .clickable(onClick = onClick)
            .run {
                if (isLarge) padding(
                    horizontal = 5.dp,
                    vertical = 2.dp
                ) else padding(horizontal = 5.dp)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            if (isLarge) 2.dp else 2.dp,
            Alignment.CenterHorizontally
        )
    ) {
        if (showAvatar && avatar != null) {
            Avatar(
                modifier = Modifier.size(
                    if (isLarge) 20.dp else 12.dp
                ),
                avatarUrl = avatar,
                contentDescription = "Labeler Avatar",
                clip = CircleShape,
            )
        }
        Text(
            text = label,
            style = (if (isLarge) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall).copy(
                color = MaterialTheme.colorScheme.onSurface.copy(0.9f)
            ),
            modifier = Modifier.padding(vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun GetServicesResponseViewUnion.getDisplayName(identifier: String): String {
    return when (this) {
        is GetServicesResponseViewUnion.LabelerView -> null
        is GetServicesResponseViewUnion.LabelerViewDetailed -> this.value.policies.labelValueDefinitions?.find { it.identifier == identifier }
            ?.locales?.find { it.lang.tag == "en" }?.name

        is GetServicesResponseViewUnion.Unknown -> null
    } ?: identifier
}

fun GetServicesResponseViewUnion.getDescription(identifier: String): String? {
    return when (this) {
        is GetServicesResponseViewUnion.LabelerView -> null
        is GetServicesResponseViewUnion.LabelerViewDetailed -> this.value.policies.labelValueDefinitions?.find { it.identifier == identifier }
            ?.locales?.find { it.lang.tag == "en" }?.description

        is GetServicesResponseViewUnion.Unknown -> null
    }
}

fun GetServicesResponseViewUnion.getCreatorName(): String? {
    return when (this) {
        is GetServicesResponseViewUnion.LabelerView -> this.value.creator.displayName
        is GetServicesResponseViewUnion.LabelerViewDetailed -> this.value.creator.displayName

        is GetServicesResponseViewUnion.Unknown -> null
    }
}

fun GetServicesResponseViewUnion.getCreatorHandle(): String? {
    return when (this) {
        is GetServicesResponseViewUnion.LabelerView -> this.value.creator.handle.handle
        is GetServicesResponseViewUnion.LabelerViewDetailed -> this.value.creator.handle.handle

        is GetServicesResponseViewUnion.Unknown -> null
    }
}

fun GetServicesResponseViewUnion.getAvatar(): String? {
    return when (this) {
        is GetServicesResponseViewUnion.LabelerView -> this.value.creator.avatar?.uri
        is GetServicesResponseViewUnion.LabelerViewDetailed -> this.value.creator.avatar?.uri

        is GetServicesResponseViewUnion.Unknown -> null
    }
}

@Composable
fun Checkmark(modifier: Modifier) {
    Icon(
        painterResource(R.drawable.check_circle),
        contentDescription = "Verified",
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(horizontal = 2.dp)
    )
}

@Composable
fun PdsIcon(did: Did, modifier: Modifier) {
    var pds by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val pdsUrl = getPdsUrlFromDid(did.did)
        pds = pdsUrl.replace("https://", "").replace("http://", "").replace("/", "")
    }

    PdsIcon(pds, modifier)
}

@Composable
fun PdsIcon(pds: String?, modifier: Modifier) {
    if (pds != null) Box(
        modifier = Modifier.padding(start = 3.dp)
    ) {
        SubcomposeAsyncImage(
            model = "https://twenty-icons.com/${pds}",
            contentDescription = "PDS Icon",
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .then(modifier)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            val state by painter.state.collectAsState()
            if (state is AsyncImagePainter.State.Success) {
                SubcomposeAsyncImageContent()
            } else if (state is AsyncImagePainter.State.Error) {
                Icon(
                    Icons.Rounded.Dns,
                    contentDescription = "PDS Icon",
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp)
                )
            }
        }
    }
}

@Composable
fun Robot(modifier: Modifier) {
    Icon(
        painterResource(R.drawable.ic_bot),
        contentDescription = "Bot",
        tint = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(start = 2.dp)
    )
}

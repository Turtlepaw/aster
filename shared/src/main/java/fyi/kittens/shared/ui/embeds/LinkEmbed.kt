package fyi.kittens.shared.ui.embeds

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.bsky.embed.ExternalView
import coil3.compose.AsyncImage
import fyi.kittens.shared.ui.Favicon
import kotlinx.coroutines.launch

class LinkEmbedStyle(
    val titleStyle: TextStyle?,
    val descriptionStyle: TextStyle?,
    val domainStyle: TextStyle?,
    val spacing: Dp
)

val DefaultLinkEmbedStyle = LinkEmbedStyle(
    titleStyle = null,
    descriptionStyle = null,
    domainStyle = null,
    spacing = 0.dp
)

@Composable
fun LinkEmbed(record: ExternalView, style: LinkEmbedStyle = DefaultLinkEmbedStyle) {
    val uriHandler = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()

    EmbedStructure(
        modifier = Modifier.fillMaxWidth(),
        onLongClick = {
            coroutineScope.launch {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                clipboardManager.setClipEntry(
                    ClipEntry(
                        ClipData.newPlainText(
                            "External embed link",
                            record.external.uri.uri
                        )
                    )
                )
            }
        },
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            uriHandler.openUri(record.external.uri.toString())
        },
    ) {
        if (record.external.thumb?.uri != null) AsyncImage(
            model = record.external.thumb?.uri,
            contentDescription = "External embed image",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
        )
        Column(
            modifier = Modifier
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(
                style.spacing
            )
        ) {
            Text(
                record.external.title,
                style = style.titleStyle ?: MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                record.external.description,
                style = style.descriptionStyle ?: MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            val domain = record.external.uri.uri.toUri().authority
            if (domain != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Favicon(
                        domain,
                        contentDescription = "Favicon for $domain",
                        placeholder = {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = "Globe",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .size(15.dp)                    )
                    Text(
                        domain,
                        style = style.domainStyle ?: MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

package fyi.kittens.shared.ui.embeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.bsky.feed.GeneratorView
import app.bsky.graph.ListView
import fyi.kittens.shared.ui.Avatar

@Composable
fun FeedGraphDisplay(it: GeneratorView) {
    FeedGraphDisplay(
        avatar = it.avatar?.uri,
        displayName = it.displayName,
        description = it.description,
        type = "Feed",
        creator = it.creator.handle.handle
    )
}

@Composable
fun FeedGraphDisplay(it: ListView) {
    FeedGraphDisplay(
        avatar = it.avatar?.uri,
        displayName = it.name,
        description = it.description,
        type = "Feed",
        creator = it.creator.handle.handle
    )
}

@Composable
private fun FeedGraphDisplay(
    avatar: String?,
    displayName: String,
    description: String? = null,
    type: String,
    creator: String
) {
    EmbedStructure(
        onClick = { /* TODO: Handle click action */ },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(
            8.dp,
            Alignment.CenterVertically
        )
        ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(
                8.dp,
                Alignment.CenterHorizontally
            )
        ) {
            Avatar(
                modifier = Modifier.size(45.dp),
                avatar,
                contentDescription = displayName,
                MaterialTheme.shapes.medium
            )
            Column {
                Text(
                    displayName, style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    "${type} by @${creator}", style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
        if (description != null) Text(
            description, style = MaterialTheme.typography.bodySmall
        )
    }
    }
}

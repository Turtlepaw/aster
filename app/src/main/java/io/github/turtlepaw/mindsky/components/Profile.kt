package io.github.turtlepaw.mindsky.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bsky.actor.ProfileView
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination.invoke
import io.github.turtlepaw.mindsky.components.post.Labelers

@Composable
fun Profile(actor: ProfileView, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                onClick()
            }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start)
    ) {
        Avatar(
            modifier = Modifier.size(30.dp),
            avatarUrl = actor.avatar?.uri,
            contentDescription = "${actor.displayName ?: actor.handle.handle}'s avatar",
        )
        Column {
            Text(text = actor.displayName ?: actor.handle.handle)
            Text(
                text = "@${actor.handle.handle}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
            )
        }
    }
}

@Composable
fun ProfileExpanded(actor: ProfileView, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                onClick()
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start)
            ) {
                Avatar(
                    modifier = Modifier.size(30.dp),
                    avatarUrl = actor.avatar?.uri,
                    contentDescription = "${actor.displayName ?: actor.handle.handle}'s avatar",
                )
                Column {
                    Text(text = actor.displayName ?: actor.handle.handle)
                    Text(
                        text = "@${actor.handle.handle}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                    )
                }
            }
            //Labelers(actor.labels)
            if (!actor.description.isNullOrEmpty()) {
                Text(
                    actor.description!!,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 70.dp),
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onSurface.copy(0.1f)
        )
    }
}
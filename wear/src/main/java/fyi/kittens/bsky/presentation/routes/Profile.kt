package fyi.kittens.bsky.presentation.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import app.bsky.actor.ProfileViewDetailed
import coil3.compose.AsyncImage
import fyi.kittens.bsky.presentation.components.post.Checkmark
import fyi.kittens.bsky.presentation.components.post.Robot
import fyi.kittens.shared.ui.Avatar

@Composable
fun ProfileScreen(profile: ProfileViewDetailed) {
    val scrollState = rememberScalingLazyListState()
    ScreenScaffold(
        modifier = Modifier.fillMaxSize(),
        scrollState = scrollState
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
//            AsyncImage(
//                model = profile.banner?.uri,
//                contentDescription = "Banner",
//                modifier = Modifier
//                    .fillMaxSize()
//                    .blur(2.dp)
//                    .graphicsLayer {
//                        alpha = 0.8f
//                    },
//                contentScale = ContentScale.Crop
//            )
//            Box(
//                modifier = Modifier
//                    .fillMaxSize()
//                    .background(
//                        Brush.radialGradient(
//                            listOf(
//                                Color.Transparent,
//                                Color.Black.copy(0.1f),
//                                Color.Black.copy(0.5f),
//                                Color.Black.copy(0.8f),
//                                Color.Black
//                            )
//                        )
//                    )
//            )
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                autoCentering = null,
                contentPadding = contentPadding,
                state = scrollState
            ) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                item {
                    Avatar(
                        avatarUrl = profile.avatar?.uri,
                        contentDescription = profile.displayName ?: profile.handle.handle,
                        modifier = Modifier.size(50.dp)
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(5.dp))
                }
                item {
                    FlowRow(
                        verticalArrangement = Arrangement.Center,
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.displayName ?: profile.handle.handle,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (profile.labels?.find { it.`val` == "bot" } != null) {
                            Robot(Modifier.size(16.dp))
                        }
                        if (profile.verification?.verifications?.isNotEmpty() == true) {
                            Checkmark(Modifier.size(16.dp))
                        }
                    }
                }
                item {
                    Text(
                        text = "@${profile.handle.handle}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        ),
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(5.dp))
                }
                item {
                    Text(
                        text = profile.description ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                item {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(15.dp)
                    )
                }
            }
        }
    }
}
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.FeedViewPost
import coil3.compose.AsyncImage
import fyi.kittens.bsky.presentation.components.post.Checkmark
import fyi.kittens.bsky.presentation.components.post.PostComponent
import fyi.kittens.bsky.presentation.components.post.Robot
import fyi.kittens.shared.ui.Avatar
import fyi.kittens.shared.ui.PostDensity

@Composable
fun PostScreen(post: FeedViewPost?, navController: NavController) {
    val scrollState = rememberScalingLazyListState()
    ScreenScaffold(
        modifier = Modifier.fillMaxSize(),
        scrollState = scrollState
    ) { contentPadding ->
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                autoCentering = null,
                contentPadding = contentPadding,
                state = scrollState
            ) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                item {
                    if (post == null) {
                        Text(text = "Post not found")
                    } else {
                        PostComponent(
                            postView = post.post,
                            navigator = navController,
                            density = PostDensity.Expanded,
                            modifier = Modifier.fillMaxSize(),
                            enabled = false,
                        ) { }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
    }
}
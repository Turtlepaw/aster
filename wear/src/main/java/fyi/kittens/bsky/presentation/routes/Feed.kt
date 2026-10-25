package fyi.kittens.bsky.presentation.routes

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import app.bsky.feed.FeedViewPost
import fyi.kittens.bsky.presentation.Screen
import fyi.kittens.bsky.presentation.WearFeedViewModel
import fyi.kittens.bsky.presentation.components.post.LoadingPost
import fyi.kittens.bsky.presentation.components.post.PostComponent
import fyi.kittens.shared.ui.LoadMore
import fyi.kittens.shared.ui.PostDensity
import sh.christian.ozone.api.AtUri

@Composable
fun FeedScreen(viewModel: WearFeedViewModel, navigator: NavController) {
    val feed by viewModel.feed.collectAsState()
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = listState,
    ) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec)
                        .clip(
                            MaterialTheme.shapes.medium
                        )
                        .clickable {
                            navigator.navigate(Screen.FeedPicker.route)
                        },
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(viewModel.currentFeed?.getName() ?: "Following")
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Chevron Right"
                        )
                    }
                }
            }
            if (viewModel.isRefreshing.value) {
                items(10) {
                    LoadingPost()
                }
            } else {
                items(feed.size) { index ->
                    PostItem(feed[index], navigator, transformationSpec)
                }

            }
            item {
                LoadMore(
                    viewModel
                ) {
                    viewModel.loadMoreFeed()
                }
            }
        }
    }
}

@Composable
fun TransformingLazyColumnItemScope.PostItem(
    feedViewPost: FeedViewPost,
    navigator: NavController,
    transformationSpec: TransformationSpec
) {
    PostComponent(
        postView = feedViewPost,
        navigator = navigator,
        density = PostDensity.Compact,
    ) {
        navigator.navigate(Screen.PostDetails.createRoute(feedViewPost.post.uri.atUri))
    }
}

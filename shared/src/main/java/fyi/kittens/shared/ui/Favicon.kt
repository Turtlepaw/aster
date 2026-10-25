package fyi.kittens.shared.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

@Composable
fun Favicon(url: String, contentDescription: String, placeholder: @Composable (contentDescription: String) -> Unit, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = "https://twenty-icons.com/${url}",
        contentDescription = contentDescription,
        modifier = modifier
    ) {
        val state by painter.state.collectAsState()
        if (state is AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent()
        } else if (state is AsyncImagePainter.State.Error) {
            placeholder(contentDescription)
        }
    }
}
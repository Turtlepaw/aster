package fyi.kittens.shared.ui.embeds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.bsky.embed.ImagesView
import app.bsky.embed.ImagesViewImage
import coil3.compose.AsyncImage
import fyi.kittens.shared.ui.rememberLoadingColor

@Composable
fun ImagesEmbed(imagesEmbed: ImagesView, showPadding: Boolean = true, onClick: (ImagesViewImage) -> Unit) {
    val images = imagesEmbed.images
    if (images.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = spacedBy(8.dp)
        ) {
            val rows = images.chunked(2)
            rows.forEach { rowImages ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = spacedBy(8.dp)
                ) {
                    rowImages.forEach { image ->
                        val imageModifier = if (images.size == 1) {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.5f)
                                .clip(MaterialTheme.shapes.medium)
                        } else {
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(MaterialTheme.shapes.medium)
                        }
                        ImageEmbed(
                            image.thumb.uri,
                            image.alt,
                            imageModifier
                        ) {
                            onClick(image)
                        }
                    }
                    if (rowImages.size == 1 && images.size > 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ImageEmbed(uri: String, alt: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AsyncImage(
        model = uri,
        contentDescription = alt ?: "Post image",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                onClick()
            }
    )
}

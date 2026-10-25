package fyi.kittens.shared.ui.embeds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.bsky.embed.Gallery
import app.bsky.embed.GalleryItemUnion
import app.bsky.embed.GalleryView
import app.bsky.embed.GalleryViewImage
import app.bsky.embed.GalleryViewItemUnion
import coil3.compose.AsyncImage

@Composable
fun GalleryEmbed(gallery: GalleryView, onImageClick: (GalleryViewImage) -> Unit) {
    val items = gallery.items
    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { items.count() },
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(top = 16.dp, bottom = 16.dp),
        preferredItemWidth = 186.dp,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) { i ->
        val item = items[i]
        if (item is GalleryViewItemUnion.ViewImage) {
            val image = item.value
            AsyncImage(
                model = image.fullsize.uri,
                contentDescription = image.alt
                    ?: "Post image", // Use alt text
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .height(205.dp)
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .clickable {
                        onImageClick(image)
                    }
            )
        }
    }
}

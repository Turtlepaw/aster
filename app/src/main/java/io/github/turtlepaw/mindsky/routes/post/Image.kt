package io.github.turtlepaw.mindsky.routes.post

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import coil3.toUri
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.ui.Avatar
import io.github.turtlepaw.mindsky.components.sheets.Sheet
import io.github.turtlepaw.mindsky.components.sheets.rememberSheetState
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import kotlinx.coroutines.launch
import kotlin.math.max
import androidx.core.content.edit
import app.bsky.embed.ImagesViewImage
import kotlinx.serialization.Serializable

private const val IMAGE_LIST_SEPARATOR = "\u001F"

@Composable
fun ImageIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    alpha: Float = 0.4f,
    icon: @Composable () -> Unit
) {
    IconButton(
        onClick = { onClick() },
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha),
                CircleShape
            )
            .size(size)
    ) {
        icon()
    }
}

suspend fun downloadImage(
    context: Context,
    imageUrl: String,
    fileName: String
): Result<Uri> {
    return try {
        val imageLoader = ImageLoader(context)

        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .build()

        val result = imageLoader.execute(request)

        val drawable = (result as? SuccessResult)?.image
            ?: throw Exception("Failed to load image")

        val bitmap = drawable.toBitmap()

        val resolver = context.contentResolver

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS
                )
            }

            resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )
        } else {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/MyApp"
                )
            }

            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
        } ?: throw Exception("Failed to create MediaStore entry")

        resolver.openOutputStream(uri)?.use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
        }

        Result.success(uri)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

@Serializable
data class ImageData(
    val url: String,
    val alt: String?,
)

object ImageDataStore {
    private val imagesByUrl = mutableMapOf<String, List<ImageData>>()

    fun put(imageUrl: String, images: List<ImageData>) {
        imagesByUrl[imageUrl] = images
    }

    fun get(imageUrl: String): List<ImageData>? = imagesByUrl[imageUrl]
}

fun ImagesViewImage.toImageData(): ImageData {
    return ImageData(
        url = fullsize.uri,
        alt = alt
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Image(
    navigator: DestinationsNavigator,
    imageUrl: String,
    alt: String?,
    postUri: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val feedModel = LocalFeedModel.current
    val post = if (postUri != null) feedModel.usePost(postUri) else null

    val imageUrls = ImageDataStore.get(imageUrl).takeUnless { it.isNullOrEmpty() } ?: run {
        listOf(ImageData(url = imageUrl, alt = alt))
    }
    val initialPage = remember(imageUrl, imageUrls) {
        imageUrls.indexOf(
            imageUrls.find { it.url == imageUrl }
        ).takeIf { it >= 0 } ?: 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { imageUrls.size }
    )
    val currentImageUrl = imageUrls[pagerState.currentPage]

    var isAltOpen by remember { mutableStateOf(true) }
    val altSheetState = rememberSheetState()

    Sheet<Any?>(altSheetState, modifier = Modifier.padding(14.dp)) { close, data ->
        item {
            Text(
                "Alt text",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item {
            val altText = imageUrls[pagerState.currentPage].alt
            Text(altText ?: "No alt text provided")
        }
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = Color.Black,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = isAltOpen,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        navigationIcon = {
                            ImageIconButton(
                                { navigator.navigateUp() },
                                modifier = Modifier.padding(start = 16.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        title = {},
                        actions = {
                            ImageIconButton(
                                {
                                    scope.launch {
                                        val result = downloadImage(
                                            context = context,
                                            imageUrl = currentImageUrl.url,
                                            fileName = currentImageUrl.url.toUri().lastPathSegment
                                                ?: "bsky.jpg"
                                        )

                                        result.onSuccess {
                                            Toast.makeText(
                                                context,
                                                "Image saved",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                        result.onFailure {
                                            Toast.makeText(
                                                context,
                                                "Download failed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            it.printStackTrace()
                                        }
                                    }
                                },
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = "Download",
                                    tint = Color.White
                                )
                            }
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Satisfy the compiler's requirement to use paddingValues
                // without affecting the fullscreen image layout
                Box(Modifier.padding(paddingValues))

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    var containerSize by remember { mutableStateOf(IntSize.Zero) }
                    var imageSize by remember { mutableStateOf(IntSize.Zero) }

                    fun clampOffset(offset: Offset): Offset {
                        val maxX = max(0f, (imageSize.width * scale - containerSize.width) / 2f)
                        val maxY = max(0f, (imageSize.height * scale - containerSize.height) / 2f)

                        val clampedX = offset.x.coerceIn(-maxX, maxX)
                        val clampedY = offset.y.coerceIn(-maxY, maxY)

                        return Offset(clampedX, clampedY)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { containerSize = it }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pointerCount = event.changes.count { it.pressed }
                                        if (pointerCount >= 2 || scale > 1f) {
                                            val zoom = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            scale = (scale * zoom).coerceIn(1f, 5f)
                                            offset = clampOffset(offset + pan)
                                            event.changes.forEach { it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = imageUrls[page].url,
                            contentDescription = "Fullscreen image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .onSizeChanged { imageSize = it }
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = null, indication = null
                                ) {
                                    isAltOpen = !isAltOpen
                                }
                        )

                        val altText = imageUrls[page].alt
                        if (altText != null) {
                            AnimatedVisibility(
                                isAltOpen,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter),
                                enter = slideInVertically(
                                    initialOffsetY = { it }
                                ) + fadeIn(),
                                exit = slideOutVertically(
                                    targetOffsetY = { it }
                                ) + fadeOut()

                            ) {
                                var isOversized by remember(altText) { mutableStateOf(false) }
                                var showTapToExpand by remember {
                                    mutableStateOf(
                                        context.getSharedPreferences(
                                            "image_viewer",
                                            Context.MODE_PRIVATE
                                        ).getInt("alt_expand_uses", 0) < 3
                                    )
                                }
                                val haptics = LocalHapticFeedback.current

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.2f),
                                                    Color.Black.copy(alpha = 0.5f),
                                                    Color.Black.copy(alpha = 0.8f),
                                                )
                                            )
                                        )
                                        .padding(horizontal = 24.dp, vertical = 38.dp)
                                        .padding(top = 20.dp)
                                        .then(
                                            if (isOversized) {
                                                Modifier
                                                    .clickable {
                                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                                        val preferences = context.getSharedPreferences(
                                                            "image_viewer",
                                                            Context.MODE_PRIVATE
                                                        )
                                                        val uses = preferences.getInt("alt_expand_uses", 0) + 1
                                                        preferences.edit {
                                                            putInt("alt_expand_uses", uses)
                                                        }
                                                        if (uses >= 3) {
                                                            showTapToExpand = false
                                                        }
                                                        altSheetState.onOpen()
                                                    }
                                                    .clip(MaterialTheme.shapes.small)
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (post?.value != null) {
                                        val record = post.value!!
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Avatar(
                                                avatarUrl = record.author.avatar?.uri,
                                                contentDescription = record.author.displayName,
                                                modifier = Modifier.size(24.dp)
                                            )

                                            Text(
                                                text = record.author.displayName
                                                    ?: record.author.handle.handle,
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Medium
                                                )
                                            )
                                        }
                                    }

                                    Text(
                                        text = altText,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        onTextLayout = { textLayoutResult ->
                                            if (textLayoutResult.didOverflowHeight) {
                                                isOversized = true
                                            }
                                        },
                                    )

                                    if (showTapToExpand) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.TouchApp,
                                                contentDescription = "Touch",
                                                tint = Color.White.copy(0.8f),
                                                modifier = Modifier.size(12.dp)
                                            )

                                            Text(
                                                "Tap to expand",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

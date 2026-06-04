package io.github.turtlepaw.mindsky.routes.post

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ImageBackButton(
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    alpha: Float = 0.3f
) {
    IconButton(
        onClick = { navigator.navigateUp() },
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha),
                CircleShape
            )
            .size(size)
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Image(navigator: DestinationsNavigator, imageUrl: String, alt: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = Color.Black,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    navigationIcon = {
                        ImageBackButton(navigator, modifier = Modifier.padding(start = 16.dp))
                    },
                    title = {},
                    actions = {
                        IconButton(onClick = {
                            scope.launch {
                                val result = downloadImage(
                                    context = context,
                                    imageUrl = imageUrl,
                                    fileName = imageUrl.toUri().lastPathSegment ?: "bsky.jpg"
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
                        }) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "Download",
                                tint = Color.White
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
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
                        //.zIndex(10f)
                        .onSizeChanged { containerSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = newScale

                                val newOffset = clampOffset(offset + pan)
                                offset = newOffset
                            }
                        }, // ensure it's above everything
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = imageUrl,
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
                    )

                    if (alt != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .padding(bottom = 16.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.7f),
                                    MaterialTheme.shapes.medium
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = alt,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
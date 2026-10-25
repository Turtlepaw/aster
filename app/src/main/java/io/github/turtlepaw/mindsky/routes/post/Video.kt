package io.github.turtlepaw.mindsky.routes.post

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
import fyi.kittens.shared.ui.embeds.VideoPlayerCache
import io.github.turtlepaw.mindsky.components.sheets.Sheet
import io.github.turtlepaw.mindsky.components.sheets.rememberSheetState
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import kotlinx.coroutines.launch
import kotlin.math.max
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.bsky.embed.ImagesViewImage
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.util.Locale

suspend fun downloadVideo(
    context: Context,
    videoUrl: String,
    fileName: String
): Result<Uri> {
    return try {
        val extension = MimeTypeMap.getFileExtensionFromUrl(videoUrl)
            .ifBlank { "mp4" }
        val safeFileName = fileName.substringBeforeLast('.', fileName) + ".$extension"
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension)
            ?: "video/mp4"
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val request = DownloadManager.Request(videoUrl.toUri())
            .setMimeType(mimeType)
            .setAllowedNetworkTypes(
                DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
            )
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setTitle(safeFileName)
            .setDescription("Downloading video")
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                safeFileName
            )

        downloadManager.enqueue(request)
        Result.success(videoUrl.toUri())
    } catch (e: Exception) {
        Result.failure(e)
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun FullscreenVideo(
    navigator: DestinationsNavigator,
    videoUrl: String,
    postUri: String? = null,
    position: Long? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = LocalPreferences.current

    val videoSoundDefault by rememberPreference(AppPrefs.VideoSoundEnabled)

    val player = remember(videoUrl) { VideoPlayerCache.get(context, videoUrl) }
    val videoSize = remember { mutableStateOf<VideoSize?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(videoSoundDefault) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPrepared by remember { mutableStateOf(false) }
    var hasStarted by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (!isPrepared) {
            hasStarted = true
            isLoading = true
            if (player.mediaItemCount == 0) {
                player.setMediaItem(MediaItem.fromUri(videoUrl))
                player.prepare()
            } else {
                isPrepared = player.playbackState == Player.STATE_READY
            }
            player.volume = if (isMuted) 0.0f else 1.0f
            if(position != null) player.seekTo(position)
            player.playWhenReady = true
        }
    }

    // Update position and duration
    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration.takeIf { it > 0 } ?: 0L
            delay(250)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(3_000)
            controlsVisible = false
        }
    }

    // Listen for player state changes
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize.value = size
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        isLoading = false
                        isPrepared = true
                    }

                    Player.STATE_BUFFERING -> {
                        isLoading = true
                    }

                    Player.STATE_ENDED -> {
                        isPlaying = false
                    }
                }
            }

            override fun onIsLoadingChanged(_isLoading: Boolean) {
                // We handle loading state in onPlaybackStateChanged
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
        }
    }

    // Manage lifecycle events
    DisposableEffect(Unit) {
        onDispose {
            player.pause()
        }
    }

    // Use detected video dimensions if available, otherwise default
    val aspectRatio =
        if (videoSize.value != null && videoSize.value!!.width > 0 && videoSize.value!!.height > 0) {
            videoSize.value!!.width.toFloat() / videoSize.value!!.height.toFloat()
        } else {
            null
        }

    val progress = if (duration > 0L) {
        (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
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
                    visible = controlsVisible,
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
                                        val result = downloadVideo(
                                            context = context,
                                            videoUrl = videoUrl,
                                            fileName = videoUrl.toUri().lastPathSegment
                                                ?: "bsky.mp4"
                                        )

                                        result.onSuccess {
                                            Toast.makeText(
                                                context,
                                                "Video saved",
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
            // Satisfy the compiler's requirement to use paddingValues
            // without affecting the fullscreen image layout
            Box(Modifier.padding(paddingValues))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .then(
                        if (aspectRatio != null) Modifier.aspectRatio(aspectRatio)
                        else Modifier.heightIn(min = 200.dp)
                    )
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                this.player = player
                                useController = false
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            if (!hasStarted) {
                                hasStarted = true
                            } else {
                                controlsVisible = !controlsVisible
                            }
                        }
                )

                if (!hasStarted || controlsVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.55f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.75f)
                                    )
                                )
                            )
                    )
                }

                if (!hasStarted) {
                    IconButton(
                        onClick = { hasStarted = true },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(68.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    controlsVisible,
                    modifier = Modifier
                        .align(Alignment.BottomCenter),
                    enter = slideInVertically(
                        initialOffsetY = { it }
                    ) + fadeIn(),
                    exit = slideOutVertically(
                        targetOffsetY = { it }
                    ) + fadeOut()

                ) {
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
                            .padding(top = 20.dp),

                        ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(
                                onClick = { player.playWhenReady = !isPlaying },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White
                                )
                            }

                            Text(
                                "${formatVideoTime(currentPosition)} / ${formatVideoTime(duration)}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )


                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    player.volume = if (isMuted) 0f else 1f

                                    prefs.set(AppPrefs.VideoSoundEnabled, isMuted)
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            ) {
                                Icon(
                                    if (isMuted) Icons.AutoMirrored.Filled.VolumeOff
                                    else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) "Unmute" else "Mute",
                                    tint = Color.White
                                )
                            }
                        }

                        Slider(
                            value = progress,
                            onValueChange = { player.seekTo((it * duration).toLong()) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatVideoTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000).coerceAtLeast(0L)
    return String.format(
        Locale.US,
        "%d:%02d",
        totalSeconds / 60,
        totalSeconds % 60
    )
}
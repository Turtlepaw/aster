package io.github.turtlepaw.mindsky.routes.composer

import android.content.ClipData
import android.content. ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.OpenableColumns
import android.net.Uri as AndroidUri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import io.github.turtlepaw.mindsky.R
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import sh.christian.ozone.api.model.Timestamp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
    import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.SearchActorsTypeaheadQueryParams
import app.bsky.embed.Images
import app.bsky.embed.ImagesImage
import app.bsky.embed.Video
import app.bsky.feed.Post
import app.bsky.feed.PostEmbedUnion
import app.bsky.richtext.Facet
import app.bsky.richtext.FacetByteSlice
import app.bsky.richtext.FacetFeatureUnion
import app.bsky.richtext.FacetLink
import app.bsky.richtext.FacetMention
import app.bsky.video.GetJobStatusQueryParams
import app.bsky.video.JobStatus
import coil3.compose.AsyncImage
import com.atproto.identity.ResolveHandleQueryParams
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.UploadBlobResponse
import com.atproto.server.GetServiceAuthQueryParams
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.imagedescription.ImageDescriberOptions
import com.google.mlkit.genai.imagedescription.ImageDescription
import com.google.mlkit.genai.imagedescription.ImageDescriptionRequest
import io.github.turtlepaw.mindsky.auth.SessionManager
import io.github.turtlepaw.mindsky.auth.UserSession
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.PostDensity
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.routes.ProfileAvatar
import io.github.turtlepaw.mindsky.routes.post.FullThreadDepth
import io.github.turtlepaw.mindsky.routes.post.rememberThreadReplies
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Nsid
import kotlin.time.Clock
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.Language
import sh.christian.ozone.api.model.Blob
import sh.christian.ozone.api.xrpc.BSKY_SOCIAL
import sh.christian.ozone.api.xrpc.defaultHttpClient
import sh.christian.ozone.api.xrpc.defaultHttpEngine
import java.io.File
import java.util.Date
import kotlin.random.Random

@Serializable
data class ImageAttachment(
    @Contextual val uri: AndroidUri,
    val altText: String = ""
)

@Serializable
data class PostContent(
    val text: String = "",
    val facets: List<Facet> = emptyList(),
    val embed: PostEmbedUnion? = null,
    val createdAt: Timestamp = Clock.System.now(),
    val images: List<ImageAttachment> = emptyList(),
    val video: ImageAttachment? = null,
    val resolvedMentions: Map<String, Did> = emptyMap()
) {
    fun isNotEmpty(): Boolean {
        return text.isNotEmpty() || images.isNotEmpty() || video != null
    }
}

object AndroidUriSerializer : KSerializer<AndroidUri> {

    override val descriptor = PrimitiveSerialDescriptor(
        "AndroidUri",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: AndroidUri) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): AndroidUri {
        return AndroidUri.parse(decoder.decodeString())
    }
}

val AppJson = Json {
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        contextual(AndroidUri::class, AndroidUriSerializer)
    }
}

@Composable
fun rememberComposerState(target: String?): MutableState<PostContent?> {
    val content = remember { mutableStateOf<PostContent?>(PostContent()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(
            "composer_drafts",
            Context.MODE_PRIVATE
        )

        val savedDraft = prefs.getString(target ?: "default", null)
        if (savedDraft != null) {
            try {
                content.value = AppJson.decodeFromString<PostContent>(savedDraft)
            } catch (e: Exception) {
                // Handle potential parsing errors
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(content.value) {
        val prefs = context.getSharedPreferences(
            "composer_drafts",
            Context.MODE_PRIVATE
        )

        val currentContent = content.value
        if (currentContent != null) {
            prefs.edit {
                putString(target ?: "default", AppJson.encodeToString(currentContent))
            }
        } else {
            // If content is null, remove the saved draft
            prefs.edit { remove(target ?: "default") }
        }
    }

    return content
}

val URL_REGEX =
    Regex("(?:http[s]?:\\/\\/.)?(?:www\\.)?[-a-zA-Z0-9@%._\\+~#=]{2,256}\\.[a-z]{2,6}\\b(?:[-a-zA-Z0-9@:%_\\+.~#?&\\/\\/=]*)")
val MENTION_REGEX = Regex("@([a-zA-Z0-9.-]+)")

class RichTextVisualTransformation(private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val annotatedString = buildAnnotatedString {
            append(text.text)
            val mentionRanges = mutableListOf<IntRange>()
            MENTION_REGEX.findAll(text.text).forEach { matchResult ->
                mentionRanges.add(matchResult.range)
                addStyle(
                    style = SpanStyle(
                        color = color,
                        fontWeight = FontWeight.Bold
                    ),
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1
                )
            }
            URL_REGEX.findAll(text.text).forEach { matchResult ->
                if (mentionRanges.any { it.contains(matchResult.range.first) || it.contains(matchResult.range.last) }) {
                    return@forEach
                }
                addStyle(
                    style = SpanStyle(
                        color = color,
                        textDecoration = TextDecoration.Underline
                    ),
                    start = matchResult.range.first,
                    end = matchResult.range.last + 1
                )
            }
        }
        return TransformedText(annotatedString, OffsetMapping.Identity)
    }
}

fun calculateFacets(text: String, resolvedMentions: Map<String, Did>): List<Facet> {
    val facets = mutableListOf<Facet>()
    val mentionRanges = mutableListOf<IntRange>()

    // Mentions
    MENTION_REGEX.findAll(text).forEach { matchResult ->
        val handle = matchResult.groupValues[1]
        val did = resolvedMentions[handle]
        if (did != null) {
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            mentionRanges.add(matchResult.range)

            val byteStart = text.substring(0, start).toByteArray(Charsets.UTF_8).size
            val byteEnd = text.substring(0, end).toByteArray(Charsets.UTF_8).size

            facets.add(
                Facet(
                    index = FacetByteSlice(byteStart.toLong(), byteEnd.toLong()),
                    features = listOf(FacetFeatureUnion.Mention(FacetMention(did)))
                )
            )
        }
    }

    // Links
    URL_REGEX.findAll(text).forEach { matchResult ->
        // Skip if this link is part of a mention
        if (mentionRanges.any { it.contains(matchResult.range.first) || it.contains(matchResult.range.last) }) {
            return@forEach
        }

        val url = matchResult.value
        val start = matchResult.range.first
        val end = matchResult.range.last + 1

        val byteStart = text.substring(0, start).toByteArray(Charsets.UTF_8).size
        val byteEnd = text.substring(0, end).toByteArray(Charsets.UTF_8).size

        facets.add(
            Facet(
                index = FacetByteSlice(byteStart.toLong(), byteEnd.toLong()),
                features = listOf(
                    FacetFeatureUnion.Link(
                        FacetLink(
                            sh.christian.ozone.api.Uri(
                                if (url.startsWith("http")) url else "https://$url"
                            )
                        )
                    )
                )
            )
        )
    }

    return facets.sortedBy { it.index.byteStart }
}

fun AndroidUri.toByteArray(
    context: Context,
): ByteArray? {
    return context.contentResolver
        .openInputStream(this)
        ?.use { inputStream ->
            inputStream.readBytes()
        }
}

suspend fun uploadVideo(
    api: BlueskyApi,
    session: UserSession,
    name: String,
    videoFile: File,
    onProgress: (Long?) -> Unit
): Blob? {
    val auth = api.getServiceAuth(
        GetServiceAuthQueryParams(
            aud = Did("did:web:${session.pdsUrl.toUri().host}"),
            lxm = Nsid("com.atproto.repo.uploadBlob"),
            exp = Clock.System.now().epochSeconds + 60 * 30, // 30 minutes
        )
    ).requireResponse()

    val token = auth.token

    val uploadUrl =
        "https://video.bsky.app/xrpc/app.bsky.video.uploadVideo".toUri().buildUpon().apply {
            appendQueryParameter("did", session.did)
            appendQueryParameter("name", name)
        }.build()

    val request = Request.Builder()
        .url(uploadUrl.toString())
        .addHeader("Authorization", "Bearer $token")
        .addHeader("Content-Type", "video/mp4")
        .addHeader("Content-Length", videoFile.length().toString())
        .post(
            videoFile.asRequestBody("video/mp4".toMediaType())
        )
        .build()

    val client = OkHttpClient()
    val videoClient = XrpcBlueskyApi(
        httpClient = HttpClient(
            defaultHttpEngine
        ) {
            install(DefaultRequest) {
                url.takeFrom(
                    Url("https://video.bsky.app")
                )
            }
        })

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw Exception("Upload failed: ${response.code} ${response.message} ($response)")
        }

        val body = response.body

        if (body != null) {
            val response = Json.decodeFromString<JobStatus>(body.string())
            Log.d("UploadVideo", "Upload successful: $response")
            onProgress(response.progress)

            while (true) {
                val jobStatus = videoClient.getJobStatus(
                    GetJobStatusQueryParams(
                        jobId = response.jobId
                    )
                ).requireResponse()

                onProgress(jobStatus.jobStatus.progress)

                if (jobStatus.jobStatus.blob != null) {
                    Log.d("UploadVideo", "Upload successful: ${jobStatus.jobStatus.blob}")
                    return jobStatus.jobStatus.blob
                }
            }
        }
    }

    return null
}

private const val ALPHABET =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz-"

fun nanoId(size: Int = 12): String =
    buildString(size) {
        repeat(size) {
            append(ALPHABET[Random.nextInt(ALPHABET.length)])
        }
    }

fun AndroidUri.toFile(context: Context): File {
    val fileName = context.contentResolver.query(
        this,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
        } else "temp_video"
    } ?: "temp_video"

    val tempFile = File(context.cacheDir, fileName)

    context.contentResolver.openInputStream(this).use { input ->
        tempFile.outputStream().use { output ->
            input!!.copyTo(output)
        }
    }

    return tempFile
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun Composer(navigator: DestinationsNavigator, postUri: String? = null) {
    val contentState = rememberComposerState(postUri)
    var content by contentState
    val primaryColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    val api = LocalMindskyApi.current
    val session by LocalSessionManager.current.sessionFlow.collectAsState()
    val developerMode by AppPrefs.DeveloperMode.stateFlow.collectAsState()
    val scope = rememberCoroutineScope()

    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(content?.text ?: ""))
    }

    var mentionSuggestions by remember { mutableStateOf<List<ProfileViewBasic>>(emptyList()) }
    var mentionQuery by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(textFieldValue.text) {
        val text = textFieldValue.text
        val selection = textFieldValue.selection.start
        
        if (selection > 0) {
            val textBeforeCursor = text.substring(0, selection)
            val lastAtIndex = textBeforeCursor.lastIndexOf('@')
            if (lastAtIndex != -1) {
                val query = textBeforeCursor.substring(lastAtIndex + 1)
                // Only trigger if there's no space between @ and cursor
                if (!query.contains(' ')) {
                    mentionQuery = query
                } else {
                    mentionQuery = null
                }
            } else {
                mentionQuery = null
            }
        } else {
            mentionQuery = null
        }
    }

    LaunchedEffect(mentionQuery) {
        val query = mentionQuery
        if (query == null || query.length < 1) {
            mentionSuggestions = emptyList()
            return@LaunchedEffect
        }

        try {
            val response = api.searchActorsTypeahead(
                SearchActorsTypeaheadQueryParams(
                    q = query,
                    limit = 5
                )
            ).requireResponse()
            mentionSuggestions = response.actors
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var editingAltTextImageIndex by remember { mutableStateOf<Int?>(null) }
    var videoUploadProgress by remember { mutableStateOf<Long?>(null) }

    // Registers a photo picker activity launcher in multi-select mode.
// In this example, the app lets the user select up to 5 media files.
    val pickMultipleMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
            // Callback is invoked after the user selects media items or closes the
            // photo picker.
            if (uris.isNotEmpty()) {
                Log.d("PhotoPicker", "Number of items selected: ${uris.size}")
                uris.forEach { uri ->
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        Log.e("PhotoPicker", "Failed to take persistable URI permission", e)
                    }
                }

                val hasVideo = uris.any {
                    // check if type is video
                    context.contentResolver.getType(it)?.startsWith("video") ?: false
                }
                if (hasVideo) {
                    if(uris.size != 1) Toast.makeText(context, "Only one video allowed", Toast.LENGTH_SHORT).show()
                    val video = uris.first()
                    content = content?.copy(
                        video = ImageAttachment(video)
                    )
                } else {
                    val newImages = uris.map { ImageAttachment(it) }
                    content =
                        content?.copy(images = (content?.images ?: emptyList()) + newImages)
                }
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }

    suspend fun onPost() {
        withContext(Dispatchers.IO) {
            val text = content?.text ?: ""
            val resolvedMentions = content?.resolvedMentions?.toMutableMap() ?: mutableMapOf()

            // Find all mentions in the text
            val allMentions = MENTION_REGEX.findAll(text).map { it.groupValues[1] }.toSet()

            // Resolve unknown mentions
            allMentions.forEach { handle ->
                if (!resolvedMentions.containsKey(handle)) {
                    try {
                        val response = api.resolveHandle(ResolveHandleQueryParams(Handle(handle))).requireResponse()
                        resolvedMentions[handle] = response.did
                    } catch (e: Exception) {
                        // Failed to resolve, skip it
                    }
                }
            }

            val finalFacets = calculateFacets(text, resolvedMentions)

            val currentImages = content?.images ?: emptyList()
            val blobs = emptyList<Pair<UploadBlobResponse, ImageAttachment>>().toMutableList()
            if (currentImages.isNotEmpty()) {
                currentImages.forEach {
                    val blob = api.uploadBlob(
                        // needs a ByteArray
                        it.uri.toByteArray(context) ?: throw Exception("No ByteArray")
                    ).requireResponse()

                    blobs.add(blob to it)
                }
            }

            val video = content?.video
            val videoBlob = if (video != null) {
                val mimeType = context.contentResolver.getType(video.uri)
                val extension = when (mimeType) {
                    "video/mp4" -> "mp4"
                    "video/webm" -> "webm"
                    "video/quicktime" -> "mov"
                    else -> null
                }

                val blob = uploadVideo(
                    api = api,
                    session = session!!,
                    name = "${nanoId(12)}.${extension}",
                    videoFile = video.uri.toFile(context)
                ) {
                    if (it != null) videoUploadProgress = it
                    else videoUploadProgress = -1
                }

                blob
                    ?: return@withContext {
                        Toast.makeText(context, "Failed to upload video", Toast.LENGTH_SHORT).show()
                    }
            } else null

            val postRecord = Post(
                text = text,
                createdAt = Clock.System.now(),
                langs = listOf(Language("en")), // Default to English or detect language
                reply = null,
                facets = finalFacets,
                embed = if (videoBlob != null) PostEmbedUnion.Video(
                    value = Video(
                        video = videoBlob,
                        alt = video?.altText
                    )
                ) else if (currentImages.isNotEmpty()) PostEmbedUnion.Images(
                    Images(
                        images = blobs.map {
                            ImagesImage(
                                image = it.first.blob,
                                alt = it.second.altText
                            )
                        }
                    )
                )
                else content?.embed,
            )

            val recordJson = BlueskyJson.encodeToJsonElement(Post.serializer(), postRecord)
                .jsonObject.toMutableMap().apply {
                    put("\$type", JsonPrimitive("app.bsky.feed.post"))
                }

            val result = api.createRecord(
                CreateRecordRequest(
                    repo = Did(session?.did ?: throw Exception("No DID")),
                    collection = Nsid("app.bsky.feed.post"),
                    record = BlueskyJson.encodeAsJsonContent(JsonObject(recordJson))
                )
            )

            if (result is sh.christian.ozone.api.response.AtpResponse.Success) {
                Log.d("Composer", "Post created: ${result.response.uri}")
                content = PostContent() // Clear state
                withContext(Dispatchers.Main) {
                    navigator.popBackStack()
                }
            } else {
                val failure = result as sh.christian.ozone.api.response.AtpResponse.Failure
                Log.e("Composer", "Failed to create post: ${failure.error}")
                throw failure.asException()
            }
        }
    }

    var isLoading by remember { mutableStateOf(false) }

    val feedModel = LocalFeedModel.current
    val post = if (postUri != null) feedModel.usePost(postUri) else null

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        navigator.popBackStack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                title = {
                    Text(
                        stringResource(
                            if (postUri != null) R.string.composer_reply else R.string.composer_new_post
                        ),
                        
                    )
                },
                actions = {
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    try {
                                        onPost()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading && (content != null && content!!.isNotEmpty())
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(stringResource(R.string.post))
                            }
                        }
                }
            )
        },
        floatingActionButton = {
            HorizontalFloatingToolbar(
                expanded = true
            ) {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(

                        ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.add_image)) } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        onClick = {
                            pickMultipleMedia.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        enabled = content?.video == null && (content?.images?.size ?: 0) < 4
                    ) {
                        Icon(
                            Icons.Filled.Photo,
                            contentDescription = stringResource(R.string.photo)
                        )
                    }
                }
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(

                        ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.add_video)) } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        onClick = {
                            pickMultipleMedia.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.VideoOnly,
                                    maxItems = 2
                                )
                            )
                        },
                        enabled = content?.video == null && content?.images?.isEmpty() == true
                    ) {
                        Icon(
                            Icons.Filled.Movie,
                            contentDescription = stringResource(R.string.video)
                        )
                    }
                }
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(

                        ),
                    tooltip = { PlainTooltip { Text(stringResource(R.string.delete)) } },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        onClick = {
                            content = PostContent()
                        },
                        enabled = !isLoading && (content != null && content!!.isNotEmpty())
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete)
                        )
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Start
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (post != null) item {
                if (post.isLoading) {
                    LoadingPost()
                } else if (post.value != null) {
                    PostComponent(
                        post.value!!,
                        navigator,
                        enabled = false,
                        density = PostDensity.Regular,
                        showActions = false,
                        showLabels = false,
                        showReply = false,
                        showSeparator = false,
                        showHandle = false
                    ) {}
                }

                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(
                            vertical = 8.dp
                        ),
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    ProfileAvatar(
                        modifier = Modifier.size(40.dp),
                        enabled = false,
                        onAvatarClick = {}
                    )
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            content = content?.copy(
                                text = newValue.text,
                                facets = calculateFacets(
                                    newValue.text,
                                    content?.resolvedMentions ?: emptyMap()
                                ),
                            )
                        },
                        visualTransformation = remember(primaryColor) {
                            RichTextVisualTransformation(primaryColor)
                        },
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier,
                            ) {
                                if (content?.text.isNullOrEmpty()) {
                                    Text(
                                        stringResource(R.string.composer_hint),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
            }

            item {
                if (mentionSuggestions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                MaterialTheme.shapes.medium
                            )
                            .clip(MaterialTheme.shapes.medium)
                    ) {
                        mentionSuggestions.forEach { actor ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val text = textFieldValue.text
                                        val selection = textFieldValue.selection.start
                                        val textBeforeCursor = text.substring(0, selection)
                                        val lastAtIndex = textBeforeCursor.lastIndexOf('@')

                                        if (lastAtIndex != -1) {
                                            val newText = text.substring(0, lastAtIndex) + "@${actor.handle.handle} " + text.substring(selection)
                                            val newCursorPosition = lastAtIndex + actor.handle.handle.length + 2

                                            val newResolvedMentions = (content?.resolvedMentions ?: emptyMap()) + (actor.handle.handle.toString() to actor.did)

                                            content = content?.copy(
                                                text = newText,
                                                resolvedMentions = newResolvedMentions,
                                                facets = calculateFacets(newText, newResolvedMentions)
                                            )
                                            textFieldValue = TextFieldValue(
                                                text = newText,
                                                selection = TextRange(newCursorPosition)
                                            )
                                            mentionQuery = null
                                            mentionSuggestions = emptyList()
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Avatar(
                                    modifier = Modifier.size(32.dp),
                                    avatarUrl = actor.avatar?.uri,
                                    contentDescription = actor.displayName ?: actor.handle.handle,
                                )
                                Column {
                                    Text(
                                        text = actor.displayName ?: actor.handle.handle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "@${actor.handle.handle}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (content != null && content?.video != null) item {
                val player = remember { ExoPlayer.Builder(context).build() }

                LaunchedEffect(videoUploadProgress) {
                    if (videoUploadProgress != null) {
                        player.pause()
                    }
                }

                DisposableEffect(Unit) {
                    val mediaItem = MediaItem.fromUri(content!!.video!!.uri)
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.playWhenReady = true
                    player.repeatMode = ExoPlayer.REPEAT_MODE_ALL

                    onDispose {
                        player.release()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .padding(horizontal = 16.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                this.player = player
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (videoUploadProgress == null) IconButton(
                        onClick = {
                            content = content?.copy(video = null)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                androidx.compose.foundation.shape.CircleShape
                            )
                            .size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove video",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (videoUploadProgress != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(75.dp)
                            )
                        }
                    }
                }
            }
            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content?.images?.forEachIndexed { index, attachment ->
                        Box(modifier = Modifier.size(100.dp)) {
                            AsyncImage(
                                model = attachment.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(150.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .border(
                                        1.dp,
                                        if (editingAltTextImageIndex == index)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        MaterialTheme.shapes.small
                                    )
                                    .clickable {
                                        editingAltTextImageIndex = index
                                    }
                            )

                            // Remove button
                            IconButton(
                                onClick = {
                                    content = content?.copy(
                                        images = content?.images?.filterIndexed { i, _ -> i != index }
                                            ?: emptyList()
                                    )
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
//                                    .background(
//                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
//                                        RoundedCornerShape(bottomStart = 8.dp)
//                                    )
                                    .size(24.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove image",
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Alt text indicator/button
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .clickable { editingAltTextImageIndex = index }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (attachment.altText.isEmpty()) "ALT" else "ALT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CompositionLocalProvider(
                                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                                ) {
                                    if (attachment.altText.isNotEmpty()) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = "Check",
                                            modifier = Modifier.size(12.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.Edit,
                                            contentDescription = "Edit",
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (developerMode && content?.facets?.isNotEmpty() == true) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.shapes.medium
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Facet Debug",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Byte count: ${content?.text?.toByteArray(Charsets.UTF_8)?.size ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (content?.resolvedMentions?.isNotEmpty() == true) {
                            Text(
                                text = "Resolved Mentions: ${content?.resolvedMentions?.keys?.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        content?.facets?.forEach { facet ->
                            val type = when (val feature = facet.features.firstOrNull()) {
                                is FacetFeatureUnion.Link -> "Link (${feature.value.uri.uri})"
                                is FacetFeatureUnion.Mention -> "Mention (${feature.value.did.did})"
                                is FacetFeatureUnion.Tag -> "Tag (#${feature.value.tag})"
                                else -> "Unknown"
                            }
                            Text(
                                text = "[$type] bytes ${facet.index.byteStart}-${facet.index.byteEnd}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Button(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(
                                    "Facets JSON",
                                    content?.facets?.toString() ?: "[]"
                                )
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Facets copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.padding(top = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Copy Facets JSON", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    editingAltTextImageIndex?.let { index ->
        val currentImages = content?.images ?: emptyList()
        AltTextDialog(
            uri = currentImages[index].uri,
            initialText = currentImages[index].altText,
            onDismiss = { editingAltTextImageIndex = null },
            onChange = { newAlt ->
                val updatedImages = currentImages.toMutableList().apply {
                    this[index] = this[index].copy(altText = newAlt)
                }
                content = content?.copy(images = updatedImages)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AltTextDialog(
    uri: AndroidUri,
    initialText: String,
    onDismiss: () -> Unit,
    onChange: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var isGenerating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state
    ) {
        Box(
            modifier = Modifier
                .padding(16.dp)
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        //contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.shapes.large
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.shapes.small
                            )
                    )
                }
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Edit Alt Text",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Alt text describes images for blind or low-vision people.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            onChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Describe this image...") },
                        enabled = !isGenerating
                    )
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isGenerating = true
                                    text = ""
                                    generateAltText(context, uri) {
                                        text += it
                                        onChange(text)
                                    }
                                    isGenerating = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isGenerating) {
                                    LoadingIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(
                                        Icons.Default.AutoFixHigh,
                                        contentDescription = "Auto fix"
                                    )
                                }

                                Text("Generate with AI")
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    state.hide()
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done")
                        }
                    }
                }
                item {
                    Text(
                        stringResource(R.string.alt_text_warning),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

suspend fun generateAltText(
    context: Context,
    uri: AndroidUri,
    onStream: (String) -> Unit
) {
    val bitmap = try {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
    } catch (e: Exception) {
        null
    } ?: return

    val options = ImageDescriberOptions.builder(context).build()
    val imageDescriber = ImageDescription.getClient(options)

    try {
        val featureStatus = imageDescriber.checkFeatureStatus().await()
        if (featureStatus == FeatureStatus.DOWNLOADABLE) {
            // Inference request will automatically run once feature is downloaded.
            Log.d("AltText", "Model is downloadable, starting download...")
        }

        val request = ImageDescriptionRequest.builder(bitmap).build()
        val imageDescriptionResultStreaming =
            imageDescriber.runInference(request) { outputText ->
                onStream(outputText)
            }.await()
    } catch (e: Exception) {
        Log.e("AltText", "Error generating alt text", e)
    } finally {
        imageDescriber.close()
    }
}


@Preview
@Composable
fun ComposerPreview() {
    Composer(navigator = EmptyDestinationsNavigator)
}

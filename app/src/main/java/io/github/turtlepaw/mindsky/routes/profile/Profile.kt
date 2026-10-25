package io.github.turtlepaw.mindsky.routes.profile

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SpeakerNotesOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalUriHandler
import app.bsky.actor.GetProfilesQueryParams
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.graph.Follow
import app.bsky.graph.GetListsWithMembershipQueryParams
import app.bsky.graph.GetListsWithMembershipResponse
import app.bsky.graph.Listitem
import coil3.compose.AsyncImage
import com.atproto.repo.CreateRecordRequest
import com.atproto.repo.DeleteRecordRequest
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BookmarksDestination
import com.ramcosta.composedestinations.generated.destinations.FullsizePostDestination
import com.ramcosta.composedestinations.generated.destinations.ImageDestination
import io.github.turtlepaw.mindsky.routes.post.ImageDataStore
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.generated.destinations.UserListDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.components.post.ThreadedPostComponent
import fyi.kittens.shared.FeedTuner
import fyi.kittens.shared.auth.getPdsUrlFromDid
import io.github.turtlepaw.fetch_and_cache.CollectionCache
import fyi.kittens.shared.R
import fyi.kittens.shared.ui.Avatar
import io.github.turtlepaw.mindsky.components.post.Checkmark
import io.github.turtlepaw.mindsky.components.post.Labelers
import io.github.turtlepaw.mindsky.components.post.PdsIcon
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.components.post.Robot
import io.github.turtlepaw.mindsky.components.post.getRkey
import fyi.kittens.shared.ui.rememberLoadingColor
import io.github.turtlepaw.mindsky.di.LocalFeedModel
import io.github.turtlepaw.mindsky.di.LocalIdentityProvider
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.di.LocalNavController
import io.github.turtlepaw.mindsky.di.LocalScrollToTop
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import fyi.kittens.shared.ui.LoadMore
import io.github.turtlepaw.mindsky.routes.Loading
import fyi.kittens.shared.Formatters
import fyi.kittens.shared.cleanPins
import fyi.kittens.shared.cleanReplies
import fyi.kittens.shared.ui.ContentAwareProvider
import fyi.kittens.shared.ui.ExpandableContent
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.mindsky.components.sheets.Sheet
import io.github.turtlepaw.mindsky.components.sheets.dismissButton
import io.github.turtlepaw.mindsky.components.post.LoadingPost
import io.github.turtlepaw.mindsky.components.sheets.rememberSheetState
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.routes.UserListType
import io.github.turtlepaw.mindsky.viewmodels.FeedViewModel
import io.github.turtlepaw.mindsky.components.media.NowPlayingProfile
import io.github.turtlepaw.mindsky.components.sheets.CodeSheet
import io.github.turtlepaw.mindsky.components.sheets.EditProfileSheet
import io.github.turtlepaw.mindsky.components.sheets.toPrettyJson
import io.github.turtlepaw.mindsky.media.NowPlayingStore
import io.github.turtlepaw.mindsky.media.Track
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import io.github.turtlepaw.mindsky.repositories.MediaRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Nsid
import sh.christian.ozone.api.model.JsonContent.Companion.encodeAsJsonContent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val startPadding = PaddingValues(horizontal = 15.dp)

private data class TabDestination(
    val label: String,
    val icon: Int? = null,
)

enum class BadgeInfoType {
    Bot,
    Verified,
    Pds
}

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalTime::class
)
@Composable
fun BadgeInformationSheet(
    badge: BadgeInfoType?,
    pds: String?,
    profileData: ProfileViewDetailed,
    onDismiss: () -> Unit,
    navigator: DestinationsNavigator
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    if (badge != null) {
        ModalBottomSheet(
            onDismissRequest = {
                onDismiss()
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (badge == BadgeInfoType.Bot) {
                    Text("This account is labeled as automated.")
                } else if (badge == BadgeInfoType.Pds) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdsIcon(
                            pds,
                            Modifier.size(24.dp)
                        )

                        Text(
                            pds ?: "",
                            style = MaterialTheme.typography.titleMediumEmphasized
                        )
                    }

                    Text(
                        "This account is hosted on $pds"
                    )
                } else if (badge == BadgeInfoType.Verified) {
                    val api = LocalMindskyApi.current

                    val accounts = CollectionCache(
                        identifier = "verification_accounts_${profileData.did.did}",
                        serializer = ProfileViewDetailed.serializer(),
                        fetcher = {
                            val verifications = profileData.verification?.verifications
                            if (verifications.isNullOrEmpty()) {
                                emptyMap()
                            } else {
                                val verificationData = api.getProfiles(
                                    GetProfilesQueryParams(
                                        verifications.map { it.issuer }
                                    )
                                ).requireResponse()
                                verificationData.profiles.associateBy { it.did.did }
                            }
                        },
                    ).load()

                    Text("This account has been verified by trusted sources.")
                    if (accounts.isLoading) {
                        repeat(2) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                ) {
                                    val color = rememberLoadingColor()
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                color,
                                                CircleShape
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .height(20.dp)
                                            .fillMaxWidth(0.5f)
                                            .background(
                                                color,
                                                RoundedCornerShape(8.dp)
                                            )
                                    )
                                }
                            }
                        }
                    } else if (accounts.error != null) {
                        Text("Failed to load verification data: ${accounts.error!!.message}")
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            accounts.value.values.forEach { account ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(
                                            MaterialTheme.shapes.medium
                                        )
                                        .clickable {
                                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                                onDismiss()
                                                navigator.navigate(
                                                    ProfileDestination(account.did.did)
                                                )
                                            }
                                        }
                                        .padding(10.dp)
                                ) {
                                    Avatar(
                                        avatarUrl = account.avatar?.uri,
                                        contentDescription = "${account.displayName}'s avatar",
                                        clip = CircleShape,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Column() {
                                        Text(
                                            account.displayName ?: account.handle.handle,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        val verification =
                                            profileData.verification?.verifications?.find { it.issuer == account.did }
                                        if (verification != null) {
                                            val formatter = DateTimeFormatter
                                                .ofPattern("MMMM d, yyyy")
                                                .withZone(ZoneId.systemDefault())
                                            Text(
                                                formatter.format(
                                                    Instant.ofEpochMilli(verification.createdAt.toEpochMilliseconds())
                                                ),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.elevatedButtonColors(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Okay")
                }
            }
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun Profile(navigator: DestinationsNavigator, did: String? = null) {
    val viewModel = LocalFeedModel.current
    val profileData by viewModel.profileDetailed
    val isLoading by viewModel.isFetchingProfileMetadata

    val session by LocalSessionManager.current.sessionFlow.collectAsState()
    val identity = did ?: session?.did

    if (identity == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Not logged in")
        }
        return
    }

    var selectedDestination by remember { mutableIntStateOf(0) }

    val api = LocalMindskyApi.current
    val posts = if (identity.isNotBlank()) {
        rememberPosts(Did(identity), enabled = selectedDestination == 0 || selectedDestination == 1)
    } else {
        null
    }
    val starterPacks = if (identity.isNotBlank()) {
        rememberStarterPacks(Did(identity), enabled = selectedDestination == 2)
    } else {
        null
    }

    val hasBackStack = LocalNavController.current.previousBackStackEntry != null
    val listState = rememberLazyListState()
    val scrollToTopHandler = LocalScrollToTop.current
    val coroutineScope = rememberCoroutineScope()
    var badgeShown by remember { mutableStateOf<BadgeInfoType?>(null) }
    PredictiveBackHandler(enabled = hasBackStack) { progress ->
        progress.collect { }
        navigator.navigateUp()
    }

    DisposableEffect(listState) {
        val handler: () -> Unit = {
            coroutineScope.launch {
                listState.animateScrollToItem(0)
                posts?.refresh()
            }
            Unit
        }
        scrollToTopHandler.value = handler
        onDispose {
            if (scrollToTopHandler.value === handler) {
                scrollToTopHandler.value = null
            }
        }
    }
    val userDid = session?.did

    LaunchedEffect(identity, viewModel) {
        if (identity.isNotBlank() && identity.startsWith("did:")) {
            val did = try {
                Did(identity)
            } catch (e: Exception) {
                null
            }
            if (did != null) {
                viewModel.fetchProfileMetadata(did)
            }
        }
    }

    val destinations = listOf(
        TabDestination(
            label = stringResource(R.string.posts)
        ),
        TabDestination(
            label = stringResource(R.string.replies)
        ),
        TabDestination(
            label = "Starter packs",
        ),
        TabDestination(
            label = "Tangled",
            icon = R.drawable.ic_tangled
        ),
    )

    var pds by remember { mutableStateOf<String?>(null) }
    var nowPlayingByRecord by remember { mutableStateOf<Track?>(null) }
    val localNowPlaying by NowPlayingStore.nowPlaying.collectAsState()

    LaunchedEffect(identity) {
        val pdsUrl = try {
            getPdsUrlFromDid(identity)
        } catch(e: Exception){
            e.printStackTrace()
            null
        }
        pds = pdsUrl
            ?.replace("https://", "")
            ?.replace("http://", "")
            ?.replace("/", "")

        if (identity.isNotBlank()) {
            nowPlayingByRecord = MediaRepository.getPlayingNow(api, identity)
        }
    }

    if (!isLoading && profileData != null) {
        BadgeInformationSheet(
            badge = badgeShown,
            pds = pds,
            profileData = profileData!!,
            onDismiss = { badgeShown = null },
            navigator = navigator
        )
    }

    var moreOptionsExpanded by remember { mutableStateOf(false) }
    val listsState = rememberSheetState()
    var processingListUri by remember { mutableStateOf<sh.christian.ozone.api.AtUri?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val developerMode by rememberPreference(AppPrefs.DeveloperMode)
    val codeSheetState = rememberSheetState()
    CodeSheet(codeSheetState, profileData?.toPrettyJson() ?: "NOT_LOADED")

    val editProfileSheet = rememberSheetState()
    EditProfileSheet(editProfileSheet, identity)

    Sheet(
        listsState,
        loadData = {
            SingleCache(
                fetcher = {
                    api.getListsWithMembership(
                        GetListsWithMembershipQueryParams(
                            actor = Did(identity),
                        )
                    ).requireResponse()
                },
                serializer = GetListsWithMembershipResponse.serializer(),
                identifier = "lists_${identity}"
            ).load()
        }
    ) { close, data ->
        if (data.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            }
        } else if (data.value != null) {
            if (data.value!!.listsWithMembership.isEmpty()) {
                item {
                    Text(
                        "No lists found",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(data.value!!.listsWithMembership) { list ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                8.dp,
                                Alignment.Start
                            )
                        ) {
                            Avatar(
                                modifier = Modifier.size(45.dp),
                                list.list.avatar?.uri,
                                contentDescription = list.list.name,
                                MaterialTheme.shapes.medium,
                                fallback = {
                                    Icon(
                                        Icons.Rounded.Groups,
                                        contentDescription = "Group",
                                        it
                                    )
                                }
                            )
                            Column {
                                Text(
                                    list.list.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val isProcessing = processingListUri == list.list.uri
                        ToggleButton(
                            checked = list.listItem == null,
                            enabled = !isProcessing,
                            onCheckedChange = {
                                scope.launch {
                                    processingListUri = list.list.uri
                                    try {
                                        if (list.listItem != null) {
                                            api.deleteRecord(
                                                DeleteRecordRequest(
                                                    repo = Did(session!!.did),
                                                    collection = Nsid("app.bsky.graph.listitem"),
                                                    rkey = list.listItem!!.uri.getRkey()
                                                )
                                            ).requireResponse()
                                        } else {
                                            api.createRecord(
                                                CreateRecordRequest(
                                                    repo = Did(session!!.did),
                                                    collection = Nsid("app.bsky.graph.listitem"),
                                                    record = BlueskyJson.encodeAsJsonContent(
                                                        Listitem(
                                                            subject = Did(identity),
                                                            list = list.list.uri,
                                                            createdAt = Clock.System.now()
                                                        )
                                                    )
                                                )
                                            ).requireResponse()
                                        }
                                        data.refresh?.invoke()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(
                                            context,
                                            "Failed to update list: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        processingListUri = null
                                    }
                                }
                            }
                        ) {
                            if (isProcessing) {
                                LoadingIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Text(
                                    if (list.listItem != null) "Remove" else "Add",
                                )
                            }
                        }
                    }
                }
            }
        } else if (data.error != null) {
            item {
                Text(
                    "Error: ${data.error!!.message}",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        dismissButton(close)
    }

    var isFollowing by remember(profileData) { mutableStateOf(profileData?.viewer?.following != null) }

    Scaffold { paddingValues ->
        val safeDid = remember(identity) {
            if (identity.isBlank() || !identity.startsWith("did:")) null
            else try {
                Did(identity)
            } catch (e: Exception) {
                null
            }
        }

        if (safeDid == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (identity.isBlank()) "Profile not specified" else "Invalid profile: $identity")
            }
            return@Scaffold
        }

        CompositionLocalProvider(
            LocalIdentityProvider provides safeDid
        ) {
            val localLabels = LocalLabelManager.current
            val definitions by localLabels.labelersDefinitionFlow.collectAsState()
            ContentAwareProvider(
                labels = profileData?.labels,
                labelDefinitions = definitions
            ) {
                val threadedPosts = if (posts != null) {
                    remember(posts.posts.value) {
                        FeedTuner.groupThreads(posts.posts.value)
                    }
                } else null

                LazyColumn(
                    state = listState,
                ) {
                    if (isLoading) {
                        item {
                            val color = rememberLoadingColor()
                            ProfileStructure(
                                banner = {
                                    Box(
                                        modifier = it.background(
                                            color
                                        )
                                    )
                                },
                                avatar = {
                                    Box(
                                        modifier = it
                                            .background(
                                                color,
                                                CircleShape
                                            )
                                    )
                                },
                                actions = {},
                                navigator = navigator,
                                showBackButton = hasBackStack,
                                avatarOnClick = null
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(26.dp))
                        }
                        items(10) {
                            LoadingPost()
                        }
                    } else if (profileData == null) {
                        item {
                            Text("Failed to load profile")
                        }
                    } else {
                        item {
                            ProfileStructure(
                                banner = {
                                    if (profileData!!.banner?.uri != null)
                                        AsyncImage(
                                            model = profileData!!.banner?.uri,
                                            contentDescription = "${profileData!!.displayName}'s banner",
                                            contentScale = ContentScale.Crop,
                                            modifier = it.clickable {
                                                ImageDataStore.put(profileData!!.banner!!.uri, emptyList())
                                                navigator.navigate(
                                                    ImageDestination(
                                                        profileData!!.banner!!.uri,
                                                        alt = null
                                                    )
                                                )
                                            }
                                        )
                                    else AsyncImage(
                                        model = profileData!!.avatar?.uri,
                                        contentDescription = "${profileData!!.displayName}'s avatar",
                                        contentScale = ContentScale.Crop,
                                        modifier = it
                                            .blur(80.dp)
                                            .graphicsLayer {
                                                alpha = 0.3f
                                            }
                                    )
                                },
                                avatar = {
                                    Avatar(
                                        avatarUrl = profileData!!.avatar?.uri,
                                        contentDescription = profileData!!.displayName,
                                        clip = CircleShape,
                                        modifier = it
                                    )
                                },
                                actions = {
                                    Row {
                                        val haptics = LocalHapticFeedback.current
                                        ToggleButton(
                                            checked = identity != userDid && !isFollowing,
                                            onCheckedChange = {
                                                if (identity != userDid) {
                                                coroutineScope.launch {
                                                    try {
                                                        val followingUri =
                                                            profileData?.viewer?.following
                                                        if (isFollowing && followingUri != null) {
                                                            api.deleteRecord(
                                                                DeleteRecordRequest(
                                                                    repo = Did(session!!.did),
                                                                    collection = Nsid("app.bsky.graph.follow"),
                                                                    rkey = followingUri.getRkey()
                                                                )
                                                            ).requireResponse()
                                                            isFollowing = false
                                                        } else if (!isFollowing) {
                                                            val followRecord = Follow(
                                                                subject = profileData!!.did,
                                                                createdAt = Clock.System.now()
                                                            )

                                                            val recordJson =
                                                                BlueskyJson.encodeToJsonElement(
                                                                    Follow.serializer(),
                                                                    followRecord
                                                                ).jsonObject.toMutableMap().apply {
                                                                    put(
                                                                        "\$type",
                                                                        JsonPrimitive("app.bsky.graph.follow")
                                                                    )
                                                                }

                                                            api.createRecord(
                                                                CreateRecordRequest(
                                                                    repo = Did(session!!.did),
                                                                    collection = Nsid("app.bsky.graph.follow"),
                                                                    record = BlueskyJson.encodeAsJsonContent(
                                                                        JsonObject(recordJson)
                                                                    )
                                                                )
                                                            ).requireResponse()
                                                            isFollowing = true
                                                        }
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            } else {
                                                editProfileSheet.onOpen()
                                                }
                                            }
                                        ) {
                                            Text(
                                                if (identity != userDid) {
                                                    if (isFollowing) "Following" else "Follow"
                                                } else {
                                                    "Edit Profile"
                                                },
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Box {
                                            IconButton(
                                                onClick = { moreOptionsExpanded = true },
                                                colors = IconButtonDefaults.filledTonalIconButtonColors()
                                            ) {
                                                Icon(
                                                    Icons.Rounded.MoreHoriz,
                                                    contentDescription = "More options"
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = moreOptionsExpanded,
                                                onDismissRequest = { moreOptionsExpanded = false }
                                            ) {
                                                if (identity == userDid) {
                                                    DropdownMenuItem(
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Rounded.Bookmark,
                                                                contentDescription = "Bookmarks"
                                                            )
                                                        },
                                                        text = { Text("Bookmarks") },
                                                        onClick = {
                                                            moreOptionsExpanded = false
                                                            navigator.navigate(BookmarksDestination)
                                                        }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                            contentDescription = "List add"
                                                        )
                                                    },
                                                    text = { Text("Add to list") },
                                                    onClick = {
                                                        moreOptionsExpanded = false
                                                        listsState.onOpen()
                                                    }
                                                )

                                                if (developerMode) DropdownMenuItem(
                                                    text = {
                                                        Text("Raw JSON")
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Rounded.Build,
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            moreOptionsExpanded = false
                                                            codeSheetState.onOpen()
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                showBackButton = hasBackStack,
                                navigator = navigator,
                            ) {
                                val avatar = profileData!!.avatar?.uri
                                if (avatar != null) {
                                    ImageDataStore.put(avatar, emptyList())
                                    navigator.navigate(
                                        ImageDestination(
                                            avatar,
                                            alt = null
                                        )
                                    )
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        item {
                            val track =
                                if (identity == userDid) localNowPlaying else nowPlayingByRecord
                            NowPlayingProfile(track)
                        }
                        item {
                            Row(
                                modifier = Modifier.padding(startPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    profileData!!.displayName ?: profileData!!.handle.handle,
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                                if (profileData!!.labels?.find { it.`val` == "bot" } != null) {
                                    Robot(
                                        Modifier
                                            .size(25.dp)
                                            .clickable {
                                                badgeShown = BadgeInfoType.Bot
                                            })
                                }
                                if (profileData!!.verification?.verifications?.isNotEmpty() == true) {
                                    Checkmark(
                                        Modifier
                                            .size(25.dp)
                                            .clickable {
                                                badgeShown = BadgeInfoType.Verified
                                            }
                                    )
                                }
                                PdsIcon(
                                    pds,
                                    Modifier
                                        .size(20.dp)
                                        .clickable {
                                            badgeShown = BadgeInfoType.Pds
                                        }
                                )
                            }
                        }
                        item {
                            FlowRow(
                                modifier = Modifier.padding(startPadding),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (profileData!!.viewer?.followedBy != null) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainer,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                width = 0.5.dp,
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                    0.2f
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Follows you",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Text(
                                    "@${profileData!!.handle.handle}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                )

                                // pronouns
                                if (profileData!!.pronouns != null)
                                    Text(
                                        profileData!!.pronouns!!,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                    )
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        item {
                            FlowRow(
                                modifier = Modifier.padding(startPadding),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalArrangement = Arrangement.spacedBy(
                                    5.dp,
                                    Alignment.CenterVertically
                                )
                            ) {
                                val followingText = stringResource(R.string.following)
                                val followersText = stringResource(R.string.followers)
                                val boldText =
                                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                val accessoryText =
                                    MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    buildAnnotatedString {
                                        withStyle(boldText.toSpanStyle()) {
                                            append(Formatters.formatCompactNumber(profileData!!.followersCount!!.toInt()))
                                        }
                                        withStyle(style = accessoryText.toSpanStyle()) {
                                            append(" ")
                                            append(followersText.lowercase())
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .clip(
                                            MaterialTheme.shapes.small
                                        )
                                        .clickable {
                                            navigator.navigate(
                                                UserListDestination(
                                                    identity,
                                                    type = UserListType.Followers(identity)
                                                )
                                            )
                                        }
                                )
                                Text(
                                    buildAnnotatedString {
                                        withStyle(boldText.toSpanStyle()) {
                                            append(Formatters.formatCompactNumber(profileData!!.followsCount!!.toInt()))
                                        }
                                        withStyle(style = accessoryText.toSpanStyle()) {
                                            append(" ")
                                            append(followingText.lowercase())
                                        }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .clip(
                                            MaterialTheme.shapes.small
                                        )
                                        .clickable {
                                            navigator.navigate(
                                                UserListDestination(
                                                    identity,
                                                    type = UserListType.Following(identity)
                                                )
                                            )
                                        }
                                )
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        item {
                            Box(
                                modifier = Modifier.padding(startPadding),
                            ) {
                                ExpandableContent(
                                    collapsedHeight = 100.dp,
                                ) {
                                    SelectionContainer {
                                        Text(
                                            profileData!!.description ?: "",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        item {
                            val followedBy = profileData!!.viewer?.knownFollowers
                            if (followedBy != null && identity != userDid) {
                                Row(
                                    modifier = Modifier
                                        .padding(startPadding)
                                        .clip(MaterialTheme.shapes.medium)
                                        .clickable {
                                            navigator.navigate(
                                                UserListDestination(
                                                    identity,
                                                    type = UserListType.KnownFollowers(identity)
                                                )
                                            )
                                        }
                                        .padding(2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(46.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val trimmedFollowedBy = followedBy.followers.take(3)
                                    AvatarStack(
                                        items = trimmedFollowedBy,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(3.dp)
                                        ) {
                                            Avatar(
                                                modifier = Modifier.fillMaxSize(),
                                                avatarUrl = it.avatar?.uri,
                                                contentDescription = "${it.displayName ?: it.handle}'s avatar",
                                                clip = CircleShape,
                                            )
                                        }
                                    }
                                    val names = followedBy.followers.take(2)
                                        .joinToString(", ") { it.displayName ?: it.handle }
                                    val text =
                                        if (followedBy.followers.size > 2) "$names, and ${followedBy.followers.size} others" else names

                                    Text(
                                        "Followed by $text",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.9f)
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                        if (profileData!!.website != null) {
                            item {
                                val uriHandler = LocalUriHandler.current
                                Row(
                                    modifier = Modifier
                                        .padding(startPadding)
                                        .padding(bottom = 16.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .clickable {
                                            uriHandler.openUri(profileData!!.website!!.uri)
                                        },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        8.dp
                                    )
                                ) {
                                    Icon(
                                        Icons.Rounded.Public,
                                        contentDescription = "Globe",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        profileData!!.website!!.uri
                                            .removePrefix("https://")
                                            .removePrefix("http://")
                                            .removeSuffix("/"),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }
                        }
                        item {
                            Box(
                                modifier = Modifier.padding(startPadding)
                            ) {
                                Labelers(profileData!!.labels, true)
                            }
                        }
                        item {
                            Spacer(modifier = Modifier.height(15.dp))
//                    HorizontalDivider(
//                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
//                        thickness = 0.5.dp
//                    )
                            PrimaryTabRow(
                                selectedTabIndex = selectedDestination,
                                modifier = Modifier
                            ) {
                                destinations.forEachIndexed { index, destination ->
                                    Tab(
                                        selected = selectedDestination == index,
                                        onClick = {
                                            selectedDestination = index
                                        },
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                if (destination.icon != null) {
                                                    Icon(
                                                        painter = painterResource(destination.icon),
                                                        contentDescription = destination.label,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = LocalContentColor.current
                                                    )
                                                }
                                                Text(
                                                    text = destination.label,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedDestination == 0) {
                            if (posts != null) {
                                profilePosts(
                                    posts,
                                    threadedPosts ?: emptyList(),
                                    true,
                                    navigator,
                                    viewModel
                                )
                            } else {
                                Loading()
                            }
                        } else if (selectedDestination == 1) {
                            if (posts != null) {
                                profilePosts(
                                    posts,
                                    threadedPosts ?: emptyList(),
                                    false,
                                    navigator,
                                    viewModel
                                )
                            } else {
                                Loading()
                            }
                        } else if (selectedDestination == 2) {
                            if (starterPacks != null) {
                                starterPacks(
                                    starterPacks,
                                    coroutineScope
                                )
                            } else {
                                Loading()
                            }
                        } else {
                            tangled(identity, navigator, selectedDestination == 3)
                        }

                        item {
                            Spacer(modifier = Modifier.height(15.dp))
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}

private data class RememberedPosts(
    val posts: MutableState<List<FeedViewPost>>,
    val isLoading: MutableState<Boolean>,
    val refresh: () -> Unit
)

@Composable
private fun rememberPosts(account: Did, enabled: Boolean): RememberedPosts {
    val api = LocalFeedModel.current

    LaunchedEffect(account, api, enabled) {
        if (!enabled) return@LaunchedEffect
        if (api.profilePosts.value.isEmpty() || api.profilePosts.value.firstOrNull()?.post?.author?.did != account) {
            api.fetchProfilePosts(account, isRefresh = true)
        }
    }

    return RememberedPosts(
        posts = api.profilePosts,
        isLoading = api.isFetchingProfile,
        refresh = {
            api.fetchProfilePosts(account, isRefresh = true)
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.profilePosts(
    posts: RememberedPosts,
    threadedPosts: List<List<FeedViewPost>>,
    filterPosts: Boolean = false,
    navigator: DestinationsNavigator,
    api: FeedViewModel
) {
    if (posts.isLoading.value && posts.posts.value.isEmpty()) {
        return Loading()
    }

    val items =
        if (filterPosts)
            FeedTuner.groupThreads(posts.posts.value.cleanReplies().cleanPins())
        else threadedPosts

    if(items.isEmpty()){
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Rounded.SpeakerNotesOff,
                    contentDescription = "No messages"
                )
                Text("No posts found")
            }
        }
    } else {
        val reasons = posts.posts.value.map { it.reason }
        Log.d("ProfilePosts", "Reasons: $reasons")
        val pinned = posts.posts.value.find { it.reason == FeedViewPostReasonUnion.ReasonPin }
        if (pinned != null) {
            item {
                PostComponent(
                    postView = pinned,
                    navigator = navigator
                ) {
                    navigator.navigate(
                        FullsizePostDestination(postUri = pinned.post.uri.atUri)
                    )
                }
            }
        }

        items(
            items = items,
            key = { it.first().post.uri.atUri }
        ) { thread ->
            ThreadedPostComponent(
                thread,
                navigator
            ) { clickedPost ->
                navigator.navigate(
                    FullsizePostDestination(postUri = clickedPost.post.uri.atUri)
                )
            }
        }
        if (posts.posts.value.isNotEmpty() && api.hasMoreProfile.value) {
            item {
                LoadMore(posts.posts.value.size) {
                    api.loadMoreProfilePosts()
                }
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(150.dp))
            }
        }
    }
}

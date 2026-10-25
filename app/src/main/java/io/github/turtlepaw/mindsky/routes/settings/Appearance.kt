package io.github.turtlepaw.mindsky.routes.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.bsky.actor.ProfileViewBasic
import app.bsky.embed.AspectRatio
import app.bsky.embed.ImagesView
import app.bsky.embed.ImagesViewImage
import app.bsky.feed.PostView
import app.bsky.feed.PostViewEmbedUnion
import app.bsky.feed.ViewerState
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.R
import io.github.turtlepaw.mindsky.cache.MockLabelManager
import io.github.turtlepaw.mindsky.components.GenericTopBar
import io.github.turtlepaw.mindsky.components.post.PostComponent
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.di.LocalProfileModel
import io.github.turtlepaw.mindsky.di.LocalSessionManager
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import io.github.turtlepaw.mindsky.utils.StringComposable
import io.github.turtlepaw.mindsky.viewmodels.ProfileUiState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.switchPreference
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Cid
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Handle
import sh.christian.ozone.api.Uri
import sh.christian.ozone.api.model.JsonContent
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Appearance(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val preferences = LocalPreferences.current
    val mockLabelManager = remember(context) { MockLabelManager(context) }
    val useCatppuccin by rememberPreference(AppPrefs.UseCatppuccinTheme)
    val catppuccinVariant by rememberPreference(AppPrefs.CatppuccinVariant)
    val catppuccinAccent by rememberPreference(AppPrefs.CatppuccinAccent)
    val showHandle by rememberPreference(AppPrefs.ShowHandle)

    val fontPreference by rememberPreference(AppPrefs.Font)
    val fontWeightPreference by rememberPreference(AppPrefs.FontWeight)
    val fontWidthPreference by rememberPreference(AppPrefs.FontWidth)
    val fontRoundnessPreference by rememberPreference(AppPrefs.FontRoundness)

    var showAccentDialog by remember { mutableStateOf(false) }

    val sessionManager = LocalSessionManager.current
    val session by sessionManager.sessionFlow.collectAsState()
    val profileModel = LocalProfileModel.current
    val profileUiState by profileModel.uiState.collectAsState()
    val currentProfile = (profileUiState as? ProfileUiState.Success)?.profile

    Scaffold(
        topBar = {
            GenericTopBar(
                title = stringResource(R.string.appearance),
                icon = {
                    Icon(
                        Icons.Rounded.Palette,
                        contentDescription = "Palette",
                    )
                },
                navigator = navigator,
                listState = null
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = paddingValues,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            switchPreference(
                key = preferences.getKey(AppPrefs.DarkTheme),
                title = {
                    R.string.dark_theme_dark.StringComposable()
                },
                summary = {
                    R.string.dark_theme_dark_description.StringComposable()
                },
                defaultValue = AppPrefs.DarkTheme.defaultValue
            )
            switchPreference(
                key = preferences.getKey(AppPrefs.ShowLabelerAvatars),
                title = {
                    R.string.show_avatar_labelers.StringComposable()
                },
                summary = {
                    R.string.show_avatar_labelers_description.StringComposable()
                },
                defaultValue = AppPrefs.ShowLabelerAvatars.defaultValue
            )
            switchPreference(
                key = preferences.getKey(AppPrefs.UseCatppuccinTheme),
                title = {
                    R.string.catppuccin_theme.StringComposable()
                },
                summary = {
                    R.string.catppuccin_theme_description.StringComposable()
                },
                defaultValue = AppPrefs.UseCatppuccinTheme.defaultValue
            )

            item {
                AnimatedVisibility(
                    visible = useCatppuccin,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.catppuccin_variant),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val variants = listOf("Mocha", "Frappé", "Macchiato")
                                variants.forEachIndexed { index, variant ->
                                    SegmentedButton(
                                        selected = (catppuccinVariant == variant),
                                        onClick = {
                                            preferences.set(
                                                AppPrefs.CatppuccinVariant,
                                                variant
                                            )
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = index,
                                            count = variants.size
                                        )
                                    ) {
                                        Text(variant)
                                    }
                                }
                            }
                        }
                        Preference(
                            title = {
                                R.string.catppuccin_accent.StringComposable()
                            },
                            summary = {
                                Text(text = catppuccinAccent)
                            },
                            onClick = {
                                showAccentDialog = true
                            }
                        )
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Font Header with Reset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.font),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (fontPreference != AppPrefs.Font.defaultValue) {
                            IconButton(
                                onClick = {
                                    preferences.set(AppPrefs.Font, AppPrefs.Font.defaultValue)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.reset),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        val fonts = listOf("Sora", "Google Sans")
                        fonts.forEachIndexed { index, font ->
                            SegmentedButton(
                                selected = (fontPreference == font),
                                onClick = {
                                    preferences.set(
                                        AppPrefs.Font,
                                        font
                                    )
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = fonts.size
                                )
                            ) {
                                Text(font)
                            }
                        }
                    }

                    // Font Weight Header with Reset
                    SliderPreference(
                        title = stringResource(R.string.font_weight),
                        value = fontWeightPreference.toFloat(),
                        valueRange = 100f..900f,
                        steps = 7,
                        defaultValue = AppPrefs.FontWeight.defaultValue.toFloat(),
                        onValueChange = { newValue ->
                            preferences.set(AppPrefs.FontWeight, newValue.toInt())
                        },
                        onReset = {
                            preferences.set(AppPrefs.FontWeight, AppPrefs.FontWeight.defaultValue)
                        }
                    )

                    // Font Width Header with Reset
                    SliderPreference(
                        title = stringResource(R.string.font_width),
                        value = fontWidthPreference.toFloat(),
                        valueRange = 75f..125f,
                        steps = 9,
                        defaultValue = AppPrefs.FontWidth.defaultValue.toFloat(),
                        onValueChange = { newValue ->
                            preferences.set(AppPrefs.FontWidth, newValue.toInt())
                        },
                        onReset = {
                            preferences.set(AppPrefs.FontWidth, AppPrefs.FontWidth.defaultValue)
                        },
                        // This formats the displayed value as a percentage (e.g., "100%")
                        valueDisplayTransformation = { value -> "${value.toInt()}%" }
                    )

                    SliderPreference(
                        title = stringResource(R.string.font_roundness),
                        value = fontRoundnessPreference.toFloat(),
                        valueRange = 0f..100f,
                        steps = 9,
                        defaultValue = AppPrefs.FontRoundness.defaultValue.toFloat(),
                        onValueChange = { newValue ->
                            preferences.set(AppPrefs.FontRoundness, newValue.toInt())
                        },
                        onReset = {
                            preferences.set(AppPrefs.FontRoundness, AppPrefs.FontRoundness.defaultValue)
                        },
                        // This formats the displayed value as a percentage (e.g., "100%")
                        valueDisplayTransformation = { value -> "${value.toInt()}%" }
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.post_appearance),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            switchPreference(
                key = preferences.getKey(AppPrefs.ShowHandle),
                title = {
                    R.string.show_handle.StringComposable()
                },
                summary = {
                    R.string.show_handle_description.StringComposable()
                },
                defaultValue = showHandle
            )
            switchPreference(
                key = preferences.getKey(AppPrefs.ShowRichTextIcons),
                title = {
                    R.string.show_rich_text_icons.StringComposable()
                },
                summary = {
                    R.string.show_rich_text_icons_description.StringComposable()
                },
                defaultValue = AppPrefs.ShowRichTextIcons.defaultValue
            )

            session?.let { session ->
                item {
                    CompositionLocalProvider(
                        LocalLabelManager provides mockLabelManager
                    ) {
                        Box {
                            PostComponent(
                                postView = PostView(
                                    uri = AtUri("at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.post/3l7qg4v3xjk2e"),
                                    cid = Cid("bafyreig5cdo4grj7hf53vfhcpxvhp7e2mq5gjka4j5kz7x3b5f6q2y4a7i"),
                                    author = ProfileViewBasic(
                                        did = Did(session.did),
                                        handle = Handle(session.handle),
                                        displayName = currentProfile?.displayName
                                            ?: session.displayName ?: session.handle,
                                        avatar = currentProfile?.avatar
                                            ?: session.avatarUrl?.toOzoneUri(),
                                        associated = null,
                                        viewer = null,
                                        labels = listOf(mockLabelManager.mockLabel),
                                        createdAt = Instant.fromEpochSeconds(1686817800L),
                                    ),
                                    record = JsonContent(
                                        buildJsonObject {
                                            put("\$type", "app.bsky.feed.post")
                                            put(
                                                "text",
                                                "Hello! Check out https://example.com"
                                            )
                                            putJsonArray("facets") {
                                                add(
                                                    buildJsonObject {
                                                        putJsonObject("index") {
                                                            put("byteStart", 17)
                                                            put("byteEnd", 36)
                                                        }
                                                        putJsonArray("features") {
                                                            add(
                                                                buildJsonObject {
                                                                    put(
                                                                        "\$type",
                                                                        "app.bsky.richtext.facet#link"
                                                                    )
                                                                    put(
                                                                        "uri",
                                                                        "https://example.com"
                                                                    )
                                                                }
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                            put("createdAt", "2026-09-24T14:32:10.583Z")
                                            putJsonArray("langs") {
                                                add("en")
                                            }
                                        },
                                        format = BlueskyJson
                                    ),
                                    embed = PostViewEmbedUnion.ImagesView(
                                        ImagesView(
                                            images = listOf(
                                                ImagesViewImage(
                                                    thumb = Uri("https://thumb.wikimedia.org/wikipedia/commons/thumb/e/e4/Asteraceae_-_Aster_amellus.JPG/960px-Asteraceae_-_Aster_amellus.JPG"),
                                                    fullsize = Uri("https://upload.wikimedia.org/wikipedia/commons/e/e4/Asteraceae_-_Aster_amellus.JPG"),
                                                    alt = "Aster amellus (?) in Fata Morgana Greenhouse, Prague Botanical Garden (Botanika Zahrada Fata Morgana Praha)\n\nImage by Hectonichus, CC BY-SA 3.0 <https://creativecommons.org/licenses/by-sa/3.0>, via Wikimedia Commons",
                                                    aspectRatio = AspectRatio(
                                                        width = 1023,
                                                        height = 768
                                                    ),
                                                )
                                            )
                                        )
                                    ),
                                    bookmarkCount = 12L,
                                    replyCount = 34L,
                                    repostCount = 89L,
                                    likeCount = 472L,
                                    quoteCount = 7L,
                                    indexedAt = Instant.fromEpochSeconds(1790260331L),
                                    viewer = ViewerState(
                                        repost = AtUri("at://did:plc:abc123def456/app.bsky.feed.repost/3l7qh2m9vnk2p"),
                                        like = AtUri("at://did:plc:abc123def456/app.bsky.feed.like/3l7qh3n8xjk2q"),
                                        replyDisabled = false,
                                        embeddingDisabled = false,
                                        pinned = false,
                                        bookmarked = false,
                                    ),
                                    labels = listOf(),
                                    threadgate = null,
                                    debug = null,
                                ),
                                navigator = navigator,
                                enabled = false,
                                readOnly = true
                            ) { }
                        }
                    }
                }

                item {
                    val uriHandler = LocalUriHandler.current
                    Text(
                        "Image by Hectonichus, CC BY-SA 3.0 <https://creativecommons.org/licenses/by-sa/3.0>, via Wikimedia Commons",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp).clip(MaterialTheme.shapes.large).clickable {
                            uriHandler.openUri("https://commons.wikimedia.org/wiki/File:Asteraceae_-_Aster_amellus.JPG")
                        }.padding(8.dp)
                    )
                }
            }
        }

        if (showAccentDialog) {
            AlertDialog(
                onDismissRequest = { showAccentDialog = false },
                title = { Text(stringResource(R.string.catppuccin_accent)) },
                text = {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val accents = listOf(
                            "Rosewater", "Flamingo", "Pink", "Mauve", "Red", "Maroon",
                            "Peach", "Yellow", "Green", "Teal", "Sky", "Sapphire",
                            "Blue", "Lavender"
                        )
                        items(accents.size) { index ->
                            val accent = accents[index]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        preferences.set(AppPrefs.CatppuccinAccent, accent)
                                        showAccentDialog = false
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (accent == catppuccinAccent),
                                    onClick = null
                                )
                                Text(
                                    text = accent,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAccentDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SliderPreference(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    modifier: Modifier = Modifier,
    defaultValue: Float? = null,
    onReset: (() -> Unit)? = null,
    valueDisplayTransformation: (Float) -> String = { it.toInt().toString() }
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = valueDisplayTransformation(value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Show reset button only if a default value is provided and current value differs
            if (defaultValue != null && value != defaultValue && onReset != null) {
                IconButton(
                    onClick = onReset,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.reset),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

fun String.toOzoneUri(): Uri {
    return Uri(this)
}

package io.github.turtlepaw.mindsky.routes.composer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.MutableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import io.github.turtlepaw.mindsky.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import sh.christian.ozone.api.model.Timestamp
import androidx.core.content.edit
import androidx.core.net.toUri
import app.bsky.feed.PostEmbedUnion
import app.bsky.richtext.Facet
import app.bsky.richtext.FacetByteSlice
import app.bsky.richtext.FacetFeatureUnion
import app.bsky.richtext.FacetLink
import kotlinx.datetime.Clock
import sh.christian.ozone.api.Uri

@Serializable
data class PostContent(
    val text: String,
    val facets: List<Facet>,
    val embeds: List<PostEmbedUnion>,
    val createdAt: Timestamp = Clock.System.now()
)

@Composable
fun rememberComposerState(): MutableState<PostContent?> {
    val content = remember { mutableStateOf<PostContent?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(
            "composer_drafts",
            Context.MODE_PRIVATE
        )

        val savedDraft = prefs.getString("draft", null)
        if (savedDraft != null) {
            try {
                content.value = Json.decodeFromString<PostContent>(savedDraft)
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
                putString("draft", Json.encodeToString(currentContent))
            }
        } else {
            // If content is null, remove the saved draft
            prefs.edit { remove("draft") }
        }
    }

    return content
}

val URL_REGEX =
    Regex("(?:http[s]?:\\/\\/.)?(?:www\\.)?[-a-zA-Z0-9@%._\\+~#=]{2,256}\\.[a-z]{2,6}\\b(?:[-a-zA-Z0-9@:%_\\+.~#?&\\/\\/=]*)")

class LinkVisualTransformation(private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val annotatedString = buildAnnotatedString {
            append(text.text)
            URL_REGEX.findAll(text.text).forEach { matchResult ->
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun Composer(navigator: DestinationsNavigator) {
    val contentState = rememberComposerState()
    var content by contentState
    val primaryColor = MaterialTheme.colorScheme.primary

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
                    Text(stringResource(R.string.composer_title))
                },
                actions = {
                    Button(
                        onClick = { /* TODO: Implement post submission logic */ }
                    ) {
                        Text(stringResource(R.string.post))
                    }
                }
            )
        },
    ) { contentPadding ->
        HorizontalFloatingToolbar(
            expanded = true
        ) {
            TooltipBox(
                positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(

                ),
                tooltip = { PlainTooltip { Text("Localized description") } },
                state = rememberTooltipState(),
            ) {
                IconButton(onClick = { /* doSomething() */ }) {
                    Icon(Icons.Filled.Person, contentDescription = "Localized description")
                }
            }
        }
        LazyColumn(
            contentPadding = contentPadding
        ) {
            item {
                BasicTextField(
                    value = content?.text ?: "",
                    onValueChange = { newText ->
                        val embeds = emptyList<PostEmbedUnion>()
                        val facets = if (URL_REGEX.containsMatchIn(newText)) {
                            val matches = URL_REGEX.findAll(newText)
                            matches.map { matchResult ->
                                val url = matchResult.value
                                val start = matchResult.range.first
                                val end = matchResult.range.last + 1

                                // Convert char offsets to byte offsets
                                val byteStart =
                                    newText.substring(0, start).toByteArray(Charsets.UTF_8).size
                                val byteEnd =
                                    newText.substring(0, end).toByteArray(Charsets.UTF_8).size

                                Facet(
                                    index = FacetByteSlice(byteStart.toLong(), byteEnd.toLong()),
                                    features = listOf(
                                        FacetFeatureUnion.Link(
                                            value = FacetLink(
                                                uri = Uri(
                                                    url.toUri().toString()
                                                )
                                            )
                                        )
                                    )
                                )
                            }.toList()
                        } else emptyList()

                        content = PostContent(
                            text = newText,
                            facets = facets,
                            embeds = emptyList()
                        )
                    },
                    visualTransformation = remember(primaryColor) {
                        LinkVisualTransformation(primaryColor)
                    },
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier,
                        ) {
                            if (content?.text.isNullOrEmpty()) {
                                Text(stringResource(R.string.composer_hint), color = Color.DarkGray)
                            }
                            innerTextField()
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun ComposerPreview() {
    Composer(navigator = EmptyDestinationsNavigator)
}
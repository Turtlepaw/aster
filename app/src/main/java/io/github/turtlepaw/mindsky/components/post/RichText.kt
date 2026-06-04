package io.github.turtlepaw.mindsky.components.post

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import app.bsky.richtext.Facet
import app.bsky.richtext.FacetFeatureUnion
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun RichText(
    text: String,
    facets: List<Facet>?,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    linkColor: Color = MaterialTheme.colorScheme.primary
) {
    val uriHandler = LocalUriHandler.current
    val annotatedText = remember(text, facets, linkColor) {
        buildAnnotatedString {
            append(text)
            val textBytes = text.toByteArray(StandardCharsets.UTF_8)
            val textByteLength = textBytes.size

            facets?.forEach { facet ->
                val byteStart = facet.index.byteStart.toInt().coerceIn(0, textByteLength)
                val byteEnd = facet.index.byteEnd.toInt().coerceIn(byteStart, textByteLength)

                // Convert byte offsets to char offsets correctly
                val charStart = String(textBytes, 0, byteStart, StandardCharsets.UTF_8).length
                val charEnd = String(textBytes, 0, byteEnd, StandardCharsets.UTF_8).length

                if (charStart >= charEnd) return@forEach

                facet.features.forEach { feature ->
                    when (feature) {
                        is FacetFeatureUnion.Link -> {
                            addLink(
                                LinkAnnotation.Url(
                                    url = feature.value.uri.uri,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        hoveredStyle = SpanStyle(
                                            background = linkColor.copy(alpha = 0.1f)
                                        ),
                                        pressedStyle = SpanStyle(
                                            background = linkColor.copy(alpha = 0.2f)
                                        )
                                    )
                                ),
                                start = charStart,
                                end = charEnd
                            )
                        }

                        is FacetFeatureUnion.Mention -> {
                            addLink(
                                LinkAnnotation.Clickable(
                                    tag = feature.value.did.did,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        hoveredStyle = SpanStyle(
                                            background = linkColor.copy(alpha = 0.1f)
                                        ),
                                        pressedStyle = SpanStyle(
                                            background = linkColor.copy(alpha = 0.2f)
                                        )
                                    ),
                                    linkInteractionListener = {
                                        navigator.navigate(ProfileDestination(feature.value.did.did))
                                    }
                                ),
                                start = charStart,
                                end = charEnd
                            )
                        }

                        is FacetFeatureUnion.Tag -> {
                            addLink(
                                LinkAnnotation.Clickable(
                                    tag = feature.value.tag,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(color = linkColor),
                                        hoveredStyle = SpanStyle(
                                            background = linkColor.copy(alpha = 0.1f)
                                        ),
                                        pressedStyle = SpanStyle(
                                            background = linkColor.copy(alpha = 0.2f)
                                        )
                                    ),
                                    linkInteractionListener = {
                                        val encodedTag = URLEncoder.encode(
                                            feature.value.tag,
                                            StandardCharsets.UTF_8.name()
                                        )
                                        uriHandler.openUri("https://bsky.app/search?q=%23$encodedTag")
                                    }
                                ),
                                start = charStart,
                                end = charEnd
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    SelectionContainer(modifier = modifier) {
        Text(
            text = annotatedText,
            style = style.copy(color = MaterialTheme.colorScheme.onSurface),
        )
    }
}

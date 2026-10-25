package io.github.turtlepaw.mindsky.components.post

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.bsky.richtext.Facet
import app.bsky.richtext.FacetFeatureUnion
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.ui.Favicon
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Composable
fun RichText(
    text: String,
    facets: List<Facet>?,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    showIcons: Boolean = true
) {
    val uriHandler = LocalUriHandler.current

    val annotatedText = remember(text, facets, linkColor, showIcons) {
        buildAnnotatedString {
            val textBytes = text.toByteArray(StandardCharsets.UTF_8)
            val textByteLength = textBytes.size

            // Sort facets chronologically to parse text from left to right smoothly
            val sortedFacets = facets?.sortedBy { it.index.byteStart.toInt() } ?: emptyList()

            var lastByteIndex = 0
            var trackingOffset = 0

            sortedFacets.forEach { facet ->
                val byteStart = facet.index.byteStart.toInt().coerceIn(0, textByteLength)
                val byteEnd = facet.index.byteEnd.toInt().coerceIn(byteStart, textByteLength)

                if (byteStart >= byteEnd) return@forEach

                val normalText = String(textBytes, lastByteIndex, byteStart - lastByteIndex, StandardCharsets.UTF_8)
                append(normalText)

                val feature = facet.features.firstOrNull()
                val (iconId, metaValue) = if (showIcons) {
                    when (feature) {
                        is FacetFeatureUnion.Link -> "link_${byteStart}" to feature.value.uri.uri
                        is FacetFeatureUnion.Mention -> "mention_${byteStart}" to feature.value.did.did
                        else -> null to null
                    }
                } else {
                    null to null
                }

                if (iconId != null && metaValue != null) {
                    // 1. Store the metadata value directly over the exact index where the icon is placed
                    addStringAnnotation(tag = "FACET_META", annotation = metaValue, start = length, end = length + 1)

                    appendInlineContent(id = iconId, alternateText = "[icon]")
                    append("\u00A0")
                }

                // 3. Mark and append the styled Link text
                val facetText = String(textBytes, byteStart, byteEnd - byteStart, StandardCharsets.UTF_8)
                val charStart = length
                append(facetText)
                val charEnd = length

                feature?.let {
                    when (it) {
                        is FacetFeatureUnion.Link -> {
                            addLink(
                                LinkAnnotation.Url(
                                    url = it.value.uri.uri,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                                        hoveredStyle = SpanStyle(background = linkColor.copy(alpha = 0.1f)),
                                        pressedStyle = SpanStyle(background = linkColor.copy(alpha = 0.2f))
                                    )
                                ),
                                start = charStart,
                                end = charEnd
                            )
                        }

                        is FacetFeatureUnion.Mention -> {
                            addLink(
                                LinkAnnotation.Clickable(
                                    tag = it.value.did.did,
                                    styles = TextLinkStyles(
                                        style = SpanStyle(color = linkColor, fontWeight = FontWeight.Bold),
                                        hoveredStyle = SpanStyle(background = linkColor.copy(alpha = 0.1f)),
                                        pressedStyle = SpanStyle(background = linkColor.copy(alpha = 0.2f))
                                    ),
                                    linkInteractionListener = { _ ->
                                        navigator.navigate(ProfileDestination(it.value.did.did))
                                    }
                                ),
                                start = charStart,
                                end = charEnd
                            )
                        }

                        is FacetFeatureUnion.Tag -> {
                            addLink(
                                LinkAnnotation.Clickable(
                                    tag = it.value.tag.replace("#", ""),
                                    styles = TextLinkStyles(
                                        style = SpanStyle(color = linkColor),
                                        hoveredStyle = SpanStyle(background = linkColor.copy(alpha = 0.1f)),
                                        pressedStyle = SpanStyle(background = linkColor.copy(alpha = 0.2f))
                                    ),
                                    linkInteractionListener = { _ ->
                                        val encodedTag = URLEncoder.encode(it.value.tag, StandardCharsets.UTF_8.name())
                                        uriHandler.openUri("https://bsky.app")
                                    }
                                ),
                                start = charStart,
                                end = charEnd
                            )
                        }
                        else -> {}
                    }
                }

                lastByteIndex = byteEnd
            }

            // Append any remaining text left over after the final facet
            if (lastByteIndex < textByteLength) {
                append(String(textBytes, lastByteIndex, textByteLength - lastByteIndex, StandardCharsets.UTF_8))
            }
        }
    }

    // Build the icon map using the generated keys from the string
    val inlineContentMap = remember(annotatedText) {
        val map = mutableMapOf<String, androidx.compose.foundation.text.InlineTextContent>()

        annotatedText.getStringAnnotations(start = 0, end = annotatedText.length).forEach { annotation ->
            if (annotation.item.startsWith("link_") || annotation.item.startsWith("mention_")) {
                val id = annotation.item

                val placeholderIndex = annotation.start

                // 3. Extract the metadata payload assigned to this exact slot position
                val contextUrlOrData = annotatedText.getStringAnnotations(
                    tag = "FACET_META",
                    start = placeholderIndex,
                    end = placeholderIndex + 1
                ).firstOrNull()?.item ?: ""

                map[id] = androidx.compose.foundation.text.InlineTextContent(
                    Placeholder(
                        width = 16.sp,
                        height = 16.sp,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    if(id.startsWith("link_")){
                        Favicon(
                            url = try {
                                contextUrlOrData.toUri().host ?: ""
                            } catch (e: Exception) {
                                ""
                            },
                            contentDescription = "Website icon",
                            placeholder = {
                                Icon(
                                    Icons.Rounded.Link,
                                    contentDescription = "Link",
                                    tint = linkColor
                                )
                            },
                            modifier = Modifier.clip(MaterialTheme.shapes.small)
                        )
                    } else if (id.startsWith("mention_")) {
                        Icon(
                            imageVector = Icons.Rounded.AccountCircle,
                            contentDescription = null,
                            tint = linkColor
                        )
                    }
                }
            }
        }
        map
    }

    SelectionContainer(modifier = modifier) {
        Text(
            text = annotatedText,
            style = style.copy(color = MaterialTheme.colorScheme.onSurface),
            inlineContent = inlineContentMap
        )
    }
}

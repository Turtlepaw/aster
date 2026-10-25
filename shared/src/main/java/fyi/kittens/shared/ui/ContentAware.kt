package fyi.kittens.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.bsky.actor.ContentLabelPref
import app.bsky.actor.ContentLabelPrefVisibility
import app.bsky.actor.PreferencesUnion
import app.bsky.feed.Post
import app.bsky.feed.PostLabelsUnion
import app.bsky.feed.PostView
import app.bsky.labeler.GetServicesResponseViewUnion
import com.atproto.label.LabelValue
import com.atproto.label.LabelValueDefinition
import com.atproto.label.LabelValueDefinitionDefaultSetting
import com.atproto.label.LabelValueDefinitionStrings
import fyi.kittens.shared.R
import fyi.kittens.shared.ui.PostLabel.Companion.toPostLabel
import io.github.turtlepaw.fetch_and_cache.Cache
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.fetch_and_cache.SingleLoadResult
import kotlinx.serialization.builtins.ListSerializer
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Language

private fun Map<String, GetServicesResponseViewUnion>.getLabel(did: Did): GetServicesResponseViewUnion? {
    return this[did.did]
}

private fun Map<String, GetServicesResponseViewUnion>.findDefinition(identifier: String): Pair<Did, LabelValueDefinition>? {
    this.forEach { (did, view) ->
        val definition = view.getDefinitionOf(identifier)
        if (definition != null) {
            return Did(did) to definition
        }
    }
    return null
}

val visibilityPriority = mapOf(
    ContentLabelPrefVisibility.Hide to 0,
    ContentLabelPrefVisibility.Warn to 1,
    ContentLabelPrefVisibility.Show to 2,
    ContentLabelPrefVisibility.Ignore to 3
)

@Composable
fun ContentAware(
    post: PostView,
    labelDefinitions: Map<String, GetServicesResponseViewUnion>,
    content: @Composable () -> Unit,
) {
    val record = post.record.decodeAs<Post>()
    val preferences = LocalSavedPreferences.current
        ?: throw IllegalStateException("Saved preferences has not been loaded")

    val labelPreferences =
        preferences.filterIsInstance<PreferencesUnion.ContentLabelPref>().map { it.value }

    val authorVisibility = getVisibilityForLabels(
        post.author.labels,
        labelPreferences,
        labelDefinitions,
        visibilityPriority
    )

    val recordVisibility = when (val labels = record.labels) {
        is PostLabelsUnion.SelfLabels -> {
            labels.value.values.map { label ->
                labelPreferences.find { pref ->
                    pref.label == label.`val`
                }?.toPostLabel() ?: labelDefinitions.findDefinition(label.`val`)?.let { (did, def) ->
                    def.toPostLabel(did)
                } ?: PostLabel(
                    label = label.`val`,
                    src = null,
                    visibility = ContentLabelPrefVisibility.Warn,
                    `val` = label.`val`
                )
            }.minByOrNull { visibilityPriority[it.visibility] ?: Int.MAX_VALUE }
        }

        else -> null
    }

    val postVisibility = getVisibilityForLabels(
        post.labels,
        labelPreferences,
        labelDefinitions,
        visibilityPriority
    )

    val finalVisibility = listOfNotNull(authorVisibility, recordVisibility, postVisibility)
        .minByOrNull { visibilityPriority[it.visibility] ?: Int.MAX_VALUE }

    if (finalVisibility?.visibility?.shouldHide() == true) {
        val labelText =
            if (finalVisibility.src?.did != null) labelDefinitions.getLabel(finalVisibility.src)
                ?.getDefinitionOf(finalVisibility.`val`)?.locales?.firstOrNull()?.name
                ?: finalVisibility.label
            else if (finalVisibility.src == null) LabelValue.safeValueOf(finalVisibility.`val`)
                .localizedName()?.let { stringResource(it) } ?: finalVisibility.label
            else finalVisibility.label
        HiddenContent(
            label = labelText,
            content = content
        )
    } else {
        content()
    }
}

fun getVisibilityForLabels(
    labels: List<com.atproto.label.Label>?,
    labelPreferences: List<ContentLabelPref>,
    labelDefinitions: Map<String, GetServicesResponseViewUnion>,
    visibilityPriority: Map<ContentLabelPrefVisibility, Int>
): PostLabel? {
    return labels?.filter { it.neg != true }?.mapNotNull { label ->
        // 1. Check user preferences
        labelPreferences.find { pref ->
            if (pref.labelerDid != null) {
                pref.labelerDid == label.src && pref.label == label.`val`
            } else {
                pref.label == label.`val`
            }
        }?.toPostLabel()
        // 2. Check labeler definitions
        ?: labelDefinitions.getLabel(label.src)?.getDefinitionOf(label.`val`)?.toPostLabel(label.src)
        // 3. Fallback: If we follow the labeler but don't know this specific label, default to Warn
        ?: if (labelDefinitions.containsKey(label.src.did)) {
            PostLabel(
                label = label.`val`,
                src = label.src,
                visibility = ContentLabelPrefVisibility.Warn,
                `val` = label.`val`
            )
        } else null
    }?.minByOrNull { visibilityPriority[it.visibility] ?: Int.MAX_VALUE }
}

fun ContentLabelPrefVisibility.shouldHide(): Boolean {
    return this == ContentLabelPrefVisibility.Hide
}

class PostLabel(
    val label: String,
    val src: Did?,
    val visibility: ContentLabelPrefVisibility,
    val `val`: String
) {
    companion object {
        fun ContentLabelPref.toPostLabel(): PostLabel {
            return PostLabel(
                label = this.label,
                src = this.labelerDid,
                visibility = this.visibility,
                `val` = this.label
            )
        }

        fun LabelValueDefinition.toPostLabel(src: Did): PostLabel {
            return PostLabel(
                label = this.locales.first().name,
                src = src,
                visibility = when (this.defaultSetting) {
                    LabelValueDefinitionDefaultSetting.Warn -> ContentLabelPrefVisibility.Warn
                    LabelValueDefinitionDefaultSetting.Hide -> ContentLabelPrefVisibility.Hide
                    LabelValueDefinitionDefaultSetting.Ignore -> ContentLabelPrefVisibility.Ignore
                    else -> ContentLabelPrefVisibility.Show
                },
                `val` = this.identifier
            )
        }
    }
}

@Composable
fun HiddenContent(
    label: String,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val rotation by animateFloatAsState(
        targetValue = if (visible) 180f else 0f,
        label = "Arrow rotation"
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.shapes.medium
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.shapes.medium
                )
                .clip(
                    MaterialTheme.shapes.medium
                )
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    visible = !visible
                }
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = "Warning",
                    modifier = Modifier.size(
                        20.dp
                    )
                )
                Text(
                    label
                )
            }

            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Arrow down",
                modifier = Modifier.rotate(rotation)
            )
        }

        AnimatedVisibility(
            visible,
            modifier = Modifier.clip(
                MaterialTheme.shapes.medium
            )
        ) {
            content()
        }
    }
}

val LocalSavedPreferences = compositionLocalOf<List<PreferencesUnion>?> {
    throw IllegalStateException("No saved preferences provided")
}

@Composable
fun rememberSavedPreferences(
    api: BlueskyApi?,
    did: String?
): SingleLoadResult<List<PreferencesUnion>> {
    val enabled = api != null && did != null
    return remember(api, did) {
        SingleCache(
            fetcher = {
                if (api == null) throw IllegalStateException("API not initialized")
                else if (did == null) emptyList()
                else api.getPreferencesForActor().requireResponse().preferences
            },
            serializer = ListSerializer(PreferencesUnion.serializer()),
            identifier = "preferences_${did ?: "anonymous"}"
        )
    }.load(enabled = enabled)
}

fun GetServicesResponseViewUnion.getDefinitionOf(labelVal: String): LabelValueDefinition? {
    return when (val label = this) {
        is GetServicesResponseViewUnion.LabelerView -> {
            null
        }

        is GetServicesResponseViewUnion.LabelerViewDetailed -> {
            label.value.policies.labelValueDefinitions?.find { it.identifier == labelVal }
        }

        is GetServicesResponseViewUnion.Unknown -> {
            null
        }
    }
}

fun LabelValue.localizedName(): Int? {
    return when (this) {
        LabelValue.Porn -> R.string.porn
        LabelValue.Nudity -> R.string.nudity
        LabelValue.Sexual -> R.string.sexual
        LabelValue.GraphicMedia -> R.string.graphic_media
        else -> null
    }
}
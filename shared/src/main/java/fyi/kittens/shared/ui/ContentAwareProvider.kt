package fyi.kittens.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import app.bsky.actor.PreferencesUnion
import app.bsky.actor.ProfileViewBasic
import app.bsky.labeler.GetServicesResponseViewUnion
import com.atproto.label.Label

val LocalContentAware = compositionLocalOf<PostLabel?> {
    null
}

@Composable
fun ContentAwareProvider(labels: List<Label>?, labelDefinitions: Map<String, GetServicesResponseViewUnion>, content: @Composable () -> Unit) {
    val preferences = LocalSavedPreferences.current
        ?: throw IllegalStateException("Saved preferences has not been loaded")

    val labelPreferences =
        preferences.filterIsInstance<PreferencesUnion.ContentLabelPref>().map { it.value }

    val authorVisibility = getVisibilityForLabels(
        labels,
        labelPreferences,
        labelDefinitions,
        visibilityPriority
    )

    CompositionLocalProvider(
        LocalContentAware provides authorVisibility
    ) {
        content()
    }
}
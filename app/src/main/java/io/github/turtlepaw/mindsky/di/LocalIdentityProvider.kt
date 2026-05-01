package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.turtlepaw.mindsky.viewmodels.FeedViewModel
import sh.christian.ozone.api.Did

val LocalIdentityProvider = staticCompositionLocalOf<Did?> {
    null
}
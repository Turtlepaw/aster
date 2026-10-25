package fyi.kittens.shared.di

import androidx.compose.runtime.staticCompositionLocalOf
import fyi.kittens.shared.auth.SessionProvider
import sh.christian.ozone.BlueskyApi

val LocalMindskyApi = staticCompositionLocalOf<BlueskyApi> {
    error("BlueskyApi not provided")
}

val LocalSessionProvider = staticCompositionLocalOf<SessionProvider> {
    error("SessionProvider not provided")
}

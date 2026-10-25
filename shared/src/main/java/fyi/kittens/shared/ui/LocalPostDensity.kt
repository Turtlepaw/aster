package fyi.kittens.shared.ui

import androidx.compose.runtime.staticCompositionLocalOf

val LocalPostDensity = staticCompositionLocalOf<PostDensity> {
    error("PostDensity not provided")
}

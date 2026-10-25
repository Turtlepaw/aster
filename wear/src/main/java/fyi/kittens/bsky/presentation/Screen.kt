package fyi.kittens.bsky.presentation

import android.net.Uri

sealed class Screen(val route: String) {
    data object Feed : Screen("feed")
    data object Profile : Screen("profile/{did}") {
        fun createRoute(did: String) = "profile/$did"
    }
    data object PostDetails : Screen("post/{uri}") {
        fun createRoute(uri: String) = "post/${Uri.encode(uri)}"
    }
    data object FeedPicker : Screen("feed_picker")
}

package io.github.turtlepaw.mindsky.di

import androidx.compose.runtime.staticCompositionLocalOf
import app.bsky.notification.GetUnreadCountResponse
import io.github.turtlepaw.fetch_and_cache.SingleLoadResult
import io.github.turtlepaw.mindsky.viewmodels.FeedViewModel

val LocalNotificationCountProvider = staticCompositionLocalOf<SingleLoadResult<GetUnreadCountResponse>> {
    error("LocalNotificationCountProvider not provided")
}

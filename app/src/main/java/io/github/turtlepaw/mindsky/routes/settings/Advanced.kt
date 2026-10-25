package io.github.turtlepaw.mindsky.routes.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.R
import io.github.turtlepaw.mindsky.di.LocalLabelManager
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import io.github.turtlepaw.mindsky.utils.StringComposable
import me.zhanghai.compose.preference.switchPreference
import me.zhanghai.compose.preference.textFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Advanced(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val preferences = LocalPreferences.current
    val baseUrl by rememberPreference(AppPrefs.BaseUrl)
    val enableBlueskyModeration by rememberPreference(AppPrefs.EnableBskyModeration)
    val labelManager = LocalLabelManager.current
    val api = LocalMindskyApi.current

    LaunchedEffect(enableBlueskyModeration) {
        labelManager.revalidateLabelers(api)
    }
    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(navigator, R.string.advanced)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            textFieldPreference(
                key = preferences.getKey(AppPrefs.BaseUrl),
                defaultValue = AppPrefs.BaseUrl.defaultValue,
                title = {
                    R.string.base_url.StringComposable()
                },
                summary = {
                    R.string.base_url_description.StringComposable()
                },
                textToValue = {
                    it
                },
            )

            switchPreference(
                key = preferences.getKey(AppPrefs.DeveloperMode),
                defaultValue = AppPrefs.DeveloperMode.defaultValue,
                title = {
                    R.string.developer_tools.StringComposable()
                },
                summary = {
                    R.string.developer_tools_description.StringComposable()
                }
            )

            switchPreference(
                key = preferences.getKey(AppPrefs.EnableBskyModeration),
                defaultValue = enableBlueskyModeration,
                title = {
                    R.string.enable_bluesky_moderation.StringComposable()
                },
                summary = {
                    R.string.enable_bluesky_moderation_description.StringComposable()
                }
            )
        }
    }
}

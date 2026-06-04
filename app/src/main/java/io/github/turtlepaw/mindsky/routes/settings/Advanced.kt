package io.github.turtlepaw.mindsky.routes.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.preferences.AppPrefs
import io.github.turtlepaw.mindsky.preferences.LocalPreferences
import io.github.turtlepaw.mindsky.preferences.rememberPreference
import io.github.turtlepaw.mindsky.utils.StringComposable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.TextFieldPreference
import me.zhanghai.compose.preference.preference
import me.zhanghai.compose.preference.switchPreference
import me.zhanghai.compose.preference.textFieldPreference

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun Advanced(navigator: DestinationsNavigator) {
    val context = LocalContext.current
    val preferences = LocalPreferences.current
    val baseUrl by rememberPreference(AppPrefs.BaseUrl)

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
                    Text("Developer Mode")
                },
                summary = {
                    Text("Enable developer tools and advanced information")
                }
            )
        }
    }
}

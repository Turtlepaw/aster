package io.github.turtlepaw.mindsky.components.sheets

import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.bsky.actor.GetProfileQueryParams
import app.bsky.actor.ProfileViewDetailed
import fyi.kittens.shared.ui.Avatar
import fyi.kittens.shared.ui.ExpandableContent
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.mindsky.components.ErrorMessage
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.routes.ProfileAvatar
import io.github.turtlepaw.mindsky.routes.settings.toOzoneUri
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import sh.christian.ozone.api.Did
import sh.christian.ozone.api.Uri

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditProfileSheet(state: SheetState, did: String) {
    val api = LocalMindskyApi.current
    val profileData = SingleCache(
        fetcher = {
            return@SingleCache api.getProfile(GetProfileQueryParams(Did(did))).requireResponse()
        },
        serializer = ProfileViewDetailed.serializer(),
        identifier = "profile_data_${did}"
    ).load()

    var displayName by remember { mutableStateOf(profileData.value?.displayName ?: "") }
    var description by remember { mutableStateOf(profileData.value?.description ?: "") }
    var avatar by remember { mutableStateOf<Uri?>(profileData.value?.avatar) }

    val pickVisualMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            avatar = uri?.toString()?.toOzoneUri() ?: profileData.value?.avatar
        }

    Sheet<Any?>(
        state = state,
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { close, data ->
        if (profileData.isLoading) {
            item {
                LoadingIndicator()
            }
        } else if (profileData.error != null) {
            item {
                ErrorMessage(
                    profileData.error?.message ?: "Unknown error",
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .padding(bottom = 12.dp)
                        .clickable{
                            pickVisualMedia.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                ) {
                    Avatar(
                        modifier = Modifier.size(100.dp),
                        avatarUrl = avatar?.uri,
                        contentDescription = "Profile Avatar"
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Spacer(
                    modifier = Modifier.height(10.dp)
                )
            }

            item {
                val coroutineScope = rememberCoroutineScope()
                Button(
                    onClick = {
                        coroutineScope.launch {
                            // save data
                            close {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save")
                }
            }
        }

        dismissButton(close)
    }
}
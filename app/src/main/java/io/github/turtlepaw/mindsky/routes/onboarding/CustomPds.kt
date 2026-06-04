package io.github.turtlepaw.mindsky.routes.onboarding

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.LoginDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.auth.getPdsUrlFromDid
import io.github.turtlepaw.mindsky.auth.resolveHandleToDid
import io.github.turtlepaw.mindsky.routes.settings.TopAppBarCommon
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.takeFrom
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sh.christian.ozone.XrpcBlueskyApi
import java.net.UnknownServiceException // Added import

enum class ConnectionState {
    Connecting,
    Connected,
    Failed,
    Idle
}

@Destination<RootGraph>
@Composable
fun CustomPds(navigator: DestinationsNavigator, isOnboarding: Boolean = true) {
    val viewModel = rememberOnboardingViewModel()

    var input by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(ConnectionState.Idle) }

    val coroutineScope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(input) {
        debounceJob?.cancel()
        if (input.isBlank()) {
            isConnected = ConnectionState.Idle
            return@LaunchedEffect
        }
        debounceJob = coroutineScope.launch {
            // Debounce delay: 500ms after user stops typing
            kotlinx.coroutines.delay(500L)
            isConnected = ConnectionState.Connecting
            try {
                val did = if(input.startsWith("did:")) input
                    else resolveHandleToDid(input)

                val pds = getPdsUrlFromDid(did)
                viewModel.pds = pds
                viewModel.handle = did
                Log.d("CustomPds", "Connected to server: $pds")
                isConnected = ConnectionState.Connected
                return@launch
            } catch (e: Exception) {
                Log.e("CustomPds", "Failed to resolve handle to DID: $input. Details: ${e.message}", e)
            }

            try {
                val httpClient = HttpClient(OkHttp) {
                    install(Logging) {
                        logger = object : Logger {
                            override fun log(message: String) {
                                Log.v("Ktor_Default", message)
                            }
                        }
                        level = LogLevel.HEADERS
                    }
                    defaultRequest {
                        url.takeFrom(
                            if(input.startsWith("http://") || input.startsWith("https://")) {
                                input
                            } else {
                                "https://$input"
                            }
                        )
                    }
                    expectSuccess = true
                }

                val api = XrpcBlueskyApi(httpClient)
                val server = api.describeServer()
                Log.d("CustomPds", "Connected to server: ${server}")
                viewModel.pds = input
                isConnected = ConnectionState.Connected
                return@launch
            } catch (e: UnknownServiceException) {
                isConnected = ConnectionState.Failed
                if (e.message?.contains("CLEARTEXT") == true &&
                    (input.startsWith("http://localhost") || input.startsWith("http://127.0.0.1"))
                ) {
                    Log.w(
                        "CustomPds",
                        "CLEARTEXT communication to localhost is not permitted. PDS: $input. Details: ${e.message}"
                    )
                } else {
                    Log.e(
                        "CustomPds",
                        "Unknown service error connecting to PDS: $input. Details: ${e.message}",
                        e
                    )
                }
            } catch (e: Exception) {
                isConnected = ConnectionState.Failed
                Log.e("CustomPds", "Failed to connect to PDS: $input. Details: ${e.message}", e)
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(
                navigator,
                R.string.login_with_handle,
                MaterialTheme.typography.titleMedium
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
//                        Text(
//                            text = "Enter your handle",
//                            style = MaterialTheme.typography.titleLarge
//                        )
                        Text(
                            text = "You can enter your handle, DID, or the URL of your PDS to login.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it.trim() },
                        label = { Text("Identifier") },
                        placeholder = { Text("example.com") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                enabled = isConnected == ConnectionState.Connected,
                onClick = {
                    when (isConnected) {
                        ConnectionState.Connected -> {
                            navigator.navigate(
                                LoginDestination(viewModel.pds, isOnboarding)
                            )
                        }

                        ConnectionState.Idle, ConnectionState.Failed -> {
                            // Do nothing, just show the current state
                        }

                        ConnectionState.Connecting -> {
                            // Already connecting, do nothing
                        }
                    }
                }
            ) {
                when (isConnected) {
                    ConnectionState.Connecting -> Text("Connecting...")
                    ConnectionState.Connected -> Text("Continue")
                    ConnectionState.Failed -> Text("Can't connect to PDS")
                    ConnectionState.Idle -> Text("Can't connect to PDS")
                }
            }
        }
    }
}

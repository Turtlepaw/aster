package io.github.turtlepaw.mindsky.routes.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.DownloadModelDestination
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.FeedDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.auth.AuthState
import io.github.turtlepaw.mindsky.MindskyApplication
import fyi.kittens.shared.ui.Avatar
import io.github.turtlepaw.mindsky.logic.DownloadStage
import io.github.turtlepaw.mindsky.logic.ModelDownloadWorker
import io.github.turtlepaw.mindsky.routes.settings.TopAppBarCommon
import io.github.turtlepaw.mindsky.viewmodels.rememberAuthViewModel
import java.io.File

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun Login(navigator: DestinationsNavigator, pds: String, isOnboarding: Boolean = true) {
    val viewModel = rememberOnboardingViewModel()
    val context = LocalContext.current

    val authViewModel = rememberAuthViewModel()
    val authState by authViewModel.loginState.collectAsState()
    val isLoading = authState == AuthState.Loading
    val errorMessage = (authState as? AuthState.Error)?.message

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            val session = (authState as AuthState.Authenticated).session
            val application = context.applicationContext as MindskyApplication
            application.configureAuthenticatedApi(session)

            val model = File(context.filesDir, ModelDownloadWorker.getOutputFilename(DownloadStage.Model))
            val tokenizer = File(context.filesDir, ModelDownloadWorker.getOutputFilename(DownloadStage.Tokenizer))
            val isPresent = model.exists() && tokenizer.exists()
            if (isOnboarding && !isPresent) {
                navigator.navigate(DownloadModelDestination) {
                    popUpTo(NavGraphs.root) {
                        inclusive = true
                    }
                }
            } else {
                navigator.navigate(FeedDestination) {
                    popUpTo(NavGraphs.root) {
                        inclusive = true
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.resetLoginState()
        authViewModel.startOAuthFlow(pds)
    }

    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(
                navigator,
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if(isLoading) {
                CircularWavyProgressIndicator()
                Spacer(
                    modifier = Modifier.height(30.dp)
                )
            }

            Text(
                if(isLoading) "Logging into" else "Login to",
                style = MaterialTheme.typography.titleSmall
            )

            val pds = Pds.All.find { it.url == viewModel.pds }
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
            ) {
                if (pds != null && pds.iconUrl != null) {
                    Avatar(
                        modifier = Modifier.size(35.dp),
                        pds.iconUrl,
                        "${pds.name} icon",
                        clip = MaterialTheme.shapes.medium
                    )
                }

                Column(
                    horizontalAlignment = if (pds == null) Alignment.CenterHorizontally else Alignment.Start,
                ) {
                    Text(
                        pds?.name ?: pds?.url ?: viewModel.pds,
                        style = MaterialTheme.typography.titleLarge
                    )
//                    Text(
//                        pds?.url ?: viewModel.pds,
//                        style = MaterialTheme.typography.bodySmall.copy(
//                            color = MaterialTheme.colorScheme.onSurfaceVariant
//                        )
//                    )
                }
            }

            //Spacer(modifier = Modifier.height(16.dp))

//            Row(
//                horizontalArrangement = Arrangement.spacedBy(6.dp),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                Icon(
//                    Icons.Rounded.Public,
//                    "Globe",
//                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
//                    modifier = Modifier.size(MaterialTheme.typography.bodySmall.toDp() + 2.dp)
//                )
//
//            }
            errorMessage?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

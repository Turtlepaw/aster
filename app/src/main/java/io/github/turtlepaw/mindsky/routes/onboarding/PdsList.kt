package io.github.turtlepaw.mindsky.routes.onboarding

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.BookmarksDestination
import com.ramcosta.composedestinations.generated.destinations.CustomPdsDestination
import com.ramcosta.composedestinations.generated.destinations.FeedDestination
import com.ramcosta.composedestinations.generated.destinations.LoginDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.auth.AuthState
import fyi.kittens.shared.R
import fyi.kittens.shared.ui.Avatar
import io.github.turtlepaw.mindsky.replaceCurrent
import io.github.turtlepaw.mindsky.routes.settings.TopAppBarCommon
import io.github.turtlepaw.mindsky.viewmodels.rememberAuthViewModel

data class Pds(
    val iconUrl: String?,
    val name: String,
    val description: String,
    val url: String,
    /**
     * if the account is verified by @bsky.app
     */
    val verified: Boolean = false
) {
    companion object {
        val BlueskyPbc = Pds(
            iconUrl = "https://cdn.bsky.app/img/avatar/plain/did:plc:z72i7hdynmk6r22z27h6tvur/bafkreihwihm6kpd6zuwhhlro75p5qks5qtrcu55jp3gddbfjsieiv7wuka",
            name = "Bluesky PBC",
            description = "The official hosting service provided by Bluesky PBC.",
            url = "https://bsky.social",
        )

        val WitchcraftSystems = Pds(
            iconUrl = "https://cdn.bsky.app/img/avatar/plain/did:web:witchcraft.systems/bafkreihxuizk4vku4wkmexc5hcemp2huwt5qpdx7p2r3ejnygg4plglkwu@jpeg",
            name = "witchcraft.systems",
            description = "witches with ethernet switches",
            url = "https://pds.witchcraft.systems"
        )

        val SelfHostedSocial = Pds(
            iconUrl = null,
            name = "selfhosted.social",
            description = "Hosting service run by @baileytownsend.dev",
            url = "https://selfhosted.social"
        )

        val CustomPds = Pds(
            iconUrl = null,
            name = "Login with your handle",
            description = "Login with your internet handle",
            url = ""
        )

        val All = listOf(BlueskyPbc, WitchcraftSystems, SelfHostedSocial, CustomPds)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Destination<RootGraph>
@Composable
fun PdsList(navigator: DestinationsNavigator, isOnboarding: Boolean = true) {
    val authViewModel = rememberAuthViewModel()
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated && isOnboarding) {
            navigator.replaceCurrent(FeedDestination)
        }
    }
    
    val viewModel = rememberOnboardingViewModel()
    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(
                navigator,
                R.string.sign_in_with_provider,
                MaterialTheme.typography.titleMedium
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            itemsIndexed(Pds.All) { index, pds ->
                PdsItem(
                    pds = pds,
                    index = index,
                    listSize = Pds.All.size
                ) {
                    viewModel.pds = pds.url
                    Log.d("PdsList", "Selected PDS: ${pds.url}")
                    navigator.navigate(
                        if (pds.url.isBlank())
                            CustomPdsDestination(isOnboarding)
                        else LoginDestination(pds.url, isOnboarding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PdsItem(pds: Pds, index: Int, listSize: Int, onClick: () -> Unit) {
    val topRadius = if (listSize == 1 || index == 0) 20.dp else 10.dp
    val bottomRadius = if (listSize == 1 || index == listSize - 1) 20.dp else 10.dp

    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = listSize),
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    4.dp,
                    alignment = Alignment.Start
                ),
            ) {
                Text(
                    pds.name,
                    style = MaterialTheme.typography.titleMedium,
                    overflow = TextOverflow.Ellipsis
                )

                if (pds.verified) {
                    Icon(
                        painterResource(R.drawable.check_circle),
                        contentDescription = "Verified",
                        modifier = Modifier
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        supportingContent = {
            Text(
                pds.description,
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            if (pds.iconUrl != null) {
                Avatar(
                    modifier = Modifier.size(50.dp),
                    pds.iconUrl,
                    "${pds.name} icon",
                    clip = MaterialTheme.shapes.large
                )
            } else {
                when (pds){
                    Pds.SelfHostedSocial -> Avatar(
                        modifier = Modifier.size(50.dp),
                        Icons.Rounded.LocalCafe,
                        contentDescription = "Local Cafe",
                        clip = MaterialTheme.shapes.large
                    )
                    Pds.CustomPds -> Icon(
                        Icons.Rounded.AlternateEmail,
                        contentDescription = "At sign",
                        modifier = Modifier.size(50.dp).padding(10.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Chevron Right",
            )
        }
    )
}

@Composable
fun TextStyle.toDp(): Dp {
    val density = LocalDensity.current
    return with(density) { fontSize.toDp() }
}

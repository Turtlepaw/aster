package io.github.turtlepaw.mindsky.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bsky.actor.ProfileView
import app.bsky.feed.GetLikesQueryParams
import app.bsky.feed.GetRepostedByQueryParams
import app.bsky.graph.GetKnownFollowersQueryParams
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination.invoke
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.Cache
import io.github.turtlepaw.mindsky.R
import io.github.turtlepaw.mindsky.components.Avatar
import io.github.turtlepaw.mindsky.components.ProfileExpanded
import io.github.turtlepaw.mindsky.di.LocalMindskyApi
import io.github.turtlepaw.mindsky.routes.settings.TopAppBarCommon
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.Did

@Serializable
sealed class UserListType {
    @Serializable
    data class Likes(val uri: String) : UserListType()

    @Serializable
    data class Reposts(val uri: String) : UserListType()
    data class FollowedBy(val actor: String) : UserListType()
}

@Destination<RootGraph>
@Composable
fun UserList(
    navigator: DestinationsNavigator,
    identifier: String,
    type: UserListType
) {
    val context = LocalContext.current
    val api = LocalMindskyApi.current
    val data = Cache<Any?, List<ProfileView>>(
        fetcher = {
            when (type) {
                is UserListType.Likes -> {
                    api.getLikes(
                        GetLikesQueryParams(
                            uri = AtUri(type.uri),
                            limit = 50L
                        )
                    ).requireResponse().likes.map { it.actor }
                }

                is UserListType.Reposts -> {
                    api.getRepostedBy(
                        GetRepostedByQueryParams(
                            uri = AtUri(type.uri),
                            limit = 50L
                        )
                    ).requireResponse().repostedBy
                }

                is UserListType.FollowedBy -> {
                    api.getKnownFollowers(
                        GetKnownFollowersQueryParams(
                            actor = Did(type.actor),
                            limit = 50,

                            )
                    ).requireResponse().followers
                }
            }
        },
        serializer = ListSerializer(ProfileView.serializer()),
        identifier = identifier,
        context = context
    ).load(Unit)

    Scaffold(
        topBar = {
            TopAppBarCommon.withBack(
                navigator,
                title = when (type) {
                    is UserListType.Likes -> R.string.likes
                    is UserListType.Reposts -> R.string.reposts
                    is UserListType.FollowedBy -> R.string.followers_known
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding
        ) {
            if(data.isLoading){
                Loading()
            } else if(data.value != null) {
                items(data.value!!){ actor ->
                    ProfileExpanded(actor) {
                        navigator.navigate(
                            ProfileDestination(
                                actor.did.did
                            )
                        )
                    }
                }
            }
        }
    }
}

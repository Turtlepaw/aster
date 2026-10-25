package io.github.turtlepaw.mindsky.routes

import android.util.Log
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.bsky.actor.ProfileView
import app.bsky.feed.GetLikesQueryParams
import app.bsky.feed.GetRepostedByQueryParams
import app.bsky.graph.GetFollowersQueryParams
import app.bsky.graph.GetFollowsQueryParams
import app.bsky.graph.GetKnownFollowersQueryParams
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ProfileDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import io.github.turtlepaw.fetch_and_cache.Cache
import fyi.kittens.shared.R
import io.github.turtlepaw.fetch_and_cache.SingleCache
import io.github.turtlepaw.mindsky.components.ErrorMessage
import io.github.turtlepaw.mindsky.components.GenericTopBar
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
    @Serializable
    data class KnownFollowers(val actor: String) : UserListType()
    @Serializable
    data class Followers(val actor: String) : UserListType()
    @Serializable
    data class Following(val actor: String) : UserListType()
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
    val data = SingleCache<List<ProfileView>>(
        fetcher = {
            val result = when (type) {
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

                is UserListType.KnownFollowers -> {
                    api.getKnownFollowers(
                        GetKnownFollowersQueryParams(
                            actor = Did(type.actor),
                            limit = 50,
                        )
                    ).requireResponse().followers
                }

                is UserListType.Followers -> {
                    api.getFollowers(
                        GetFollowersQueryParams(
                            actor = Did(type.actor),
                            limit = 50,
                        )
                    ).requireResponse().followers
                }

                is UserListType.Following -> {
                    api.getFollows(
                        GetFollowsQueryParams(
                            actor = Did(type.actor),
                            limit = 50,
                        )
                    ).requireResponse().follows
                }

                else -> throw IllegalArgumentException("Unknown type: $type")
            }

            Log.d("UserList", "Fetched ${result.size} items")

            return@SingleCache result
        },
        serializer = ListSerializer(ProfileView.serializer()),
        identifier = "${identifier}_$type",
    ).load()

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            GenericTopBar(
                title = stringResource(
                    when (type) {
                        is UserListType.Likes -> R.string.likes
                        is UserListType.Reposts -> R.string.reposts
                        is UserListType.KnownFollowers -> R.string.followers_known
                        is UserListType.Followers -> R.string.followers
                        is UserListType.Following -> R.string.following
                    }
                ),
                listState = null,
                navigator = navigator
            )
        }
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding,
            state = listState
        ) {
            if (data.isLoading) {
                Loading()
            } else if (data.value != null) {
                items(data.value!!) { actor ->
                    ProfileExpanded(actor) {
                        navigator.navigate(
                            ProfileDestination(
                                actor.did.did
                            )
                        )
                    }
                }
            } else if (data.error != null) {
                item {
                    ErrorMessage(
                        data.error.toString()
                    )
                }
            } else {
                item {
                    ErrorMessage(R.string.no_data){
                        Icon(
                            imageVector = Icons.Rounded.QuestionMark,
                            contentDescription = "Question mark"
                        )
                    }
                }
            }
        }
    }
}

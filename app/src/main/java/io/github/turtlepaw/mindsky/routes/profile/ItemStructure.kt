package io.github.turtlepaw.mindsky.routes.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fyi.kittens.shared.ui.rememberLoadingColor

@Composable
fun ItemStructure(
    icon: @Composable (Modifier) -> Unit,
    title: @Composable (Modifier) -> Unit,
    subtitle: @Composable (Modifier) -> Unit = {},
    trailing: @Composable () -> Unit = {}
){
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                16.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        icon(
            Modifier
                .size(40.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            title(
                Modifier
            )
            subtitle(
                Modifier
            )
        }
        trailing()
    }
}

@Composable
fun LoadingListItem(){
    val loadingColor = rememberLoadingColor()

    ItemStructure(
        icon = {
            Box(
                modifier = it
                    .background(
                        loadingColor,
                        MaterialTheme.shapes.small
                    )
            )
        },
        title = {
            Box(
                modifier = Modifier
                    .size(100.dp, 16.dp)
                    .background(
                        loadingColor,
                        MaterialTheme.shapes.small
                    )
            )
        },
        subtitle = {
            Box(
                modifier = Modifier
                    .size(200.dp, 16.dp)
                    .background(
                        loadingColor,
                        MaterialTheme.shapes.small
                    )
            )
        },
        trailing = {}
    )
}
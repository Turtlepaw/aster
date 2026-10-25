package io.github.turtlepaw.mindsky.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fyi.kittens.shared.R

@Composable
fun ErrorMessage(content: Int = R.string.failed_to_load, icon: @Composable () -> Unit = {
    Icon(
        imageVector = Icons.Rounded.Warning,
        contentDescription = "Warning"
    )
}) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon()
        Text(
            stringResource(content)
        )
    }
}

@Composable
fun ErrorMessage(content: String, icon: @Composable () -> Unit = {
    Icon(
        imageVector = Icons.Rounded.Warning,
        contentDescription = "Warning"
    )
}) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        icon()
        Text(
            text = content
        )
    }
}

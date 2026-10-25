package io.github.turtlepaw.mindsky.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import fyi.kittens.shared.R
import io.github.turtlepaw.mindsky.routes.settings.TopAppBarCommon
import kotlinx.coroutines.launch

@Composable
fun TopBarBackground(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface,
                        //MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
fun TopBarInteractiveElements(
    listState: LazyListState,
    onIconClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.75f else 1f,
        label = "Scale Animation"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            Icon(
                painter = painterResource(R.drawable.ic_mindsky),
                contentDescription = "Mindsky icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(30.dp)
                    .scale(scale)
                    .animateContentSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Scroll to top first
                        scope.launch {
                            listState.animateScrollToItem(0)
                        }
                        // Then invoke the click handler from Feed.kt
                        onIconClick()
                    },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericTopBar(
    title: String,
    icon: @Composable RowScope.() -> Unit = {},
    listState: LazyListState?,
    navigator: DestinationsNavigator? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val shouldShow by remember(listState) {
        derivedStateOf {
            if (listState == null) true
            else (listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 150)
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (shouldShow) 1f else 0f,
        label = "topBarAlpha",
        animationSpec = tween(300)
    )


    Box {
        Box(
            modifier = Modifier
                .alpha(alpha)
                .fillMaxWidth()
                .height(130.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
                .zIndex(1f)
        )

        TopAppBar(
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    icon()
                    Text(
                        text = title,
                    )
                }
            },
            navigationIcon = {
                if (navigator != null) {
                    TopAppBarCommon.BackButton(navigator)
                }
            },
            actions = {
                if (listState != null && shouldShow) {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = "Scroll to top"
                        )
                    }
                }
                actions()
            },
            modifier = Modifier.zIndex(10f),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
    }
}

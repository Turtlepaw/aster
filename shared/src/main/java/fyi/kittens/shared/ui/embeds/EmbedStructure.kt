package fyi.kittens.shared.ui.embeds

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class EmbedStructureStyle(
    val border: BorderStroke?,
    val colors: CardColors?
){
    companion object {
        @Composable
        fun createStyle(border: Color, containerColor: Color): EmbedStructureStyle {
            return EmbedStructureStyle(
                colors = CardDefaults.outlinedCardColors(
                    containerColor = containerColor
                ),
                border = BorderStroke(0.5.dp, border)
            )
        }
    }
}

val LocalEmbedStructureStyle = compositionLocalOf<EmbedStructureStyle> {
    EmbedStructureStyle(
        border = null,
        colors = null
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmbedStructure(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable (ColumnScope.() -> Unit),
) {
    val style = LocalEmbedStructureStyle.current
    if (onLongClick != null) {
        OutlinedCard(
            modifier = modifier
                .padding(vertical = 8.dp)
                .animateContentSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    enabled = enabled
                ),
            border = (style.border ?: CardDefaults.outlinedCardBorder()).copy(width = 0.5.dp),
            colors = style.colors ?: CardDefaults.outlinedCardColors(),
            content = content
        )
    } else {
        OutlinedCard(
            modifier = modifier
                .padding(vertical = 8.dp)
                .animateContentSize(),
            border = (style.border ?: CardDefaults.outlinedCardBorder()).copy(width = 0.5.dp),
            colors = style.colors ?: CardDefaults.outlinedCardColors(),
            onClick = onClick,
            enabled = enabled,
            content = content
        )
    }
}


package io.github.turtlepaw.mindsky.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fyi.kittens.shared.R
import kotlinx.coroutines.launch

class SheetState(
    val isOpen: Boolean,
    val onDismiss: () -> Unit,
    val onOpen: () -> Unit,
)

@Composable
fun rememberSheetState(initialState: Boolean = false): SheetState {
    var isOpen by remember { mutableStateOf(initialState) }
    return SheetState(
        isOpen = isOpen,
        onDismiss = { isOpen = false },
        onOpen = { isOpen = true }
    )
}

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun <T> Sheet(
    state: SheetState,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    loadData: @Composable (() -> T) = { null as T },
    content: LazyListScope.(closeSheet: (onCompletion: (() -> Unit)?) -> Unit, data: T) -> Unit
) {
    Sheet(
        isOpen = state.isOpen,
        onDismiss = state.onDismiss,
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        loadData = loadData,
        content = content
    )
}

@OptIn(
    ExperimentalMaterial3Api::class
)
@Composable
fun <T> Sheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    loadData: @Composable (() -> T) = { null as T },
    content: LazyListScope.(closeSheet: (onCompletion: (() -> Unit)?) -> Unit, data: T) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val closeSheet: (onCompletion: (() -> Unit)?) -> Unit = { onCompletion ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            onCompletion?.invoke()
        }
    }

    if (isOpen) {
        val data = loadData()
        ModalBottomSheet(
            onDismissRequest = {
                onDismiss()
            },
            sheetState = sheetState,
        ) {
            LazyColumn(
                modifier = modifier,
                verticalArrangement = verticalArrangement,
            ) {
                content(closeSheet, data)
            }
        }
    }
}

fun LazyListScope.dismissButton(
    onDismiss: (onCompletion: (() -> Unit)?) -> Unit
) {
    item {
        OutlinedButton(
            onClick = {
                onDismiss {}
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.close)
            )
        }
    }
}
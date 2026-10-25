package io.github.turtlepaw.mindsky.components.sheets

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fyi.kittens.shared.ui.ExpandableContent
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun CodeSheet(state: SheetState, code: String) {
    Sheet<Any?>(
        state = state,
        modifier = Modifier.padding(12.dp)
    ) { close, data ->
        item {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.shapes.medium
                    )
                    .padding(12.dp)
            ) {
                ExpandableContent(
                    collapsedHeight = 200.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        code,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        item {
            val clipboard = LocalClipboard.current
            val coroutineScope = rememberCoroutineScope()
            Button(
                onClick = {
                    coroutineScope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(
                                ClipData.newPlainText("Code", code)
                            )
                        )
                        close {}
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy to clipboard")
            }
        }
        dismissButton(close)
    }
}

@Preview
@Composable
fun CodeSheetPreview() {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                MaterialTheme.shapes.medium
            )
            .padding(12.dp)
    ) {
        Text(
            "fun CodeSheet(state: SheetState, code: String) {\n" +
                    "    Sheet<Any>(\n" +
                    "        state = state\n" +
                    "    ) { close, data ->\n" +
                    "        item {\n" +
                    "            Box {\n" +
                    "                Text(code)\n" +
                    "            }\n" +
                    "        }\n" +
                    "    }\n" +
                    "}",
            fontFamily = FontFamily.Monospace
        )
    }
}

inline fun <reified T> T.toPrettyJson(): String {
    val json = Json { prettyPrint = true }
    return json.encodeToString(this)
}
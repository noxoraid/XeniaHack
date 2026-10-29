package com.uplink.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val calcKeys = listOf(
    listOf("C", "(", ")", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "-"),
    listOf("1", "2", "3", "+"),
    listOf("0", ".", "⌫", "="),
)

@Composable
fun QuickCalculatorPane() {
    var expr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    fun press(key: String) {
        when (key) {
            "C" -> {
                expr = ""
                result = ""
            }
            "⌫" -> expr = expr.dropLast(1)
            "=" -> runCatching { CalcEngine.format(CalcEngine.eval(expr)) }
                .onSuccess {
                    expr = it
                    result = it
                }
                .onFailure { result = tr("Error") }
            "×" -> expr += "*"
            "÷" -> expr += "/"
            else -> expr += key
        }
    }

    NxSection(tr("Calculator"))
    NxCard(Modifier.fillMaxWidth()) {
        Text(expr.ifEmpty { "0" }.replace("*", "×").replace("/", "÷"), color = NxText)
        Text(result, color = NxDim)
        calcKeys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    NxButton(
                        key,
                        modifier = Modifier.weight(1f),
                        tone = if (key == "=") Tone.Accent else Tone.Quiet,
                    ) { press(key) }
                }
            }
        }
    }
}

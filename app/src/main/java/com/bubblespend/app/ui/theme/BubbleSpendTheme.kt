package com.bubblespend.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BubbleSpendColorScheme = lightColorScheme(background = Color(0xFFFFFBFE))

@Composable
fun BubbleSpendTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BubbleSpendColorScheme,
        content = content,
    )
}

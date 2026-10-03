package com.bubblespend.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.bubblespend.app.ui.BubbleSpendApp
import com.bubblespend.app.ui.theme.BubbleSpendTheme

/** Android entry point and explicit application composition root. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BubbleSpendTheme {
                BubbleSpendApp()
            }
        }
    }
}

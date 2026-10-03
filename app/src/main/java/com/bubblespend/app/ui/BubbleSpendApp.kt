package com.bubblespend.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bubblespend.app.R

private const val HOME_ROUTE = "home"

@Composable
fun BubbleSpendApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            HomeScreen()
        }
    }
}

@Composable
private fun HomeScreen() {
    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("home_surface"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag("home_route"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_title),
                modifier = Modifier.semantics { heading() },
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

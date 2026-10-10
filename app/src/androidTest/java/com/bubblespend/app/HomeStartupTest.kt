package com.bubblespend.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercises the real launch activity and its first visible destination. */
class HomeStartupTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun awaitLaunch() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("home_surface").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() &&
                composeRule.onAllNodesWithText("Loading").fetchSemanticsNodes().isEmpty()
        }
        if (composeRule.onAllNodesWithText("Browse app").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Browse app").performClick()
        }
    }

    @Test
    fun appLaunch_opensHomeRoute_withAccessibleHeading() {
        composeRule.onNodeWithTag("home_route").assertIsDisplayed()
        composeRule
            .onNodeWithText("Home")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }

    @Test
    fun homeSurface_usesConfiguredLightThemeBackground() {
        val pixel = composeRule.onNodeWithTag("home_surface").captureToImage().toPixelMap()[0, 0]

        assertEquals(Color(0xFFFFFBFE), pixel)
    }
}

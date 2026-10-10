package com.bubblespend.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Coral = Color(0xFFFF634F)
val Ink = Color(0xFF111629)
val MutedInk = Color(0xFF535B6B)
val Paper = Color(0xFFFFFBFE)
val Peach = Color(0xFFFFD4C9)
val Lavender = Color(0xFFE3D5FF)
val Mint = Color(0xFFBDEBEC)
val Sky = Color(0xFFCEE4FF)
val Butter = Color(0xFFFFE4AE)

private val BubbleSpendColorScheme =
    lightColorScheme(
        primary = Color(0xFFBB3E2F),
        onPrimary = Color.White,
        primaryContainer = Peach,
        onPrimaryContainer = Ink,
        secondary = Color(0xFF7654AC),
        secondaryContainer = Lavender,
        onSecondaryContainer = Ink,
        tertiary = Color(0xFF227D83),
        tertiaryContainer = Mint,
        background = Paper,
        onBackground = Ink,
        surface = Color.White,
        onSurface = Ink,
        surfaceVariant = Color(0xFFF4F0EF),
        onSurfaceVariant = MutedInk,
        outline = Color(0xFF8F8B95),
        outlineVariant = Color(0xFFE7E2E2),
        error = Color(0xFFB3261E),
    )

private val BubbleSpendTypography =
    Typography(
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 38.sp,
                lineHeight = 44.sp,
                letterSpacing = (-1).sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                letterSpacing = (-0.8).sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.6).sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
            ),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    )

@Composable
fun BubbleSpendTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BubbleSpendColorScheme,
        typography = BubbleSpendTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}

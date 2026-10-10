package com.bubblespend.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bubblespend.app.R
import com.bubblespend.app.ui.theme.Ink
import com.bubblespend.app.ui.theme.MutedInk
import com.bubblespend.core.domain.CurrencyCode
import java.math.BigDecimal
import java.math.BigInteger
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

internal enum class BubbleGlyph { HOME, GRID, CARD, SETTINGS, PLUS, CLOSE, FOOD, BAG, TRAVEL, BILL, WALLET, CHEVRON }

/** Code-native icons: no bitmap assets or remote fonts are needed. */
@Composable
internal fun BubbleIcon(
    glyph: BubbleGlyph,
    color: Color = Ink,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        withTransform({ scale(this@Canvas.size.width / 24f, this@Canvas.size.height / 24f, pivot = Offset.Zero) }) {
            fun line(
                x1: Float,
                y1: Float,
                x2: Float,
                y2: Float,
            ) = drawLine(color, Offset(x1, y1), Offset(x2, y2), 1.8f, StrokeCap.Round)

            fun rectangle(
                x: Float,
                y: Float,
                width: Float,
                height: Float,
            ) = drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(2f), style = Stroke(1.8f))
            when (glyph) {
                BubbleGlyph.HOME -> {
                    val roof =
                        Path().apply {
                            moveTo(3f, 11f)
                            lineTo(12f, 3f)
                            lineTo(21f, 11f)
                        }
                    drawPath(roof, color, style = Stroke(2f, cap = StrokeCap.Round))
                    rectangle(6f, 10f, 12f, 11f)
                    line(12f, 15f, 12f, 20f)
                }
                BubbleGlyph.GRID -> {
                    rectangle(3f, 3f, 7f, 7f)
                    rectangle(14f, 3f, 7f, 7f)
                    rectangle(3f, 14f, 7f, 7f)
                    rectangle(14f, 14f, 7f, 7f)
                }
                BubbleGlyph.CARD -> {
                    rectangle(2f, 5f, 20f, 14f)
                    line(3f, 10f, 21f, 10f)
                    line(6f, 15f, 10f, 15f)
                }
                BubbleGlyph.SETTINGS -> {
                    drawCircle(color, 6f, Offset(12f, 12f), style = Stroke(1.8f))
                    drawCircle(color, 2f, Offset(12f, 12f), style = Stroke(1.8f))
                    for (i in 0..7) {
                        val angle = Math.PI * i / 4
                        line(
                            (12 + 7 * kotlin.math.cos(angle)).toFloat(),
                            (12 + 7 * kotlin.math.sin(angle)).toFloat(),
                            (
                                12 +
                                    9 * kotlin.math.cos(angle)
                            ).toFloat(),
                            (12 + 9 * kotlin.math.sin(angle)).toFloat(),
                        )
                    }
                }
                BubbleGlyph.PLUS -> {
                    line(12f, 4f, 12f, 20f)
                    line(4f, 12f, 20f, 12f)
                }
                BubbleGlyph.CLOSE -> {
                    line(6f, 6f, 18f, 18f)
                    line(18f, 6f, 6f, 18f)
                }
                BubbleGlyph.FOOD -> {
                    line(5f, 3f, 5f, 10f)
                    line(8f, 3f, 8f, 20f)
                    line(11f, 3f, 11f, 10f)
                    line(5f, 10f, 11f, 10f)
                    line(18f, 3f, 18f, 20f)
                    line(16f, 4f, 16f, 11f)
                    line(16f, 11f, 18f, 11f)
                }
                BubbleGlyph.BAG -> {
                    rectangle(4f, 7f, 16f, 14f)
                    drawArc(color, 180f, 180f, false, Offset(8f, 2f), Size(8f, 10f), style = Stroke(1.8f))
                }
                BubbleGlyph.TRAVEL -> {
                    val plane =
                        Path().apply {
                            moveTo(11f, 3f)
                            lineTo(13f, 3f)
                            lineTo(14f, 10f)
                            lineTo(21f, 14f)
                            lineTo(21f, 16f)
                            lineTo(14f, 14f)
                            lineTo(14f, 19f)
                            lineTo(17f, 21f)
                            lineTo(7f, 21f)
                            lineTo(10f, 19f)
                            lineTo(10f, 14f)
                            lineTo(3f, 16f)
                            lineTo(3f, 14f)
                            lineTo(10f, 10f)
                            close()
                        }
                    drawPath(plane, color, style = Stroke(1.5f))
                }
                BubbleGlyph.BILL -> {
                    rectangle(5f, 3f, 14f, 18f)
                    line(9f, 8f, 15f, 8f)
                    line(9f, 12f, 15f, 12f)
                    line(9f, 16f, 13f, 16f)
                }
                BubbleGlyph.WALLET -> {
                    rectangle(3f, 5f, 18f, 15f)
                    rectangle(14f, 10f, 8f, 6f)
                    drawCircle(color, 1f, Offset(17f, 13f))
                }
                BubbleGlyph.CHEVRON -> {
                    line(9f, 6f, 15f, 12f)
                    line(15f, 12f, 9f, 18f)
                }
            }
        }
    }
}

@Composable
internal fun BrandHeader(
    onAction: () -> Unit,
    close: Boolean = false,
) {
    val description = stringResource(if (close) R.string.close else R.string.settings_title)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.app_name),
            modifier = Modifier.weight(1f),
            color = Color(0xFFE85240),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onAction, modifier = Modifier.semantics { contentDescription = description }) {
            BubbleIcon(if (close) BubbleGlyph.CLOSE else BubbleGlyph.SETTINGS, color = MutedInk)
        }
    }
}

internal fun formatAmount(
    minorUnits: BigInteger,
    currency: CurrencyCode,
): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    formatter.currency = Currency.getInstance(currency.value)
    formatter.minimumFractionDigits =
        if (minorUnits.remainder(BigInteger.TEN.pow(currency.fractionDigits)) == BigInteger.ZERO) 0 else currency.fractionDigits
    formatter.maximumFractionDigits = currency.fractionDigits
    return formatter.format(BigDecimal(minorUnits, currency.fractionDigits))
}

internal fun categoryGlyph(name: String): BubbleGlyph =
    when (name.lowercase(Locale.ROOT)) {
        "food" -> BubbleGlyph.FOOD
        "shopping" -> BubbleGlyph.BAG
        "travel", "transport" -> BubbleGlyph.TRAVEL
        "bills", "utilities" -> BubbleGlyph.BILL
        "housing" -> BubbleGlyph.HOME
        else -> BubbleGlyph.WALLET
    }

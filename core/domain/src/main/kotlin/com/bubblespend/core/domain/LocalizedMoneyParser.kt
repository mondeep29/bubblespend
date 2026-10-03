package com.bubblespend.core.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.ParsePosition
import java.util.Locale

/** Parses a numeric amount using only the locale and currency supplied by the caller. */
object LocalizedMoneyParser {
    fun parse(
        text: String,
        currencyCode: CurrencyCode,
        locale: Locale,
    ): Money {
        require(currencyCode.fractionDigits >= 0) { "Currency has no minor unit definition" }
        require(text.isNotEmpty()) { "Amount is empty" }

        val format =
            java.text.NumberFormat.getNumberInstance(locale) as? DecimalFormat
                ?: throw IllegalArgumentException("Locale does not provide a decimal number format")
        format.isParseBigDecimal = true
        format.isGroupingUsed = true

        validateGrouping(text, format)

        val position = ParsePosition(0)
        val parsed = format.parse(text, position)
        require(parsed is BigDecimal && position.index == text.length && position.errorIndex < 0) {
            "Amount is not a complete localized number"
        }

        val scaled =
            try {
                parsed.setScale(currencyCode.fractionDigits, RoundingMode.UNNECESSARY)
            } catch (exception: ArithmeticException) {
                throw IllegalArgumentException("Amount has nonzero excess precision", exception)
            }
        val minorUnits =
            scaled
                .movePointRight(currencyCode.fractionDigits)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        return Money(minorUnits, currencyCode)
    }

    private fun validateGrouping(
        text: String,
        format: DecimalFormat,
    ) {
        val symbols: DecimalFormatSymbols = format.decimalFormatSymbols
        val decimalSeparator = symbols.decimalSeparator
        val groupingSeparator = symbols.groupingSeparator
        val negativeSign = symbols.minusSign

        var body = text
        if (body.firstOrNull() == negativeSign || body.firstOrNull() == '-') {
            body = body.drop(1)
        } else if (body.firstOrNull() == '+') {
            body = body.drop(1)
        }
        require(body.isNotEmpty()) { "Amount has no digits" }

        val decimalIndex = body.indexOf(decimalSeparator)
        require(decimalIndex == body.lastIndexOf(decimalSeparator)) { "Amount has multiple decimal separators" }
        val integerPart = if (decimalIndex >= 0) body.substring(0, decimalIndex) else body
        val fractionPart = if (decimalIndex >= 0) body.substring(decimalIndex + 1) else ""
        require(integerPart.isNotEmpty() && integerPart.any(::isDigit)) { "Amount has no integer digits" }
        require(decimalIndex < 0 || fractionPart.isNotEmpty()) { "Amount has no fractional digits" }
        require(fractionPart.all(::isDigit)) { "Amount has invalid fractional digits" }

        if (integerPart.contains(groupingSeparator)) {
            val groups = integerPart.split(groupingSeparator)
            val groupingSize = format.groupingSize
            require(groupingSize > 0 && groups.size > 1) { "Grouping is unsupported" }
            require(groups.first().isNotEmpty() && groups.first().length <= groupingSize && groups.first().all(::isDigit)) {
                "Malformed leading digit group"
            }
            require(groups.drop(1).all { it.length == groupingSize && it.all(::isDigit) }) {
                "Malformed digit grouping"
            }
        } else {
            require(integerPart.all(::isDigit)) { "Amount has invalid integer digits" }
        }
    }

    private fun isDigit(character: Char): Boolean = Character.digit(character, 10) >= 0
}

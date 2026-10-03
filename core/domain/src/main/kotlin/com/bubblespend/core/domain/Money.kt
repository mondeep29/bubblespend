package com.bubblespend.core.domain

import java.util.Currency

/** An ISO 4217 currency supported by the running JDK. */
class CurrencyCode private constructor(
    val value: String,
    val fractionDigits: Int,
) {
    override fun equals(other: Any?): Boolean = other is CurrencyCode && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = value

    companion object {
        fun of(value: String): CurrencyCode {
            require(value == value.uppercase() && value.length == 3) { "Invalid currency code" }
            val currency = Currency.getInstance(value)
            require(currency.currencyCode == value) { "Unsupported currency code" }
            return CurrencyCode(value, currency.defaultFractionDigits)
        }
    }
}

/** Money stored as signed minor units, without imposing expense validation. */
data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode,
) {
    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = Math.addExact(minorUnits, other.minorUnits))
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = Math.subtractExact(minorUnits, other.minorUnits))
    }

    operator fun unaryMinus(): Money = copy(minorUnits = Math.negateExact(minorUnits))

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) { "Money currencies must match" }
    }
}

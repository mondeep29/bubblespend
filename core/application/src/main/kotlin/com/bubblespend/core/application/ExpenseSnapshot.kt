package com.bubblespend.core.application

import com.bubblespend.core.domain.CurrencyCode
import java.math.BigInteger
import java.util.UUID

/** An all-time overview of existing expenses, not a calendar-period report. */
data class ExpenseSnapshot(
    val currency: CurrencyCode,
    val totalMinorUnits: BigInteger,
    val categories: List<CategorySnapshot>,
    val expenses: List<Expense>,
) {
    companion object {
        fun from(
            expenses: List<Expense>,
            currency: CurrencyCode,
        ): ExpenseSnapshot {
            val active = expenses.filter { it.deletedAt == null }
            require(active.all { it.amount.currency == currency && it.amount.minorUnits > 0 }) { "Invalid expense currency or amount" }
            val groups =
                active
                    .groupBy { it.categoryId }
                    .map { (id, records) ->
                        CategorySnapshot(id, records.sumMinorUnits(), records)
                    }.sortedWith(compareByDescending<CategorySnapshot> { it.totalMinorUnits }.thenBy { it.categoryId.toString() })
            return ExpenseSnapshot(currency, active.sumMinorUnits(), groups, active)
        }

        private fun List<Expense>.sumMinorUnits(): BigInteger =
            fold(BigInteger.ZERO) { total, expense ->
                total +
                    BigInteger.valueOf(expense.amount.minorUnits)
            }
    }
}

data class CategorySnapshot(
    val categoryId: UUID,
    val totalMinorUnits: BigInteger,
    val expenses: List<Expense>,
)

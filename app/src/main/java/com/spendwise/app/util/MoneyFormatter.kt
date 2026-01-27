package com.spendwise.app.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToInt

object MoneyFormatter {

    fun format(
        amount: Double,
        currencyCode: String,
        showSign: Boolean = false
    ): String {
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            currency = Currency.getInstance(currencyCode)
            maximumFractionDigits = 0
        }

        val value = format.format(kotlin.math.abs(amount))

        return when {
            showSign && amount > 0 -> "+$value"
            showSign && amount < 0 -> "-$value"
            else -> value
        }
    }

    fun compact(
        amount: Double,
        currencyCode: String
    ): String {
        val abs = kotlin.math.abs(amount)
        val symbol = Currency.getInstance(currencyCode).symbol

        return when {
            abs >= 100_000 ->
                "$symbol${(abs / 100_000).roundToInt()}L"

            abs >= 1_000 ->
                "$symbol${(abs / 1_000).roundToInt()}K"

            else ->
                "$symbol${abs.roundToInt()}"
        }
    }
}

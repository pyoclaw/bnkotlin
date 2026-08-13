package com.brodogfyld.domain.model

import kotlinx.serialization.Serializable

/**
 * An amount of money in minor units (øre for DKK, cents for EUR/USD).
 *
 * Integer minor units avoid floating point rounding errors in pricing. The
 * currency is carried by the Cart/Order, not by each [Money] value, so that a
 * single order has a single currency.
 */
@JvmInline
@Serializable
value class Money(val amountMinor: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(amountMinor + other.amountMinor)

    operator fun minus(other: Money): Money = Money(amountMinor - other.amountMinor)

    operator fun times(multiplier: Int): Money = Money(amountMinor * multiplier)

    fun isZero(): Boolean = amountMinor == 0L

    fun isNegative(): Boolean = amountMinor < 0L

    /** Formats a non-negative amount, e.g. `Money(1250).format(Currency.DKK) == "12,50 kr"`. */
    fun format(currency: Currency = Currency.DKK): String {
        val major = amountMinor / 100
        val minor = (amountMinor % 100).let { if (it < 0) -it else it }
        val sign = if (amountMinor < 0 && major == 0L) "-" else ""
        val minorText = minor.toString().padStart(2, '0')
        return "$sign$major,$minorText ${currency.symbol}"
    }

    override fun compareTo(other: Money): Int = amountMinor.compareTo(other.amountMinor)

    override fun toString(): String = "$amountMinor"

    companion object {
        val ZERO = Money(0L)

        fun of(major: Long, minor: Int): Money = Money(major * 100 + minor)
    }
}

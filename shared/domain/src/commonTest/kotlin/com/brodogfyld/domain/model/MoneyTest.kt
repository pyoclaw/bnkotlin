package com.brodogfyld.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTest {

    @Test
    fun performsArithmeticInMinorUnits() {
        val a = Money(1250)
        val b = Money(500)
        assertEquals(Money(1750), a + b)
        assertEquals(Money(750), a - b)
        assertEquals(Money(2500), a * 2)
    }

    @Test
    fun formatsDanishKroner() {
        assertEquals("12,50 kr", Money(1250).format(Currency.DKK))
        assertEquals("0,05 kr", Money(5).format(Currency.DKK))
        assertEquals("99,00 kr", Money(9900).format(Currency.DKK))
    }

    @Test
    fun buildsFromMajorAndMinor() {
        assertEquals(Money(1234), Money.of(12, 34))
        assertEquals(Money.ZERO, Money.of(0, 0))
    }

    @Test
    fun reportsSigns() {
        assertMoneySigns(Money(0), isZero = true, isNegative = false)
        assertMoneySigns(Money(1), isZero = false, isNegative = false)
        assertMoneySigns(Money(-1), isZero = false, isNegative = true)
    }

    private fun assertMoneySigns(money: Money, isZero: Boolean, isNegative: Boolean) {
        assertEquals(isZero, money.isZero())
        assertEquals(isNegative, money.isNegative())
    }
}

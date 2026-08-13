package com.brodogfyld.domain.pricing

import com.brodogfyld.domain.model.Cart
import com.brodogfyld.domain.model.CartItem
import com.brodogfyld.domain.model.Menu
import com.brodogfyld.domain.model.MenuCategory
import com.brodogfyld.domain.model.ModifierGroup
import com.brodogfyld.domain.model.ModifierOption
import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.model.Product
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PricingTest {

    private val ryeBread = ModifierOption("bread-rye", "Rye bread", Money.ZERO)
    private val whiteBread = ModifierOption("bread-white", "White bread", Money(300))
    private val breadGroup = ModifierGroup(
        id = "bread",
        name = "Bread",
        required = true,
        minSelection = 1,
        maxSelection = 1,
        options = listOf(ryeBread, whiteBread),
    )

    private val extraCheese = ModifierOption("cheese", "Extra cheese", Money(1000))
    private val extrasGroup = ModifierGroup(
        id = "extras",
        name = "Extras",
        required = false,
        minSelection = 0,
        maxSelection = 2,
        options = listOf(extraCheese),
    )

    private val smorrebrod = Product(
        id = "p1",
        name = "Smørrebrød",
        categoryId = "cat1",
        basePrice = Money.of(45, 0),
        modifierGroups = listOf(breadGroup, extrasGroup),
    )

    private val menu = Menu(
        restaurantId = "r1",
        categories = listOf(MenuCategory("cat1", "Sandwiches")),
        products = listOf(smorrebrod),
    )

    private fun cart(vararg items: CartItem) = Cart("c1", "r1", items = items.toList())

    @Test
    fun pricesBaseProductWithSatisfiedRequiredModifiers() {
        val result = Pricing.priceCart(menu, cart(CartItem("ci1", "p1", quantity = 2, selectedModifierOptionIds = setOf("bread-rye"))))

        assertIs<PricingResult.Success>(result)
        assertEquals(Money.of(90, 0), result.totals.total)
        assertEquals(Money.of(45, 0), result.lines.single().unitPrice)
        assertEquals(Money.of(90, 0), result.lines.single().lineTotal)
    }

    @Test
    fun appliesModifierPriceDeltas() {
        val item = CartItem("ci1", "p1", quantity = 1, selectedModifierOptionIds = setOf("bread-white", "cheese"))
        val result = Pricing.priceCart(menu, cart(item))

        assertIs<PricingResult.Success>(result)
        // 4500 (base) + 300 (white bread) + 1000 (extra cheese) = 5800
        assertEquals(Money.of(58, 0), result.totals.total)
    }

    @Test
    fun failsWhenRequiredModifierGroupIsNotSatisfied() {
        val result = Pricing.priceCart(menu, cart(CartItem("ci1", "p1", quantity = 1)))

        assertIs<PricingResult.Failure>(result)
        assertEquals(listOf(PricingError.MODIFIER_SELECTION_BELOW_MINIMUM), result.errors)
    }

    @Test
    fun failsWhenModifierSelectionExceedsMaximum() {
        val result = Pricing.priceCart(menu, cart(CartItem("ci1", "p1", quantity = 1, selectedModifierOptionIds = setOf("bread-rye", "bread-white"))))
        assertIs<PricingResult.Failure>(result)
        assertEquals(listOf(PricingError.MODIFIER_SELECTION_ABOVE_MAXIMUM), result.errors)
    }

    @Test
    fun failsWhenUnknownModifierOptionIsSelected() {
        val result = Pricing.priceCart(menu, cart(CartItem("ci1", "p1", quantity = 1, selectedModifierOptionIds = setOf("bread-rye", "does-not-exist"))))
        assertIs<PricingResult.Failure>(result)
        assertEquals(listOf(PricingError.MODIFIER_OPTION_NOT_FOUND), result.errors)
    }

    @Test
    fun failsWhenProductIsSoldOut() {
        val soldOutMenu = Menu("r1", products = listOf(smorrebrod.copy(soldOut = true)))
        val result = Pricing.priceCart(soldOutMenu, cart(CartItem("ci1", "p1", quantity = 1, selectedModifierOptionIds = setOf("bread-rye"))))
        assertIs<PricingResult.Failure>(result)
        assertEquals(listOf(PricingError.PRODUCT_SOLD_OUT), result.errors)
    }

    @Test
    fun failsOnInvalidQuantity() {
        val result = Pricing.priceCart(menu, cart(CartItem("ci1", "p1", quantity = 0, selectedModifierOptionIds = setOf("bread-rye"))))
        assertIs<PricingResult.Failure>(result)
        assertEquals(listOf(PricingError.INVALID_QUANTITY), result.errors)
    }
}

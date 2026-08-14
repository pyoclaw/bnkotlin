package com.brodogfyld.domain.order

import com.brodogfyld.domain.model.CartItem
import com.brodogfyld.domain.model.Currency
import com.brodogfyld.domain.model.Menu
import com.brodogfyld.domain.model.MenuCategory
import com.brodogfyld.domain.model.ModifierGroup
import com.brodogfyld.domain.model.ModifierOption
import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.model.Product
import com.brodogfyld.domain.pricing.Pricing
import com.brodogfyld.domain.pricing.PricingResult
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OrderFactoryTest {

    private val t0 = Instant.parse("2026-08-13T10:00:00Z")

    private val menu = Menu(
        restaurantId = "r1",
        categories = listOf(MenuCategory("cat1", "Sandwiches")),
        products = listOf(
            Product(
                id = "p1",
                name = "Roast beef",
                categoryId = "cat1",
                basePrice = Money.of(45, 0),
                modifierGroups = listOf(
                    ModifierGroup(
                        id = "bread",
                        name = "Bread",
                        required = true,
                        minSelection = 1,
                        maxSelection = 1,
                        options = listOf(
                            ModifierOption("rye", "Rye bread", Money.ZERO),
                            ModifierOption("white", "White bread", Money(300)),
                        ),
                    )
                ),
            )
        ),
    )

    @Test
    fun snapshotsPricedLinesIntoAnImmutableDraft() {
        val cart = com.brodogfyld.domain.model.Cart(
            id = "cart-1",
            restaurantId = "r1",
            items = listOf(CartItem("ci1", "p1", quantity = 2, selectedModifierOptionIds = setOf("white"))),
        )
        val priced = Pricing.priceCart(menu, cart)
        assertIs<PricingResult.Success>(priced)

        val order = OrderFactory.create(
            id = "order-1",
            restaurantId = "r1",
            lines = priced.lines,
            currency = Currency.DKK,
            customerId = "cust-1",
            createdAt = t0,
        )

        assertEquals(OrderState.DRAFT, order.state)
        assertEquals(Money.of(96, 0), order.total) // (4500 + 300) * 2
        assertEquals(1, order.items.size)
        assertEquals("Roast beef", order.items.single().name)
        assertEquals(listOf("White bread"), order.items.single().modifierNames)
        assertEquals(0L, order.version)
        assertEquals(emptyList(), order.timeline)
    }
}

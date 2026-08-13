package com.brodogfyld.domain.pricing

import com.brodogfyld.domain.model.Cart
import com.brodogfyld.domain.model.CartItem
import com.brodogfyld.domain.model.Menu
import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.model.Product
import kotlinx.serialization.Serializable

@Serializable
data class CartLine(
    val item: CartItem,
    val product: Product,
    val unitPrice: Money,
    val lineTotal: Money,
)

@Serializable
data class CartTotals(
    val subtotal: Money,
    val total: Money,
)

@Serializable
enum class PricingError {
    PRODUCT_NOT_FOUND,
    PRODUCT_UNAVAILABLE,
    PRODUCT_SOLD_OUT,
    INVALID_QUANTITY,
    MODIFIER_OPTION_NOT_FOUND,
    MODIFIER_SELECTION_BELOW_MINIMUM,
    MODIFIER_SELECTION_ABOVE_MAXIMUM,
}

sealed interface PricingResult {
    data class Success(val lines: List<CartLine>, val totals: CartTotals) : PricingResult
    data class Failure(val errors: List<PricingError>) : PricingResult
}

/**
 * Server-authoritative pricing. The client may render an optimistic estimate,
 * but the backend recalculates totals from the menu before an order is
 * created, exactly as [Pricing.priceCart] does here.
 */
object Pricing {

    /**
     * Prices a single product with a selected set of modifier option ids.
     * Returns null when the selection is invalid for the product.
     */
    fun unitPrice(product: Product, selectedOptionIds: Set<String>): Money? {
        val allOptionIds = product.modifierGroups.flatMap { it.options }.map { it.id }.toSet()
        if (selectedOptionIds.any { it !in allOptionIds }) return null

        var price = product.basePrice
        for (group in product.modifierGroups) {
            val selected = group.options.count { it.id in selectedOptionIds }
            if (selected < group.minSelection) return null
            if (selected > group.maxSelection) return null
            for (option in group.options) {
                if (option.id in selectedOptionIds) price += option.priceDelta
            }
        }
        return price
    }

    fun priceCart(menu: Menu, cart: Cart): PricingResult {
        val lines = mutableListOf<CartLine>()
        val errors = mutableListOf<PricingError>()

        for (item in cart.items) {
            val product = menu.productById(item.productId)
            when {
                product == null -> errors += PricingError.PRODUCT_NOT_FOUND
                !product.available -> errors += PricingError.PRODUCT_UNAVAILABLE
                product.soldOut -> errors += PricingError.PRODUCT_SOLD_OUT
                item.quantity < 1 -> errors += PricingError.INVALID_QUANTITY
                else -> {
                    val unit = unitPrice(product, item.selectedModifierOptionIds)
                    if (unit == null) {
                        val allOptionIds = product.modifierGroups.flatMap { it.options }.map { it.id }.toSet()
                        if (item.selectedModifierOptionIds.any { it !in allOptionIds }) {
                            errors += PricingError.MODIFIER_OPTION_NOT_FOUND
                        }
                        for (group in product.modifierGroups) {
                            val selected = group.options.count { it.id in item.selectedModifierOptionIds }
                            if (selected < group.minSelection) errors += PricingError.MODIFIER_SELECTION_BELOW_MINIMUM
                            if (selected > group.maxSelection) errors += PricingError.MODIFIER_SELECTION_ABOVE_MAXIMUM
                        }
                    } else {
                        lines += CartLine(item, product, unit, unit * item.quantity)
                    }
                }
            }
        }

        return if (errors.isEmpty()) {
            val subtotal = lines.fold(Money.ZERO) { acc, line -> acc + line.lineTotal }
            PricingResult.Success(lines, CartTotals(subtotal = subtotal, total = subtotal))
        } else {
            PricingResult.Failure(errors.distinct())
        }
    }
}

package com.brodogfyld.domain.order

import com.brodogfyld.domain.model.Currency
import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.pricing.CartLine
import kotlinx.datetime.Instant

/**
 * Creates an immutable [Order] from server-priced cart lines. Product names,
 * prices and selected modifiers are snapshotted so later menu edits cannot
 * mutate an existing order (see docs/04-domain-model.md "Snapshot rule").
 */
object OrderFactory {

    fun create(
        id: String,
        restaurantId: String,
        lines: List<CartLine>,
        currency: Currency,
        customerId: String?,
        createdAt: Instant,
    ): Order {
        val items = lines.mapIndexed { index, line ->
            val modifierNames = line.product.modifierGroups
                .flatMap { it.options }
                .filter { it.id in line.item.selectedModifierOptionIds }
                .map { it.name }
            OrderItem(
                id = "$id-item-$index",
                productId = line.product.id,
                name = line.product.name,
                unitPrice = line.unitPrice,
                quantity = line.item.quantity,
                modifierNames = modifierNames,
                lineTotal = line.lineTotal,
            )
        }
        val total = lines.fold(Money.ZERO) { acc, line -> acc + line.lineTotal }
        return Order(
            id = id,
            restaurantId = restaurantId,
            total = total,
            items = items,
            currency = currency,
            state = OrderState.DRAFT,
            customerId = customerId,
            createdAt = createdAt,
            updatedAt = createdAt,
        )
    }
}

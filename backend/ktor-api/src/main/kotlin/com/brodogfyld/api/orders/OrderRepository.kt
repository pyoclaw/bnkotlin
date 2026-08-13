package com.brodogfyld.api.orders

import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderState

/**
 * Persistence boundary for orders. The backend is the source of truth; clients
 * read and mutate orders only through the API, never directly.
 */
interface OrderRepository {
    suspend fun save(order: Order): Order

    suspend fun findById(id: String): Order?

    suspend fun findByRestaurant(restaurantId: String): List<Order>

    suspend fun findByRestaurantAndStates(restaurantId: String, states: Set<OrderState>): List<Order>
}

package com.brodogfyld.api.orders

import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderState
import java.util.concurrent.ConcurrentHashMap

/**
 * Dev/test fallback used only when DATABASE_URL is not configured. PostgreSQL
 * is the canonical store; see PostgresOrderRepository.
 */
class InMemoryOrderRepository : OrderRepository {

    private val store = ConcurrentHashMap<String, Order>()

    override suspend fun save(order: Order): Order {
        store[order.id] = order
        return order
    }

    override suspend fun findById(id: String): Order? = store[id]

    override suspend fun findByRestaurant(restaurantId: String): List<Order> =
        store.values
            .filter { it.restaurantId == restaurantId }
            .sortedByDescending { it.createdAt }

    override suspend fun findByRestaurantAndStates(restaurantId: String, states: Set<OrderState>): List<Order> =
        store.values
            .filter { it.restaurantId == restaurantId && it.state in states }
            .sortedByDescending { it.createdAt }
}

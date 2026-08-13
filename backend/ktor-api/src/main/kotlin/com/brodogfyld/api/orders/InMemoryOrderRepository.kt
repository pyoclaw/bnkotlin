package com.brodogfyld.api.orders

import com.brodogfyld.api.realtime.OrderEvent
import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderState
import java.util.concurrent.ConcurrentHashMap

/**
 * Dev/test fallback used only when DATABASE_URL is not configured. PostgreSQL
 * is the canonical store; see PostgresOrderRepository. Mirrors the optimistic
 * concurrency contract so the same stale-write behavior is observable without
 * a database.
 */
class InMemoryOrderRepository : OrderRepository {

    private val store = ConcurrentHashMap<String, Order>()

    override suspend fun create(order: Order, events: List<OrderEvent>): Order {
        store[order.id] = order
        return order
    }

    override suspend fun update(order: Order, expectedVersion: Long, events: List<OrderEvent>): Order =
        synchronized(store) {
            val existing = store[order.id] ?: throw OrderNotFoundException(order.id)
            if (existing.version != expectedVersion) {
                throw OrderConcurrencyException(order.id, expectedVersion, existing.version)
            }
            store[order.id] = order
            order
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

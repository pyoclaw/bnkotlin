package com.brodogfyld.api.orders

import com.brodogfyld.api.realtime.OrderEvent
import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderState

/**
 * Thrown when an optimistic write fails because the persisted [version] no
 * longer matches the [expectedVersion] a caller read earlier. The backend is
 * the source of truth; stale kitchen/customer mutations are rejected rather
 * than silently overwriting a newer state (docs/12-database.md "Order
 * versioning").
 */
class OrderConcurrencyException(
    orderId: String,
    val expectedVersion: Long,
    val actualVersion: Long,
) : Exception("Order $orderId modified concurrently (expected version $expectedVersion, found $actualVersion)")

/**
 * Persistence boundary for orders. The domain never depends on Exposed or
 * JDBC; implementations map [Order] to PostgreSQL rows. [create] inserts a new
 * order, [update] applies an optimistic, version-checked mutation. [events]
 * are written to the transactional outbox in the same transaction as the order
 * change so a committed change always has a durable realtime event.
 */
interface OrderRepository {
    suspend fun create(order: Order, events: List<OrderEvent>): Order

    suspend fun update(order: Order, expectedVersion: Long, events: List<OrderEvent>): Order

    suspend fun findById(id: String): Order?

    suspend fun findByRestaurant(restaurantId: String): List<Order>

    suspend fun findByRestaurantAndStates(restaurantId: String, states: Set<OrderState>): List<Order>
}

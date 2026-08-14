package com.brodogfyld.api

import com.brodogfyld.api.db.Database
import com.brodogfyld.api.db.OutboxEventsTable
import com.brodogfyld.api.orders.OrderConcurrencyException
import com.brodogfyld.api.orders.PostgresOrderRepository
import com.brodogfyld.api.realtime.OrderEvent
import com.brodogfyld.domain.model.Currency
import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderActorType
import com.brodogfyld.domain.order.OrderItem
import com.brodogfyld.domain.order.OrderState
import com.brodogfyld.domain.order.OrderTimelineEvent
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.Assume.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs against a real PostgreSQL when DATABASE_URL is set (CI supplies it via
 * a Postgres service container); skipped otherwise. PostgreSQL-specific
 * behavior (transactions, constraints, optimistic locking) is tested here
 * rather than against an in-memory double.
 */
class PostgresOrderRepositoryTest {

    private fun sampleOrder(id: String = "pg-${UUID.randomUUID()}"): Order {
        val now = Clock.System.now()
        return Order(
            id = id,
            restaurantId = "test-restaurant",
            total = Money.of(90, 0),
            createdAt = now,
            updatedAt = now,
            items = listOf(
                OrderItem(
                    id = "item-0",
                    productId = "smorrebrod-okse",
                    name = "Roast beef smørrebrød",
                    unitPrice = Money.of(45, 0),
                    quantity = 2,
                    modifierNames = listOf("Rye bread"),
                    lineTotal = Money.of(90, 0),
                )
            ),
            currency = Currency.DKK,
            state = OrderState.QUEUED,
            version = 3L,
            timeline = listOf(
                OrderTimelineEvent("ev-1", now, OrderActorType.CUSTOMER, null, OrderState.DRAFT, OrderState.PENDING_PAYMENT, null, emptyMap()),
                OrderTimelineEvent("ev-2", now, OrderActorType.PAYMENT_PROVIDER, "fake", OrderState.PENDING_PAYMENT, OrderState.PAID, null, emptyMap()),
                OrderTimelineEvent("ev-3", now, OrderActorType.SYSTEM, null, OrderState.PAID, OrderState.QUEUED, null, emptyMap()),
            ),
        )
    }

    private fun orderEvent(order: Order, eventId: String = "ev-${UUID.randomUUID()}"): OrderEvent =
        OrderEvent(
            eventId = eventId,
            type = "order.queued",
            orderId = order.id,
            restaurantId = order.restaurantId,
            version = order.version,
            occurredAt = order.updatedAt,
            state = order.state,
        )

    @Test
    fun persistsAndReloadsAnOrderWithItemsAndTimeline() {
        val jdbcUrl = System.getenv("DATABASE_URL")
        assumeTrue("DATABASE_URL not set; skipping PostgreSQL integration test", !jdbcUrl.isNullOrBlank())

        val database = Database(jdbcUrl!!)
        try {
            runBlocking {
                val repository = PostgresOrderRepository(database)
                val order = sampleOrder()
                repository.create(order, emptyList())

                val loaded = repository.findById(order.id)
                assertNotNull(loaded)
                assertEquals(order.id, loaded.id)
                assertEquals(OrderState.QUEUED, loaded.state)
                assertEquals(3L, loaded.version)
                assertEquals(Money.of(90, 0), loaded.total)
                assertEquals(1, loaded.items.size)
                assertEquals("Roast beef smørrebrød", loaded.items.single().name)
                assertEquals(listOf("Rye bread"), loaded.items.single().modifierNames)
                assertEquals(3, loaded.timeline.size)

                val queued = repository.findByRestaurantAndStates("test-restaurant", setOf(OrderState.QUEUED))
                assertTrue(queued.any { it.id == order.id })
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun rejectsStaleVersionUpdates() {
        val jdbcUrl = System.getenv("DATABASE_URL")
        assumeTrue("DATABASE_URL not set; skipping PostgreSQL integration test", !jdbcUrl.isNullOrBlank())

        val database = Database(jdbcUrl!!)
        try {
            runBlocking {
                val repository = PostgresOrderRepository(database)
                val order = sampleOrder()
                repository.create(order, emptyList())

                // A write based on version 3 succeeds and advances to 4.
                val next = order.copy(version = 4L, state = OrderState.ACCEPTED, updatedAt = Clock.System.now())
                repository.update(next, expectedVersion = 3L, events = emptyList())

                // A second write still based on the stale version 3 is rejected.
                val stale = next.copy(version = 5L, state = OrderState.PREPARING)
                assertFailsWith<OrderConcurrencyException> {
                    runBlocking { repository.update(stale, expectedVersion = 3L, events = emptyList()) }
                }
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun writesOutboxEventsInTheSameTransactionAsTheOrderChange() {
        val jdbcUrl = System.getenv("DATABASE_URL")
        assumeTrue("DATABASE_URL not set; skipping PostgreSQL integration test", !jdbcUrl.isNullOrBlank())

        val database = Database(jdbcUrl!!)
        try {
            runBlocking {
                val repository = PostgresOrderRepository(database)
                val order = sampleOrder()

                val created = orderEvent(order, eventId = "ev-created")
                repository.create(order, listOf(created))

                val next = order.copy(version = 4L, state = OrderState.ACCEPTED, updatedAt = Clock.System.now())
                val accepted = orderEvent(next, eventId = "ev-accepted")
                repository.update(next, expectedVersion = 3L, events = listOf(accepted))

                val eventIds = transaction(database.exposed) {
                    OutboxEventsTable.selectAll()
                        .where { OutboxEventsTable.aggregateId eq order.id }
                        .map { it[OutboxEventsTable.eventId] }
                }
                assertEquals(setOf("ev-created", "ev-accepted"), eventIds.toSet())
            }
        } finally {
            database.close()
        }
    }
}

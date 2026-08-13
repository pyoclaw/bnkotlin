package com.brodogfyld.api

import com.brodogfyld.api.db.Database
import com.brodogfyld.api.orders.PostgresOrderRepository
import com.brodogfyld.domain.model.Currency
import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderActorType
import com.brodogfyld.domain.order.OrderItem
import com.brodogfyld.domain.order.OrderState
import com.brodogfyld.domain.order.OrderTimelineEvent
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assume.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Runs against a real PostgreSQL when DATABASE_URL is set (CI supplies it via
 * a Postgres service container); skipped otherwise.
 */
class PostgresOrderRepositoryTest {

    @Test
    fun persistsAndReloadsAnOrderWithItemsAndTimeline() {
        val jdbcUrl = System.getenv("DATABASE_URL")
        assumeTrue("DATABASE_URL not set; skipping PostgreSQL integration test", !jdbcUrl.isNullOrBlank())

        val database = Database(jdbcUrl!!)
        try {
            runBlocking {
                val repository = PostgresOrderRepository(database)
                val now = Clock.System.now()
                val order = Order(
                    id = "pg-${UUID.randomUUID()}",
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

                repository.save(order)

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
                assertEquals(true, queued.any { it.id == order.id })
            }
        } finally {
            database.close()
        }
    }
}

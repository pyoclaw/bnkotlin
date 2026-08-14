package com.brodogfyld.sync

import com.brodogfyld.domain.order.OrderState
import com.brodogfyld.sync.db.KitchenDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncCoordinatorTest {

    private val now = Instant.parse("2026-01-01T12:00:00Z")

    private fun idGenerator(): () -> String {
        var counter = 0
        return { "m-${++counter}" }
    }

    private fun newDb() = KitchenDatabase(createSqlDriver())

    private fun seedCache(db: KitchenDatabase, id: String, state: OrderState, version: Long, restaurantId: String = "r1") {
        db.kitchenQueries.upsertOrder(id, restaurantId, state.name, version, 9000L, now.toString())
    }

    private fun remote(id: String, state: OrderState, version: Long) = RemoteOrder(
        id = id,
        restaurantId = "r1",
        state = state.name,
        totalMinor = 9000L,
        version = version,
        createdAt = now.toString(),
        updatedAt = now.toString(),
    )

    @Test
    fun synchronizeReplaysOutboxThenResyncs() = runBlocking {
        val transport = FakeSyncTransport()
        transport.seed(remote("o1", OrderState.QUEUED, 4L))
        val db = newDb()
        seedCache(db, "o1", OrderState.QUEUED, 4L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })
        val coordinator = SyncCoordinator(engine, "r1")

        // Offline optimistic edit leaves a pending mutation behind.
        val applied = engine.localAction("o1", KitchenAction.ACCEPT, actorId = "staff-1")
        assertTrue(applied is LocalActionResult.Applied)
        assertEquals(1, db.kitchenQueries.selectOutbox().executeAsList().size)

        // Reconnect: the outbox drains and the cache converges on the server.
        val report = coordinator.synchronize()
        assertEquals(1, report.appliedCount)
        assertEquals(0, db.kitchenQueries.selectOutbox().executeAsList().size)
        assertEquals("ACCEPTED", db.kitchenQueries.selectOrder("o1").executeAsOne().state)
        assertEquals(5L, db.kitchenQueries.selectOrder("o1").executeAsOne().version)
        assertEquals("ACCEPTED", transport.serverOrder("o1")?.state)
    }

    @Test
    fun synchronizeReconcilesAStaleOptimisticEditBeforeResync() = runBlocking {
        val transport = FakeSyncTransport()
        // Another device already accepted; the client's cached QUEUED is stale.
        transport.seed(remote("o1", OrderState.ACCEPTED, 5L))
        transport.seed(remote("o2", OrderState.PREPARING, 6L))
        val db = newDb()
        seedCache(db, "o1", OrderState.QUEUED, 4L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })
        val coordinator = SyncCoordinator(engine, "r1")

        engine.localAction("o1", KitchenAction.ACCEPT, actorId = "staff-1")
        assertEquals(1, db.kitchenQueries.selectOutbox().executeAsList().size)

        val report = coordinator.synchronize()

        // The stale accept is dropped and the cache reconciled from the server,
        // which also brings in the order the client never cached.
        assertEquals(1, report.staleCount)
        assertEquals(0, db.kitchenQueries.selectOutbox().executeAsList().size)
        assertEquals("ACCEPTED", db.kitchenQueries.selectOrder("o1").executeAsOne().state)
        assertEquals(5L, db.kitchenQueries.selectOrder("o1").executeAsOne().version)
        assertEquals("PREPARING", db.kitchenQueries.selectOrder("o2").executeAsOne().state)
    }
}

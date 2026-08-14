package com.brodogfyld.sync

import com.brodogfyld.domain.order.OrderState
import com.brodogfyld.sync.db.KitchenDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OrderSyncEngineTest {

    private val now = Instant.parse("2026-01-01T12:00:00Z")
    private val later = Instant.parse("2026-01-01T12:30:00Z")

    private fun idGenerator(prefix: String = "m"): () -> String {
        var counter = 0
        return { "${prefix}-${++counter}" }
    }

    private fun newDb() = KitchenDatabase(createSqlDriver())

    private fun seedCache(
        db: KitchenDatabase,
        id: String,
        state: OrderState,
        version: Long,
        restaurantId: String = "r1",
        totalMinor: Long = 9000L,
    ) {
        db.kitchenQueries.upsertOrder(id, restaurantId, state.name, version, totalMinor, now.toString())
    }

    private fun seededOrder(id: String, state: OrderState, version: Long) = RemoteOrder(
        id = id,
        restaurantId = "r1",
        state = state.name,
        totalMinor = 9000L,
        version = version,
        createdAt = now.toString(),
        updatedAt = now.toString(),
    )

    private fun event(orderId: String, version: Long, state: OrderState, eventId: String = "ev") = SyncEvent(
        eventId = eventId,
        type = "order.${state.name.lowercase()}",
        aggregateId = orderId,
        version = version,
        occurredAt = now.toString(),
        payload = buildJsonObject { put("state", state.name) },
    )

    // --- outbox replay -------------------------------------------------------

    @Test
    fun replaysOfflineMutationsInOrderAndClearsTheOutbox() = runBlocking {
        val transport = FakeSyncTransport()
        transport.seed(seededOrder("o1", OrderState.QUEUED, 4L))
        val db = newDb()
        seedCache(db, "o1", OrderState.QUEUED, 4L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        val accepted = engine.localAction("o1", KitchenAction.ACCEPT, actorId = "staff-1")
        val readied = engine.localAction("o1", KitchenAction.READY, actorId = "staff-1")
        assertTrue(accepted is LocalActionResult.Applied && accepted.version == 5L)
        assertTrue(readied is LocalActionResult.Applied && readied.version == 6L)
        assertEquals(2, db.kitchenQueries.selectOutbox().executeAsList().size)

        val report = engine.replayOutbox()
        assertEquals(2, report.appliedCount)
        assertEquals(0, report.staleCount)
        assertEquals(0, report.pendingCount)
        assertEquals(0, db.kitchenQueries.selectOutbox().executeAsList().size)

        assertEquals("READY", db.kitchenQueries.selectOrder("o1").executeAsOne().state)
        assertEquals(6L, db.kitchenQueries.selectOrder("o1").executeAsOne().version)
        assertEquals("READY", transport.serverOrder("o1")?.state)
    }

    @Test
    fun keepsMutationsOnTransientFailureAndRetriesNextPass() = runBlocking {
        val transport = FakeSyncTransport()
        transport.seed(seededOrder("o1", OrderState.QUEUED, 4L))
        val db = newDb()
        seedCache(db, "o1", OrderState.QUEUED, 4L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        engine.localAction("o1", KitchenAction.ACCEPT, actorId = "staff-1")
        engine.localAction("o1", KitchenAction.READY, actorId = "staff-1")

        transport.failOnAction = "ready"
        val first = engine.replayOutbox()
        assertEquals(1, first.appliedCount)
        assertEquals(1, first.pendingCount)
        // Only the second mutation remains queued.
        assertEquals(1, db.kitchenQueries.selectOutbox().executeAsList().size)
        assertEquals("ready", db.kitchenQueries.selectOutbox().executeAsList().single().mutation_type)

        // Connectivity returns for the next pass.
        transport.failOnAction = null
        val second = engine.replayOutbox()
        assertEquals(1, second.appliedCount)
        assertEquals(0, db.kitchenQueries.selectOutbox().executeAsList().size)
        assertEquals("READY", transport.serverOrder("o1")?.state)
    }

    @Test
    fun dropsStaleMutationsAndReconcilesFromTheServer() = runBlocking {
        val transport = FakeSyncTransport()
        // The server has already moved past the optimistic state (another device
        // accepted first), so our queued accept is now stale.
        transport.seed(seededOrder("o1", OrderState.ACCEPTED, 5L))
        val db = newDb()
        seedCache(db, "o1", OrderState.QUEUED, 4L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        engine.localAction("o1", KitchenAction.ACCEPT, actorId = "staff-1")
        assertEquals(1, db.kitchenQueries.selectOutbox().executeAsList().size)

        val report = engine.replayOutbox()
        assertEquals(1, report.staleCount)
        assertEquals(0, report.appliedCount)
        assertEquals(0, db.kitchenQueries.selectOutbox().executeAsList().size)

        // Cache reconciled to the server's authoritative ACCEPTED/5.
        val cached = db.kitchenQueries.selectOrder("o1").executeAsOne()
        assertEquals("ACCEPTED", cached.state)
        assertEquals(5L, cached.version)
    }

    @Test
    fun supersedesLaterMutationsForAnOrderThatWentStale() = runBlocking {
        val transport = FakeSyncTransport()
        transport.seed(seededOrder("o1", OrderState.ACCEPTED, 5L))
        val db = newDb()
        seedCache(db, "o1", OrderState.QUEUED, 4L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        engine.localAction("o1", KitchenAction.ACCEPT, actorId = "staff-1")
        engine.localAction("o1", KitchenAction.READY, actorId = "staff-1")

        val report = engine.replayOutbox()
        assertEquals(1, report.staleCount)
        assertEquals(1, report.outcomes.count { it is ReplayOutcome.Superseded })
        assertEquals(0, db.kitchenQueries.selectOutbox().executeAsList().size)
    }

    @Test
    fun replayIsIdempotentWhenServerAlreadyAppliedTheMutation() = runBlocking {
        val transport = FakeSyncTransport()
        transport.seed(seededOrder("o1", OrderState.ACCEPTED, 5L))
        // The server already applied "m-1" before the client could clear its outbox.
        transport.markApplied("m-1")
        val db = newDb()
        seedCache(db, "o1", OrderState.QUEUED, 4L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        db.kitchenQueries.insertOutbox(
            id = "m-1",
            mutation_type = "accept",
            order_id = "o1",
            payload = SyncJson.encodeToString(
                OutboxMutation("m-1", "accept", "o1", "r1", baseVersion = 4L, actorId = "staff-1")
            ),
            created_at = now.toString(),
        )

        val report = engine.replayOutbox()
        assertEquals(1, report.appliedCount)
        assertEquals(0, report.staleCount)
        assertEquals(0, db.kitchenQueries.selectOutbox().executeAsList().size)
        // Server state is unchanged: the retry did not double-apply.
        assertEquals(5L, transport.serverOrder("o1")?.version)
        assertEquals("ACCEPTED", db.kitchenQueries.selectOrder("o1").executeAsOne().state)
    }

    @Test
    fun replaysDelayMutationWithFutureEta() = runBlocking {
        val transport = FakeSyncTransport()
        transport.seed(seededOrder("o1", OrderState.ACCEPTED, 5L))
        val db = newDb()
        seedCache(db, "o1", OrderState.ACCEPTED, 5L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        val result = engine.localAction("o1", KitchenAction.DELAY, actorId = "staff-1", newEta = later)
        assertTrue(result is LocalActionResult.Applied && result.version == 6L)
        assertEquals("DELAYED", db.kitchenQueries.selectOrder("o1").executeAsOne().state)

        val report = engine.replayOutbox()
        assertEquals(1, report.appliedCount)
        assertEquals("DELAYED", transport.serverOrder("o1")?.state)
        assertEquals(6L, transport.serverOrder("o1")?.version)
    }

    // --- local action validation ---------------------------------------------

    @Test
    fun rejectsIllegalLocalTransitionsWithoutQueuing() = runBlocking {
        val transport = FakeSyncTransport()
        val db = newDb()
        seedCache(db, "o1", OrderState.QUEUED, 4L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        // QUEUED -> COMPLETED is not a legal transition.
        val result = engine.localAction("o1", KitchenAction.COMPLETE, actorId = "staff-1")
        assertTrue(result is LocalActionResult.Rejected && result.reason == LocalRejectReason.ILLEGAL_TRANSITION)
        assertEquals(0, db.kitchenQueries.selectOutbox().executeAsList().size)
        assertEquals("QUEUED", db.kitchenQueries.selectOrder("o1").executeAsOne().state)
    }

    // --- realtime event application ------------------------------------------

    @Test
    fun ignoresDuplicateEvents() = runBlocking {
        val transport = FakeSyncTransport()
        val db = newDb()
        seedCache(db, "o1", OrderState.QUEUED, 4L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        // Same version as the cache (already applied) and an older one.
        assertEquals(EventResult.Duplicate, engine.applyEvent(event("o1", 4L, OrderState.QUEUED, "ev-dup")))
        assertEquals(EventResult.Duplicate, engine.applyEvent(event("o1", 3L, OrderState.QUEUED, "ev-old")))
        assertEquals("QUEUED", db.kitchenQueries.selectOrder("o1").executeAsOne().state)
    }

    @Test
    fun appliesInSequenceEventsAndPreservesTotal() = runBlocking {
        val transport = FakeSyncTransport()
        val db = newDb()
        seedCache(db, "o1", OrderState.ACCEPTED, 5L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        assertEquals(EventResult.Applied, engine.applyEvent(event("o1", 6L, OrderState.PREPARING, "ev-next")))
        val cached = db.kitchenQueries.selectOrder("o1").executeAsOne()
        assertEquals("PREPARING", cached.state)
        assertEquals(6L, cached.version)
        assertEquals(9000L, cached.total_minor)
    }

    @Test
    fun removesOrderFromCacheOnTerminalEvent() = runBlocking {
        val transport = FakeSyncTransport()
        val db = newDb()
        seedCache(db, "o1", OrderState.READY, 6L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        assertEquals(EventResult.Applied, engine.applyEvent(event("o1", 7L, OrderState.COMPLETED, "ev-done")))
        assertNull(db.kitchenQueries.selectOrder("o1").executeAsOneOrNull())
    }

    @Test
    fun recoversFromVersionGapByFetchingTheOrder() = runBlocking {
        val transport = FakeSyncTransport()
        transport.seed(seededOrder("o1", OrderState.READY, 9L))
        val db = newDb()
        seedCache(db, "o1", OrderState.QUEUED, 4L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        val result = engine.applyEvent(event("o1", 9L, OrderState.READY, "ev-gap"))
        assertTrue(result is EventResult.Recovered && result.orderId == "o1")
        val cached = db.kitchenQueries.selectOrder("o1").executeAsOne()
        assertEquals("READY", cached.state)
        assertEquals(9L, cached.version)
    }

    // --- full recovery -------------------------------------------------------

    @Test
    fun recoverReplacesMissingOrdersAndRemovesEvictedOnes() = runBlocking {
        val transport = FakeSyncTransport()
        transport.seed(seededOrder("a", OrderState.QUEUED, 1L))
        transport.seed(seededOrder("b", OrderState.PREPARING, 6L))
        val db = newDb()
        seedCache(db, "b", OrderState.ACCEPTED, 5L)
        seedCache(db, "c", OrderState.QUEUED, 1L) // no longer on the server
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        val result = engine.recover("r1")
        assertEquals(2, result.replaced)
        assertEquals(1, result.removed)

        assertTrue(db.kitchenQueries.selectOrder("a").executeAsOneOrNull() != null)
        assertEquals("PREPARING", db.kitchenQueries.selectOrder("b").executeAsOne().state)
        assertNull(db.kitchenQueries.selectOrder("c").executeAsOneOrNull())
    }

    @Test
    fun recoverPreservesOrdersWithPendingMutations() = runBlocking {
        val transport = FakeSyncTransport()
        transport.seed(seededOrder("o1", OrderState.QUEUED, 4L))
        val db = newDb()
        seedCache(db, "o1", OrderState.QUEUED, 4L)
        val engine = OrderSyncEngine(db, transport, idGenerator(), { now })

        // Optimistic accept is still pending; recover must not clobber it back
        // to the server's QUEUED state before replay.
        engine.localAction("o1", KitchenAction.ACCEPT, actorId = "staff-1")
        assertEquals("ACCEPTED", db.kitchenQueries.selectOrder("o1").executeAsOne().state)

        engine.recover("r1")
        assertEquals("ACCEPTED", db.kitchenQueries.selectOrder("o1").executeAsOne().state)

        // After replay the server is authoritative and the outbox is drained.
        val report = engine.replayOutbox()
        assertEquals(1, report.appliedCount)
        assertEquals(0, db.kitchenQueries.selectOutbox().executeAsList().size)
    }
}

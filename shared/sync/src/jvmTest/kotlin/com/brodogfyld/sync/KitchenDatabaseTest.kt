package com.brodogfyld.sync

import com.brodogfyld.sync.db.KitchenDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class KitchenDatabaseTest {

    @Test
    fun insertsAndReadsACachedOrder() {
        val db = KitchenDatabase(createSqlDriver())
        db.kitchenQueries.upsertOrder(
            id = "o1",
            restaurant_id = "r1",
            state = "QUEUED",
            version = 3L,
            total_minor = 9000L,
            updated_at = "2026-01-01T00:00:00Z",
        )

        val cached = db.kitchenQueries.selectOrder("o1").executeAsOne()
        assertEquals("QUEUED", cached.state)
        assertEquals(3L, cached.version)
        assertEquals(9000L, cached.total_minor)
    }

    @Test
    fun enqueuesAndClearsAnOutboxMutation() {
        val db = KitchenDatabase(createSqlDriver())
        val queries = db.kitchenQueries
        queries.insertOutbox("m1", "accept", "o1", "{}", "2026-01-01T00:00:00Z")

        val pending = queries.selectOutbox().executeAsList()
        assertEquals(1, pending.size)
        assertEquals("m1", pending.single().id)

        queries.deleteOutbox("m1")
        assertEquals(0, queries.selectOutbox().executeAsList().size)
    }
}

package com.brodogfyld.sync

/**
 * Drives the offline sync engine through the reconnect/recovery loop
 * (docs/07-sync-engine.md).
 *
 * [synchronize] is the canonical "back online" action: drain the local outbox
 * first (so optimistic edits are either applied or reconciled against the
 * authoritative server), then resync the kitchen cache from the server list.
 * Replay-before-recover ordering matters: a pending mutation's optimistic
 * state must not be clobbered by a resync before the outbox has had its
 * chance to be applied or dropped.
 */
class SyncCoordinator(
    private val engine: OrderSyncEngine,
    private val restaurantId: String,
) {
    /** One reconnect pass: replay the outbox, then reconcile the cache. */
    suspend fun synchronize(): ReplayReport {
        val report = engine.replayOutbox()
        engine.recover(restaurantId)
        return report
    }
}

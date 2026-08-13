package com.brodogfyld.domain.order

import kotlinx.serialization.Serializable

/**
 * The single source of truth for order states. No UI or client may invent
 * arbitrary states; transitions are defined centrally in [OrderStateMachine].
 */
@Serializable
enum class OrderState {
    DRAFT,
    PENDING_PAYMENT,
    PAYMENT_AUTHORIZED,
    PAID,
    QUEUED,
    ACCEPTED,
    PREPARING,
    DELAYED,
    READY,
    COMPLETED,

    REJECTED,
    CANCELLED,
    REFUNDED,
    EXPIRED;

    val isTerminal: Boolean get() = this in TERMINAL_STATES

    val isActive: Boolean get() = !isTerminal

    companion object {
        val TERMINAL_STATES: Set<OrderState> =
            setOf(COMPLETED, REJECTED, CANCELLED, REFUNDED, EXPIRED)

        val ACTIVE_STATES: Set<OrderState> = entries.filter { it.isActive }.toSet()
    }
}

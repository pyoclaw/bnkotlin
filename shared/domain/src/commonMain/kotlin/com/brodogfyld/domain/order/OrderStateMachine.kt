package com.brodogfyld.domain.order

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

enum class TransitionFailure {
    TERMINAL_STATE,
    ILLEGAL_TRANSITION,
    MISSING_REASON_CODE,
    INVALID_ETA,
}

class OrderTransitionException(
    val failure: TransitionFailure,
    val from: OrderState,
    val to: OrderState,
) : Exception("Cannot transition order from $from to $to: $failure")

/**
 * Central authority for order transitions. The backend enforces the same table
 * on the server; clients call into this shared logic so the rules live in
 * exactly one place.
 *
 * Event and identifier generation are infrastructure concerns, so callers
 * (the backend, with JVM UUIDs) supply [eventId]; tests supply fixed values.
 */
object OrderStateMachine {

    private val transitions: Map<OrderState, Set<OrderState>> = mapOf(
        OrderState.DRAFT to setOf(OrderState.PENDING_PAYMENT, OrderState.CANCELLED, OrderState.EXPIRED),
        OrderState.PENDING_PAYMENT to setOf(OrderState.PAYMENT_AUTHORIZED, OrderState.PAID, OrderState.CANCELLED, OrderState.EXPIRED),
        OrderState.PAYMENT_AUTHORIZED to setOf(OrderState.PAID, OrderState.CANCELLED, OrderState.EXPIRED),
        OrderState.PAID to setOf(OrderState.QUEUED, OrderState.CANCELLED, OrderState.REFUNDED),
        OrderState.QUEUED to setOf(OrderState.ACCEPTED, OrderState.REJECTED, OrderState.CANCELLED),
        OrderState.ACCEPTED to setOf(OrderState.PREPARING, OrderState.DELAYED, OrderState.READY, OrderState.REJECTED, OrderState.CANCELLED),
        OrderState.PREPARING to setOf(OrderState.DELAYED, OrderState.READY, OrderState.CANCELLED),
        OrderState.DELAYED to setOf(OrderState.PREPARING, OrderState.READY, OrderState.CANCELLED),
        OrderState.READY to setOf(OrderState.COMPLETED, OrderState.CANCELLED),
    )

    fun allowedTransitions(from: OrderState): Set<OrderState> = transitions[from] ?: emptySet()

    fun canTransition(from: OrderState, to: OrderState): Boolean = to in allowedTransitions(from)

    fun transition(
        order: Order,
        target: OrderState,
        actorType: OrderActorType,
        eventId: String,
        actorId: String? = null,
        reasonCode: String? = null,
        metadata: Map<String, String> = emptyMap(),
        occurredAt: Instant = Clock.System.now(),
    ): Result<Order> {
        if (order.state.isTerminal) {
            return Result.failure(OrderTransitionException(TransitionFailure.TERMINAL_STATE, order.state, target))
        }
        if (!canTransition(order.state, target)) {
            return Result.failure(OrderTransitionException(TransitionFailure.ILLEGAL_TRANSITION, order.state, target))
        }
        val event = OrderTimelineEvent(
            eventId = eventId,
            occurredAt = occurredAt,
            actorType = actorType,
            actorId = actorId,
            previousState = order.state,
            newState = target,
            reasonCode = reasonCode,
            metadata = metadata,
        )
        return Result.success(
            order.copy(
                state = target,
                version = order.version + 1,
                timeline = order.timeline + event,
                updatedAt = occurredAt,
            )
        )
    }

    // Kitchen actions ---------------------------------------------------------

    fun accept(order: Order, actorId: String, eventId: String, occurredAt: Instant = Clock.System.now()): Result<Order> =
        transition(order, OrderState.ACCEPTED, OrderActorType.KITCHEN, eventId, actorId, occurredAt = occurredAt)

    fun reject(order: Order, actorId: String, reasonCode: String, eventId: String, occurredAt: Instant = Clock.System.now()): Result<Order> {
        if (reasonCode.isBlank()) {
            return Result.failure(OrderTransitionException(TransitionFailure.MISSING_REASON_CODE, order.state, OrderState.REJECTED))
        }
        return transition(order, OrderState.REJECTED, OrderActorType.KITCHEN, eventId, actorId, reasonCode = reasonCode, occurredAt = occurredAt)
    }

    fun delay(order: Order, actorId: String, newEta: Instant, eventId: String, occurredAt: Instant = Clock.System.now()): Result<Order> {
        if (newEta <= occurredAt) {
            return Result.failure(OrderTransitionException(TransitionFailure.INVALID_ETA, order.state, OrderState.DELAYED))
        }
        return transition(
            order,
            OrderState.DELAYED,
            OrderActorType.KITCHEN,
            eventId,
            actorId,
            metadata = mapOf("eta" to newEta.toString()),
            occurredAt = occurredAt,
        )
    }

    fun ready(order: Order, actorId: String, eventId: String, occurredAt: Instant = Clock.System.now()): Result<Order> =
        transition(order, OrderState.READY, OrderActorType.KITCHEN, eventId, actorId, occurredAt = occurredAt)

    fun complete(order: Order, actorId: String, eventId: String, occurredAt: Instant = Clock.System.now()): Result<Order> =
        transition(order, OrderState.COMPLETED, OrderActorType.KITCHEN, eventId, actorId, occurredAt = occurredAt)

    fun cancel(order: Order, reasonCode: String?, actorType: OrderActorType = OrderActorType.CUSTOMER, actorId: String? = null, eventId: String, occurredAt: Instant = Clock.System.now()): Result<Order> =
        transition(order, OrderState.CANCELLED, actorType, eventId, actorId, reasonCode = reasonCode, occurredAt = occurredAt)
}

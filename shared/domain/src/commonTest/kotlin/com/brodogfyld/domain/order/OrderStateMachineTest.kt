package com.brodogfyld.domain.order

import com.brodogfyld.domain.model.Money
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderStateMachineTest {

    private val t0 = Instant.parse("2026-08-13T10:00:00Z")

    private fun orderAt(state: OrderState = OrderState.DRAFT): Order = Order(
        id = "order-1",
        restaurantId = "r1",
        total = Money.of(90, 0),
        state = state,
        createdAt = t0,
        updatedAt = t0,
    )

    @Test
    fun followsTheHappyPathWithTimelineEvents() {
        var order = orderAt()
        order = OrderStateMachine.transition(order, OrderState.PENDING_PAYMENT, OrderActorType.CUSTOMER, eventId = "e1", occurredAt = t0).getOrThrow()
        order = OrderStateMachine.transition(order, OrderState.PAID, OrderActorType.PAYMENT_PROVIDER, eventId = "e2", occurredAt = t0).getOrThrow()
        order = OrderStateMachine.transition(order, OrderState.QUEUED, OrderActorType.SYSTEM, eventId = "e3", occurredAt = t0).getOrThrow()
        order = OrderStateMachine.accept(order, "staff-1", eventId = "e4", occurredAt = t0).getOrThrow()

        assertEquals(OrderState.ACCEPTED, order.state)
        assertEquals(4, order.timeline.size)
        assertEquals(4L, order.version)
        assertEquals("e4", order.timeline.last().eventId)
        assertEquals(OrderState.QUEUED, order.timeline.last().previousState)
        assertEquals(OrderActorType.KITCHEN, order.timeline.last().actorType)
    }

    @Test
    fun recordsRejectWithReasonCode() {
        val rejected = OrderStateMachine.reject(orderAt(OrderState.QUEUED), "staff-1", "OUT_OF_INGREDIENTS", eventId = "e1", occurredAt = t0).getOrThrow()

        assertEquals(OrderState.REJECTED, rejected.state)
        assertEquals("OUT_OF_INGREDIENTS", rejected.timeline.last().reasonCode)
        assertTrue(rejected.state.isTerminal)
    }

    @Test
    fun rejectsIllegalTransitions() {
        val ex = assertFailsWith<OrderTransitionException> {
            OrderStateMachine.transition(orderAt(OrderState.DRAFT), OrderState.COMPLETED, OrderActorType.KITCHEN, eventId = "e1", occurredAt = t0).getOrThrow()
        }
        assertEquals(TransitionFailure.ILLEGAL_TRANSITION, ex.failure)
    }

    @Test
    fun blocksTransitionsFromTerminalStates() {
        val ex = assertFailsWith<OrderTransitionException> {
            OrderStateMachine.transition(orderAt(OrderState.COMPLETED), OrderState.READY, OrderActorType.KITCHEN, eventId = "e1", occurredAt = t0).getOrThrow()
        }
        assertEquals(TransitionFailure.TERMINAL_STATE, ex.failure)
    }

    @Test
    fun rejectRequiresAReasonCode() {
        val ex = assertFailsWith<OrderTransitionException> {
            OrderStateMachine.reject(orderAt(OrderState.QUEUED), "staff-1", "   ", eventId = "e1", occurredAt = t0).getOrThrow()
        }
        assertEquals(TransitionFailure.MISSING_REASON_CODE, ex.failure)
    }

    @Test
    fun delayRequiresAnEtaInTheFuture() {
        val past = Instant.parse("2026-08-13T09:00:00Z")
        val ex = assertFailsWith<OrderTransitionException> {
            OrderStateMachine.delay(orderAt(OrderState.ACCEPTED), "staff-1", past, eventId = "e1", occurredAt = t0).getOrThrow()
        }
        assertEquals(TransitionFailure.INVALID_ETA, ex.failure)

        val future = Instant.parse("2026-08-13T10:30:00Z")
        val delayed = OrderStateMachine.delay(orderAt(OrderState.ACCEPTED), "staff-1", future, eventId = "e2", occurredAt = t0).getOrThrow()
        assertEquals(OrderState.DELAYED, delayed.state)
        assertEquals(future.toString(), delayed.timeline.last().metadata["eta"])
    }

    @Test
    fun exposesTheCentralTransitionTable() {
        assertTrue(OrderStateMachine.canTransition(OrderState.PAID, OrderState.QUEUED))
        assertTrue(OrderStateMachine.canTransition(OrderState.PAID, OrderState.REFUNDED))
        assertFalse(OrderStateMachine.canTransition(OrderState.PAID, OrderState.ACCEPTED))
        assertFalse(OrderStateMachine.canTransition(OrderState.COMPLETED, OrderState.READY))
    }
}

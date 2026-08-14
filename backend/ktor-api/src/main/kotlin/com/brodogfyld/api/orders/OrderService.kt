package com.brodogfyld.api.orders

import com.brodogfyld.api.dto.CreateOrderRequest
import com.brodogfyld.api.payment.FakePaymentProvider
import com.brodogfyld.api.realtime.OrderEvent
import com.brodogfyld.api.realtime.OrderEventBus
import com.brodogfyld.api.realtime.eventType
import com.brodogfyld.api.realtime.toOrderEvent
import com.brodogfyld.domain.model.Cart
import com.brodogfyld.domain.model.CartItem
import com.brodogfyld.domain.model.Currency
import com.brodogfyld.domain.model.Menu
import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderActorType
import com.brodogfyld.domain.order.OrderFactory
import com.brodogfyld.domain.order.OrderState
import com.brodogfyld.domain.order.OrderStateMachine
import com.brodogfyld.domain.order.OrderTimelineEvent
import com.brodogfyld.domain.order.OrderTransitionException
import com.brodogfyld.domain.order.TransitionFailure
import com.brodogfyld.domain.payment.Payment
import com.brodogfyld.domain.payment.PaymentProvider
import com.brodogfyld.domain.payment.PaymentResult
import com.brodogfyld.domain.pricing.Pricing
import com.brodogfyld.domain.pricing.PricingError
import com.brodogfyld.domain.pricing.PricingResult
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

class OrderNotFoundException(orderId: String) : Exception("Order not found: $orderId")

class PaymentException(reason: String) : Exception("Payment failed: $reason")

sealed interface CreateOrderResult {
    data class Created(val order: Order) : CreateOrderResult
    data class Invalid(val errors: List<PricingError>) : CreateOrderResult
}

/**
 * The backend application service for the order vertical slice. All state
 * changes go through the shared [OrderStateMachine]; the repository persists
 * the authoritative result.
 */
class OrderService(
    private val repository: OrderRepository,
    private val eventBus: OrderEventBus,
    private val paymentProvider: PaymentProvider = FakePaymentProvider(),
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun createOrder(request: CreateOrderRequest, menu: Menu): CreateOrderResult {
        val currency = Currency.entries.firstOrNull { it.code == request.currency } ?: Currency.DKK
        val cart = Cart(
            id = idGenerator(),
            restaurantId = request.restaurantId,
            currency = currency,
            items = request.items.map { item ->
                CartItem(
                    id = idGenerator(),
                    productId = item.productId,
                    quantity = item.quantity,
                    selectedModifierOptionIds = item.modifierOptionIds,
                )
            },
        )
        return when (val priced = Pricing.priceCart(menu, cart)) {
            is PricingResult.Failure -> CreateOrderResult.Invalid(priced.errors)
            is PricingResult.Success -> {
                val order = OrderFactory.create(
                    id = idGenerator(),
                    restaurantId = request.restaurantId,
                    lines = priced.lines,
                    currency = currency,
                    customerId = request.customerId,
                    createdAt = Clock.System.now(),
                )
                val createdEvent = OrderEvent(
                    eventId = idGenerator(),
                    type = order.state.eventType(),
                    orderId = order.id,
                    restaurantId = order.restaurantId,
                    version = order.version,
                    occurredAt = order.createdAt,
                    state = order.state,
                )
                val saved = repository.create(order, listOf(createdEvent))
                eventBus.publish(createdEvent)
                CreateOrderResult.Created(saved)
            }
        }
    }

    suspend fun get(orderId: String): Order? = repository.findById(orderId)

    suspend fun timeline(orderId: String): List<OrderTimelineEvent>? =
        repository.findById(orderId)?.timeline

    suspend fun kitchenOrders(restaurantId: String): List<Order> =
        repository.findByRestaurantAndStates(
            restaurantId,
            setOf(OrderState.QUEUED, OrderState.ACCEPTED, OrderState.PREPARING, OrderState.DELAYED, OrderState.READY),
        )

    suspend fun submit(orderId: String): Result<Order> = mutate(orderId) { order ->
        OrderStateMachine.transition(order, OrderState.PENDING_PAYMENT, OrderActorType.CUSTOMER, eventId = idGenerator())
    }

    suspend fun pay(orderId: String): Result<Order> {
        val order = repository.findById(orderId) ?: return Result.failure(OrderNotFoundException(orderId))
        return when (order.state) {
            OrderState.PENDING_PAYMENT, OrderState.PAYMENT_AUTHORIZED -> {
                val now = Clock.System.now()
                val payment = Payment(
                    id = idGenerator(),
                    orderId = order.id,
                    provider = paymentProvider.name,
                    amount = order.total,
                    currency = order.currency,
                    createdAt = now,
                    updatedAt = now,
                )
                when (val result = paymentProvider.createPayment(payment)) {
                    is PaymentResult.Failure -> Result.failure(PaymentException(result.reason))
                    is PaymentResult.Success -> {
                        var current = order
                        val events = mutableListOf<OrderEvent>()
                        current = OrderStateMachine.transition(
                            current, OrderState.PAYMENT_AUTHORIZED, OrderActorType.PAYMENT_PROVIDER,
                            eventId = idGenerator(), actorId = paymentProvider.name, occurredAt = now,
                        ).getOrElse { return Result.failure(it) }
                        events += current.toOrderEvent()
                        current = OrderStateMachine.transition(
                            current, OrderState.PAID, OrderActorType.PAYMENT_PROVIDER,
                            eventId = idGenerator(), actorId = paymentProvider.name, occurredAt = now,
                        ).getOrElse { return Result.failure(it) }
                        events += current.toOrderEvent()
                        current = OrderStateMachine.transition(
                            current, OrderState.QUEUED, OrderActorType.SYSTEM,
                            eventId = idGenerator(), occurredAt = now,
                        ).getOrElse { return Result.failure(it) }
                        events += current.toOrderEvent()
                        val saved = persist { repository.update(current, order.version, events) }
                            .getOrElse { return Result.failure(it) }
                        events.forEach(eventBus::publish)
                        Result.success(saved)
                    }
                }
            }
            else -> {
                if (order.state.isTerminal) {
                    Result.failure(OrderTransitionException(TransitionFailure.TERMINAL_STATE, order.state, OrderState.QUEUED))
                } else {
                    // Already paid and progressing — idempotent no-op.
                    Result.success(order)
                }
            }
        }
    }

    suspend fun accept(orderId: String, actorId: String?, mutationId: String? = null): Result<Order> =
        kitchenMutate(orderId, mutationId) { order ->
            OrderStateMachine.accept(order, actorId ?: "unknown", eventId = mutationId ?: idGenerator())
        }

    suspend fun reject(orderId: String, actorId: String?, reasonCode: String?, mutationId: String? = null): Result<Order> =
        kitchenMutate(orderId, mutationId) { order ->
            OrderStateMachine.reject(order, actorId ?: "unknown", reasonCode ?: "", eventId = mutationId ?: idGenerator())
        }

    suspend fun delay(orderId: String, actorId: String?, newEta: Instant, mutationId: String? = null): Result<Order> =
        kitchenMutate(orderId, mutationId) { order ->
            OrderStateMachine.delay(order, actorId ?: "unknown", newEta, eventId = mutationId ?: idGenerator())
        }

    suspend fun ready(orderId: String, actorId: String?, mutationId: String? = null): Result<Order> =
        kitchenMutate(orderId, mutationId) { order ->
            OrderStateMachine.ready(order, actorId ?: "unknown", eventId = mutationId ?: idGenerator())
        }

    suspend fun complete(orderId: String, actorId: String?, mutationId: String? = null): Result<Order> =
        kitchenMutate(orderId, mutationId) { order ->
            OrderStateMachine.complete(order, actorId ?: "unknown", eventId = mutationId ?: idGenerator())
        }

    /**
     * Applies a kitchen mutation idempotently. [mutationId] is used as the
     * timeline event id, so a client that replays its offline outbox with the
     * same id is recognized: the transition is not re-applied and the current
     * order is returned instead (docs/07-sync-engine.md). Mutation ids must be
     * globally unique because they double as timeline event ids.
     */
    private suspend fun kitchenMutate(
        orderId: String,
        mutationId: String?,
        action: (Order) -> Result<Order>,
    ): Result<Order> {
        if (mutationId != null) {
            val current = repository.findById(orderId) ?: return Result.failure(OrderNotFoundException(orderId))
            if (current.timeline.any { it.eventId == mutationId }) {
                return Result.success(current)
            }
        }
        return mutate(orderId, action)
    }

    private suspend fun mutate(orderId: String, action: (Order) -> Result<Order>): Result<Order> {
        val order = repository.findById(orderId) ?: return Result.failure(OrderNotFoundException(orderId))
        return action(order).fold(
            onSuccess = { mutated ->
                val event = mutated.toOrderEvent()
                persist {
                    val saved = repository.update(mutated, order.version, listOf(event))
                    eventBus.publish(event)
                    saved
                }
            },
            onFailure = { Result.failure(it) },
        )
    }

    /** Persists and publishes; maps optimistic-concurrency failures to [Result]. */
    private suspend fun persist(block: suspend () -> Order): Result<Order> = try {
        Result.success(block())
    } catch (e: OrderConcurrencyException) {
        Result.failure(e)
    }
}

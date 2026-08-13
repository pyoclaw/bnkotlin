package com.brodogfyld.api.orders

import com.brodogfyld.api.dto.CreateOrderRequest
import com.brodogfyld.api.payment.FakePaymentProvider
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
                CreateOrderResult.Created(repository.save(order))
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
                        current = OrderStateMachine.transition(
                            current, OrderState.PAYMENT_AUTHORIZED, OrderActorType.PAYMENT_PROVIDER,
                            eventId = idGenerator(), actorId = paymentProvider.name, occurredAt = now,
                        ).getOrElse { return Result.failure(it) }
                        current = OrderStateMachine.transition(
                            current, OrderState.PAID, OrderActorType.PAYMENT_PROVIDER,
                            eventId = idGenerator(), actorId = paymentProvider.name, occurredAt = now,
                        ).getOrElse { return Result.failure(it) }
                        current = OrderStateMachine.transition(
                            current, OrderState.QUEUED, OrderActorType.SYSTEM,
                            eventId = idGenerator(), occurredAt = now,
                        ).getOrElse { return Result.failure(it) }
                        Result.success(repository.save(current))
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

    suspend fun accept(orderId: String, actorId: String?): Result<Order> = mutate(orderId) { order ->
        OrderStateMachine.accept(order, actorId ?: "unknown", eventId = idGenerator())
    }

    suspend fun reject(orderId: String, actorId: String?, reasonCode: String?): Result<Order> = mutate(orderId) { order ->
        OrderStateMachine.reject(order, actorId ?: "unknown", reasonCode ?: "", eventId = idGenerator())
    }

    suspend fun delay(orderId: String, actorId: String?, newEta: Instant): Result<Order> = mutate(orderId) { order ->
        OrderStateMachine.delay(order, actorId ?: "unknown", newEta, eventId = idGenerator())
    }

    suspend fun ready(orderId: String, actorId: String?): Result<Order> = mutate(orderId) { order ->
        OrderStateMachine.ready(order, actorId ?: "unknown", eventId = idGenerator())
    }

    suspend fun complete(orderId: String, actorId: String?): Result<Order> = mutate(orderId) { order ->
        OrderStateMachine.complete(order, actorId ?: "unknown", eventId = idGenerator())
    }

    private suspend fun mutate(orderId: String, action: (Order) -> Result<Order>): Result<Order> {
        val order = repository.findById(orderId) ?: return Result.failure(OrderNotFoundException(orderId))
        return action(order).fold(
            onSuccess = { repository.save(it).let { saved -> Result.success(saved) } },
            onFailure = { Result.failure(it) },
        )
    }
}

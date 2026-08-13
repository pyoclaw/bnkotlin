package com.brodogfyld.domain.payment

import com.brodogfyld.domain.model.Money

/**
 * Backend-side contract that isolates payment providers from order logic.
 *
 * The customer client never marks an order as paid. Only the backend may
 * transition an order into a paid state, and only after verifying a provider
 * response or a signed webhook. Implementations must be idempotent and verify
 * webhook signatures (Slice 5). The webhook signature verification is
 * deliberately left out of this interface until a provider is selected.
 */
interface PaymentProvider {
    val name: String

    suspend fun createPayment(payment: Payment): PaymentResult
    suspend fun verifyPayment(orderId: String, providerReference: String): PaymentVerification
    suspend fun capturePayment(payment: Payment): PaymentResult
    suspend fun refundPayment(payment: Payment, amount: Money): PaymentResult
    suspend fun cancelPayment(payment: Payment): PaymentResult
}

sealed interface PaymentResult {
    data class Success(val payment: Payment) : PaymentResult
    data class Failure(val reason: String) : PaymentResult
}

sealed interface PaymentVerification {
    data class Authorized(val providerReference: String) : PaymentVerification
    data class Failed(val reason: String) : PaymentVerification
}

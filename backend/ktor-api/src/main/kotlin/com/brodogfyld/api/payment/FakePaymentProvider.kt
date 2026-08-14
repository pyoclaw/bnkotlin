package com.brodogfyld.api.payment

import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.payment.Payment
import com.brodogfyld.domain.payment.PaymentProvider
import com.brodogfyld.domain.payment.PaymentResult
import com.brodogfyld.domain.payment.PaymentStatus
import com.brodogfyld.domain.payment.PaymentVerification

/**
 * Deterministic fake provider so the order flow can be exercised end-to-end
 * before a real provider is selected (docs/08-payments.md, Slice 5). The fake
 * never authorizes real money; it only marks payments AUTHORIZED/CAPTURED.
 */
class FakePaymentProvider : PaymentProvider {

    override val name: String = "fake"

    override suspend fun createPayment(payment: Payment): PaymentResult =
        PaymentResult.Success(payment.copy(status = PaymentStatus.AUTHORIZED, providerReference = "fake-${payment.id}"))

    override suspend fun verifyPayment(orderId: String, providerReference: String): PaymentVerification =
        PaymentVerification.Authorized(providerReference)

    override suspend fun capturePayment(payment: Payment): PaymentResult =
        PaymentResult.Success(payment.copy(status = PaymentStatus.CAPTURED))

    override suspend fun refundPayment(payment: Payment, amount: Money): PaymentResult =
        PaymentResult.Success(payment.copy(status = PaymentStatus.REFUNDED))

    override suspend fun cancelPayment(payment: Payment): PaymentResult =
        PaymentResult.Success(payment.copy(status = PaymentStatus.CANCELLED))
}

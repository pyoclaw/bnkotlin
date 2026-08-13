package com.brodogfyld.domain.payment

import com.brodogfyld.domain.model.Currency
import com.brodogfyld.domain.model.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class PaymentStatus { PENDING, AUTHORIZED, CAPTURED, REFUNDED, FAILED, CANCELLED }

/**
 * Payment records stay separate from order records. Raw card data is never
 * stored anywhere in the platform.
 */
@Serializable
data class Payment(
    val id: String,
    val orderId: String,
    val provider: String,
    val amount: Money,
    val currency: Currency = Currency.DKK,
    val providerReference: String? = null,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val createdAt: Instant,
    val updatedAt: Instant,
)

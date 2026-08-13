package com.brodogfyld.domain.model

import kotlinx.serialization.Serializable

/** ISO 4217 currencies supported by the platform. */
@Serializable
enum class Currency(val code: String, val symbol: String) {
    DKK("DKK", "kr"),
    EUR("EUR", "€"),
    USD("USD", "$"),
}

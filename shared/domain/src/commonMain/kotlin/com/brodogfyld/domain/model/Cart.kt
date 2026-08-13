package com.brodogfyld.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CartItem(
    val id: String,
    val productId: String,
    val quantity: Int,
    val selectedModifierOptionIds: Set<String> = emptySet(),
)

@Serializable
data class Cart(
    val id: String,
    val restaurantId: String,
    val currency: Currency = Currency.DKK,
    val items: List<CartItem> = emptyList(),
)

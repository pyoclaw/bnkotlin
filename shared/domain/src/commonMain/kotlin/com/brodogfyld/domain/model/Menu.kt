package com.brodogfyld.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MenuCategory(
    val id: String,
    val name: String,
    val description: String? = null,
    val sortOrder: Int = 0,
)

@Serializable
data class ModifierOption(
    val id: String,
    val name: String,
    val priceDelta: Money = Money.ZERO,
)

@Serializable
data class ModifierGroup(
    val id: String,
    val name: String,
    val required: Boolean = false,
    val minSelection: Int = 0,
    val maxSelection: Int = 1,
    val options: List<ModifierOption> = emptyList(),
)

@Serializable
data class Product(
    val id: String,
    val name: String,
    val description: String? = null,
    val categoryId: String,
    val basePrice: Money,
    val modifierGroups: List<ModifierGroup> = emptyList(),
    val available: Boolean = true,
    val soldOut: Boolean = false,
)

@Serializable
data class Menu(
    val restaurantId: String,
    val categories: List<MenuCategory> = emptyList(),
    val products: List<Product> = emptyList(),
    val version: Long = 0L,
) {
    fun productById(id: String): Product? = products.firstOrNull { it.id == id }

    fun categoryById(id: String): MenuCategory? = categories.firstOrNull { it.id == id }
}

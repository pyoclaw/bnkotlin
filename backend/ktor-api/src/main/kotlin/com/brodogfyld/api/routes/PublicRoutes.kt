package com.brodogfyld.api.routes

import com.brodogfyld.api.AppConfig
import com.brodogfyld.api.dto.RestaurantStatusDto
import com.brodogfyld.domain.model.Menu
import com.brodogfyld.domain.model.MenuCategory
import com.brodogfyld.domain.model.ModifierGroup
import com.brodogfyld.domain.model.ModifierOption
import com.brodogfyld.domain.model.Money
import com.brodogfyld.domain.model.Product
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

fun Application.configurePublicRoutes(config: AppConfig) {
    routing {
        route("/v1") {
            get("/restaurant/status") {
                call.respond(
                    RestaurantStatusDto(
                        id = config.restaurantId,
                        name = config.restaurantName,
                        open = true,
                        acceptsOrders = true,
                        currency = "DKK",
                        timezone = "Europe/Copenhagen",
                    )
                )
            }
            get("/menu") {
                // In-memory sample menu until PostgreSQL persistence lands in
                // Slice 3. The shape already matches the shared Menu model.
                call.respond(sampleMenu(config.restaurantId))
            }
        }
    }
}

private fun sampleMenu(restaurantId: String): Menu = Menu(
    restaurantId = restaurantId,
    categories = listOf(
        MenuCategory("cat-smorrebrod", "Smørrebrød", "Open-faced sandwiches", sortOrder = 1),
        MenuCategory("cat-drinks", "Drinks", "Cold drinks", sortOrder = 2),
    ),
    products = listOf(
        Product(
            id = "smorrebrod-okse",
            name = "Roast beef smørrebrød",
            description = "Roast beef, remoulade, crispy onions and pickles on rye bread.",
            categoryId = "cat-smorrebrod",
            basePrice = Money.of(45, 0),
            modifierGroups = listOf(
                ModifierGroup(
                    id = "bread",
                    name = "Bread",
                    required = true,
                    minSelection = 1,
                    maxSelection = 1,
                    options = listOf(
                        ModifierOption("bread-rye", "Rye bread", Money.ZERO),
                        ModifierOption("bread-white", "White bread", Money(300)),
                    ),
                )
            ),
        ),
        Product(
            id = "smorrebrod-avocado",
            name = "Avocado smørrebrød",
            description = "Smashed avocado, tomato and lemon on sourdough.",
            categoryId = "cat-smorrebrod",
            basePrice = Money.of(42, 0),
        ),
        Product(
            id = "drink-elderflower",
            name = "Elderflower soda",
            description = "Organic elderflower sparkling drink.",
            categoryId = "cat-drinks",
            basePrice = Money.of(28, 0),
        ),
    ),
    version = 1,
)

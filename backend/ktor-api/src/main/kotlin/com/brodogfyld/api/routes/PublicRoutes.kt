package com.brodogfyld.api.routes

import com.brodogfyld.api.AppConfig
import com.brodogfyld.api.dto.RestaurantStatusDto
import com.brodogfyld.api.menu.SampleMenu
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
                call.respond(SampleMenu.menu(config.restaurantId))
            }
        }
    }
}

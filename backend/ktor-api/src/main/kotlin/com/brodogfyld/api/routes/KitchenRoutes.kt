package com.brodogfyld.api.routes

import com.brodogfyld.api.dto.ErrorResponse
import com.brodogfyld.api.dto.KitchenActionRequest
import com.brodogfyld.api.dto.toResponse
import com.brodogfyld.api.orders.OrderService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.Instant

fun Route.kitchenRoutes(service: OrderService, defaultRestaurantId: String) {
    route("/v1/kitchen/orders") {
        get {
            val restaurantId = call.request.queryParameters["restaurantId"] ?: defaultRestaurantId
            call.respond(service.kitchenOrders(restaurantId).map { it.toResponse() })
        }
        post("/{id}/accept") {
            val request = call.receiveNullable<KitchenActionRequest>() ?: KitchenActionRequest()
            call.respondOrderResult(service.accept(call.parameters["id"]!!, request.actorId, request.mutationId))
        }
        post("/{id}/reject") {
            val request = call.receiveNullable<KitchenActionRequest>() ?: KitchenActionRequest()
            if (request.reasonCode.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("reason_required"))
            } else {
                call.respondOrderResult(service.reject(call.parameters["id"]!!, request.actorId, request.reasonCode, request.mutationId))
            }
        }
        post("/{id}/delay") {
            val request = call.receiveNullable<KitchenActionRequest>() ?: KitchenActionRequest()
            val eta = request.newEta?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (eta == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_eta"))
            } else {
                call.respondOrderResult(service.delay(call.parameters["id"]!!, request.actorId, eta, request.mutationId))
            }
        }
        post("/{id}/ready") {
            val request = call.receiveNullable<KitchenActionRequest>() ?: KitchenActionRequest()
            call.respondOrderResult(service.ready(call.parameters["id"]!!, request.actorId, request.mutationId))
        }
        post("/{id}/complete") {
            val request = call.receiveNullable<KitchenActionRequest>() ?: KitchenActionRequest()
            call.respondOrderResult(service.complete(call.parameters["id"]!!, request.actorId, request.mutationId))
        }
    }
}

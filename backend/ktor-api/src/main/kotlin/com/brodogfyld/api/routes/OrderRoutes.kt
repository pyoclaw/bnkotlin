package com.brodogfyld.api.routes

import com.brodogfyld.api.dto.CreateOrderRequest
import com.brodogfyld.api.dto.ErrorResponse
import com.brodogfyld.api.dto.toResponse
import com.brodogfyld.api.orders.CreateOrderResult
import com.brodogfyld.api.orders.OrderService
import com.brodogfyld.domain.model.Menu
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.orderRoutes(service: OrderService, menuProvider: (String) -> Menu) {
    route("/v1/orders") {
        post {
            val request = call.receive<CreateOrderRequest>()
            val menu = menuProvider(request.restaurantId)
            when (val result = service.createOrder(request, menu)) {
                is CreateOrderResult.Invalid ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_order", result.errors.map { it.name }))
                is CreateOrderResult.Created ->
                    call.respond(HttpStatusCode.Created, result.order.toResponse())
            }
        }
        get("/{id}") {
            val order = service.get(call.parameters["id"]!!)
            if (order == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("order_not_found"))
            } else {
                call.respond(order.toResponse())
            }
        }
        post("/{id}/submit") {
            call.respondOrderResult(service.submit(call.parameters["id"]!!))
        }
        post("/{id}/pay") {
            call.respondOrderResult(service.pay(call.parameters["id"]!!))
        }
        get("/{id}/timeline") {
            val timeline = service.timeline(call.parameters["id"]!!)
            if (timeline == null) {
                call.respond(HttpStatusCode.NotFound, ErrorResponse("order_not_found"))
            } else {
                call.respond(timeline.map { it.toResponse() })
            }
        }
    }
}

package com.brodogfyld.api.routes

import com.brodogfyld.api.dto.ErrorResponse
import com.brodogfyld.api.dto.toResponse
import com.brodogfyld.api.orders.OrderConcurrencyException
import com.brodogfyld.api.orders.OrderNotFoundException
import com.brodogfyld.domain.order.Order
import com.brodogfyld.domain.order.OrderTransitionException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.respondOrderResult(result: Result<Order>, success: HttpStatusCode = HttpStatusCode.OK) {
    result.fold(
        onSuccess = { respond(success, it.toResponse()) },
        onFailure = { e ->
            when (e) {
                is OrderNotFoundException -> respond(HttpStatusCode.NotFound, ErrorResponse("order_not_found"))
                is OrderConcurrencyException -> respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("concurrent_modification"),
                )
                is OrderTransitionException -> respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse("invalid_transition", listOf(e.failure.name)),
                )
                else -> respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", listOf(e.message ?: "")))
            }
        },
    )
}

package com.brodogfyld.api.routes

import com.brodogfyld.api.ApiJson
import com.brodogfyld.api.dto.EventEnvelope
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.util.UUID

fun Application.configureWebSocketRoutes() {
    routing {
        webSocket("/v1/ws/orders") {
            // Skeleton for Slice 7. Authentication, subscriptions and event
            // recovery are added when the order event stream exists.
            sendEvent(
                EventEnvelope(
                    eventId = UUID.randomUUID().toString(),
                    type = "connection.established",
                    aggregateId = "orders",
                    version = 0,
                    occurredAt = Instant.now().toString(),
                )
            )
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    sendEvent(
                        EventEnvelope(
                            eventId = UUID.randomUUID().toString(),
                            type = "message.ack",
                            aggregateId = "orders",
                            version = 0,
                            occurredAt = Instant.now().toString(),
                        )
                    )
                }
            }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.sendEvent(envelope: EventEnvelope) {
    send(Frame.Text(ApiJson.encodeToString(envelope)))
}

package com.brodogfyld.api.routes

import com.brodogfyld.api.ApiJson
import com.brodogfyld.api.dto.EventEnvelope
import com.brodogfyld.api.realtime.OrderEvent
import com.brodogfyld.api.realtime.OrderEventBus
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.time.Instant
import java.util.UUID

fun Application.configureWebSocketRoutes(eventBus: OrderEventBus) {
    routing {
        webSocket("/v1/ws/orders") {
            // Optional restaurant scoping until auth lands (Slice 7 follow-up):
            // ?restaurantId=... narrows the stream; omitted streams all orders.
            val restaurantId = call.request.queryParameters["restaurantId"]
            val channel = Channel<OrderEvent>(capacity = 64)
            eventBus.subscribe(channel)

            val job = launch {
                // Sent after subscribe() has registered this client, so a
                // client that has received this frame is guaranteed to see
                // every subsequently emitted event.
                sendEvent(
                    EventEnvelope(
                        eventId = UUID.randomUUID().toString(),
                        type = "connection.established",
                        aggregateId = "orders",
                        version = 0,
                        occurredAt = Instant.now().toString(),
                    )
                )
                for (event in channel) {
                    if (restaurantId == null || event.restaurantId == restaurantId) {
                        sendEvent(event.toEnvelope())
                    }
                }
            }

            try {
                for (frame in incoming) {
                    // Client keepalive/subscribe frames are consumed but not
                    // acted upon yet (auth + explicit subscribe is a follow-up).
                    if (frame is Frame.Text) frame.readText()
                }
            } finally {
                eventBus.unsubscribe(channel)
                channel.close()
                job.cancel()
            }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.sendEvent(envelope: EventEnvelope) {
    send(Frame.Text(ApiJson.encodeToString(envelope)))
}

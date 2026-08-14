package com.brodogfyld.api

import com.brodogfyld.api.dto.CreateOrderRequest
import com.brodogfyld.api.dto.KitchenActionRequest
import com.brodogfyld.api.dto.OrderItemRequest
import com.brodogfyld.api.dto.OrderResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class OrderRealtimeTest {

    private fun testConfig() = AppConfig(
        host = "127.0.0.1",
        port = 8080,
        restaurantId = "test-restaurant",
        restaurantName = "Test Sandwich",
        databaseUrl = null,
        jwtSecret = null,
        jwtIssuer = "test",
        jwtAudience = "test",
        logLevel = "WARN",
    )

    @Test
    fun webSocketStreamsOrderEvents() = testApplication {
        application { module(testConfig()) }
        val http = createClient {
            install(ContentNegotiation) { json(ApiJson) }
        }
        val ws = createClient {
            install(WebSockets)
        }

        ws.webSocket("/v1/ws/orders?restaurantId=test-restaurant") {
            suspend fun next(): String = (incoming.receive() as Frame.Text).readText()

            assertContains(next(), "connection.established")

            val created = http.post("/v1/orders") {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateOrderRequest(
                        restaurantId = "test-restaurant",
                        items = listOf(OrderItemRequest(productId = "smorrebrod-okse", quantity = 1, modifierOptionIds = setOf("bread-rye"))),
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, created.status)
            val draft: OrderResponse = created.body()

            assertContains(next(), "\"type\":\"order.created\"")

            assertEquals(HttpStatusCode.OK, http.post("/v1/orders/${draft.id}/submit").status)
            assertContains(next(), "\"type\":\"order.submitted\"")

            assertEquals(HttpStatusCode.OK, http.post("/v1/orders/${draft.id}/pay").status)
            assertContains(next(), "\"type\":\"order.payment_authorized\"")
            assertContains(next(), "\"type\":\"order.paid\"")
            assertContains(next(), "\"type\":\"order.queued\"")

            val accepted = http.post("/v1/kitchen/orders/${draft.id}/accept") {
                contentType(ContentType.Application.Json)
                setBody(KitchenActionRequest(actorId = "staff-1"))
            }
            assertEquals(HttpStatusCode.OK, accepted.status)

            val acceptedFrame = next()
            assertContains(acceptedFrame, "\"type\":\"order.accepted\"")
            assertContains(acceptedFrame, draft.id)
        }
    }
}

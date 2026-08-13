package com.brodogfyld.api

import com.brodogfyld.api.dto.CreateOrderRequest
import com.brodogfyld.api.dto.KitchenActionRequest
import com.brodogfyld.api.dto.OrderItemRequest
import com.brodogfyld.api.dto.OrderResponse
import com.brodogfyld.api.dto.TimelineEventResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderFlowTest {

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

    private fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json(ApiJson) }
    }

    @Test
    fun customerOrderFlowsToKitchenAcceptanceAndBack() = testApplication {
        application { module(testConfig()) }
        val client = jsonClient()

        val created = client.post("/v1/orders") {
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
        assertEquals("DRAFT", draft.state)
        assertEquals(4500L, draft.totalMinor)

        val submitted = client.post("/v1/orders/${draft.id}/submit")
        assertEquals(HttpStatusCode.OK, submitted.status)
        assertEquals("PENDING_PAYMENT", submitted.body<OrderResponse>().state)

        val paid = client.post("/v1/orders/${draft.id}/pay")
        assertEquals(HttpStatusCode.OK, paid.status)
        assertEquals("QUEUED", paid.body<OrderResponse>().state)

        val queue: List<OrderResponse> = client.get("/v1/kitchen/orders?restaurantId=test-restaurant").body()
        assertEquals(listOf(draft.id), queue.map { it.id })

        val accepted = client.post("/v1/kitchen/orders/${draft.id}/accept") {
            contentType(ContentType.Application.Json)
            setBody(KitchenActionRequest(actorId = "staff-1"))
        }
        assertEquals(HttpStatusCode.OK, accepted.status)
        assertEquals("ACCEPTED", accepted.body<OrderResponse>().state)

        val fromCustomer: OrderResponse = client.get("/v1/orders/${draft.id}").body()
        assertEquals("ACCEPTED", fromCustomer.state)
        assertEquals(5L, fromCustomer.version) // submit + authorize + paid + queued + accept

        val timeline: List<TimelineEventResponse> = client.get("/v1/orders/${draft.id}/timeline").body()
        assertEquals(5, timeline.size) // submit + authorize + paid + queued + accept
        assertEquals("ACCEPTED", timeline.last().newState)
    }

    @Test
    fun rejectsOrdersWithUnknownProducts() = testApplication {
        application { module(testConfig()) }
        val client = jsonClient()

        val response = client.post("/v1/orders") {
            contentType(ContentType.Application.Json)
            setBody(
                CreateOrderRequest(
                    restaurantId = "test-restaurant",
                    items = listOf(OrderItemRequest(productId = "does-not-exist", quantity = 1)),
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun rejectsIllegalKitchenTransitions() = testApplication {
        application { module(testConfig()) }
        val client = jsonClient()

        val created = client.post("/v1/orders") {
            contentType(ContentType.Application.Json)
            setBody(CreateOrderRequest(restaurantId = "test-restaurant", items = listOf(OrderItemRequest("smorrebrod-avocado", 1))))
        }
        val draft: OrderResponse = created.body()

        // Completing a DRAFT order is not a legal transition.
        val complete = client.post("/v1/kitchen/orders/${draft.id}/complete") {
            contentType(ContentType.Application.Json)
            setBody(KitchenActionRequest(actorId = "staff-1"))
        }
        assertEquals(HttpStatusCode.Conflict, complete.status)
    }
}

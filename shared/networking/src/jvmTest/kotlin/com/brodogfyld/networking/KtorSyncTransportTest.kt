package com.brodogfyld.networking

import com.brodogfyld.sync.MutationResult
import com.brodogfyld.sync.OutboxMutation
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KtorSyncTransportTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        val engine = MockEngine { request -> handler(request) }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
    }

    private fun transport(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): KtorSyncTransport = KtorSyncTransport(client(handler), "https://api.example.test")

    private fun mutation(action: String = "accept", id: String = "m-1", orderId: String = "o1") = OutboxMutation(
        id = id,
        action = action,
        orderId = orderId,
        restaurantId = "r1",
        baseVersion = 4L,
        actorId = "staff-1",
    )

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    // --- applyMutation -------------------------------------------------------

    @Test
    fun applyMutationReturnsAppliedOnSuccess() = runBlocking {
        val t = transport { request ->
            assertEquals("/v1/kitchen/orders/o1/accept", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            respond(
                content = """{"id":"o1","restaurantId":"r1","state":"ACCEPTED","totalMinor":9000,"version":5,"createdAt":"2026-01-01T12:00:00Z","updatedAt":"2026-01-01T12:00:00Z"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val result = t.applyMutation(mutation())
        assertTrue(result is MutationResult.Applied)
        assertEquals("ACCEPTED", result.order.state)
        assertEquals(5L, result.order.version)
    }

    @Test
    fun applyMutationMapsConflictToStale() = runBlocking {
        val t = transport {
            respond(
                content = """{"code":"concurrent_modification"}""",
                status = HttpStatusCode.Conflict,
                headers = jsonHeaders,
            )
        }

        val result = t.applyMutation(mutation())
        assertTrue(result is MutationResult.Stale)
        assertEquals("concurrent_modification", result.code)
    }

    @Test
    fun applyMutationMapsNotFoundToStale() = runBlocking {
        val t = transport {
            respond(
                content = """{"code":"order_not_found"}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders,
            )
        }

        val result = t.applyMutation(mutation())
        assertTrue(result is MutationResult.Stale)
        assertEquals("order_not_found", result.code)
    }

    @Test
    fun applyMutationMapsServerErrorToRetryable() = runBlocking {
        val t = transport {
            respond(content = "", status = HttpStatusCode.ServiceUnavailable)
        }

        val result = t.applyMutation(mutation())
        assertTrue(result is MutationResult.Retryable)
    }

    @Test
    fun applyMutationMapsTransportFailureToRetryable() = runBlocking {
        val t = transport { throw RuntimeException("connection reset") }

        val result = t.applyMutation(mutation())
        assertTrue(result is MutationResult.Retryable)
        assertEquals("connection reset", result.message)
    }

    @Test
    fun applyMutationRejectsUnknownActionAsStale() = runBlocking {
        val t = transport { error("should not be called") }

        val result = t.applyMutation(mutation(action = "nonsense"))
        assertTrue(result is MutationResult.Stale)
        assertEquals("unknown_action", result.code)
    }

    @Test
    fun applyMutationRoutesDelayAction() = runBlocking {
        val t = transport { request ->
            assertEquals("/v1/kitchen/orders/o2/delay", request.url.encodedPath)
            respond(
                content = """{"id":"o2","restaurantId":"r1","state":"DELAYED","totalMinor":9000,"version":6,"createdAt":"2026-01-01T12:00:00Z","updatedAt":"2026-01-01T12:00:00Z"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val result = t.applyMutation(
            mutation(action = "delay", id = "m-2", orderId = "o2").copy(newEta = "2026-01-01T13:00:00Z"),
        )
        assertTrue(result is MutationResult.Applied)
        assertEquals("DELAYED", result.order.state)
    }

    // --- fetchOrder ----------------------------------------------------------

    @Test
    fun fetchOrderReturnsOrderOnSuccess() = runBlocking {
        val t = transport { request ->
            assertEquals("/v1/orders/o1", request.url.encodedPath)
            respond(
                content = """{"id":"o1","restaurantId":"r1","state":"READY","totalMinor":9000,"version":7,"createdAt":"2026-01-01T12:00:00Z","updatedAt":"2026-01-01T12:30:00Z","items":[]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val order = t.fetchOrder("o1")
        assertEquals("READY", order?.state)
        assertEquals(7L, order?.version)
    }

    @Test
    fun fetchOrderReturnsNullOnNotFound() = runBlocking {
        val t = transport {
            respond(
                content = """{"code":"order_not_found"}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders,
            )
        }

        assertEquals(null, t.fetchOrder("missing"))
    }

    // --- fetchKitchenOrders --------------------------------------------------

    @Test
    fun fetchKitchenOrdersPassesRestaurantAndParsesList() = runBlocking {
        val t = transport { request ->
            assertEquals("/v1/kitchen/orders", request.url.encodedPath)
            assertEquals("r1", request.url.parameters["restaurantId"])
            respond(
                content = """[
                    {"id":"a","restaurantId":"r1","state":"QUEUED","totalMinor":1000,"version":1,"createdAt":"2026-01-01T12:00:00Z","updatedAt":"2026-01-01T12:00:00Z"},
                    {"id":"b","restaurantId":"r1","state":"PREPARING","totalMinor":2000,"version":3,"createdAt":"2026-01-01T12:00:00Z","updatedAt":"2026-01-01T12:05:00Z"}
                ]""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }

        val orders = t.fetchKitchenOrders("r1")
        assertEquals(listOf("a", "b"), orders.map { it.id })
        assertEquals("PREPARING", orders[1].state)
    }

    @Test
    fun fetchKitchenOrdersReturnsEmptyOnFailure() = runBlocking {
        val t = transport { throw RuntimeException("boom") }

        assertEquals(emptyList(), t.fetchKitchenOrders("r1"))
    }
}

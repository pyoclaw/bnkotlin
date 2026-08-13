package com.brodogfyld.api

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ApplicationTest {

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
    fun healthLiveRespondsOk() = testApplication {
        application { module(testConfig()) }
        val response = client.get("/health/live")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"ok\"")
    }

    @Test
    fun healthReadyRespondsReady() = testApplication {
        application { module(testConfig()) }
        val response = client.get("/health/ready")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"ready\":true")
    }

    @Test
    fun restaurantStatusReturnsBrand() = testApplication {
        application { module(testConfig()) }
        val response = client.get("/v1/restaurant/status")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "Test Sandwich")
    }

    @Test
    fun menuReturnsProducts() = testApplication {
        application { module(testConfig()) }
        val response = client.get("/v1/menu")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "smorrebrod-okse")
    }

    @Test
    fun webSocketEstablishesAndSendsWelcome() = testApplication {
        application { module(testConfig()) }
        val wsClient = createClient {
            install(WebSockets)
        }
        wsClient.webSocket("/v1/ws/orders") {
            val frame = incoming.receive()
            val text = (frame as? Frame.Text)?.readText()
            assertTrue(text != null && text.contains("connection.established"), "unexpected frame: $text")
        }
    }
}

package com.brodogfyld.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Default JVM client for [KtorSyncTransport]. Uses the CIO engine and JSON
 * content negotiation; `ignoreUnknownKeys` lets the client DTOs
 * (`RemoteOrder`) ignore server fields they do not model (e.g. line items).
 */
fun createDefaultHttpClient(): HttpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            },
        )
    }
}

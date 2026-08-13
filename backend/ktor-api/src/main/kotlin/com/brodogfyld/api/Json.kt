package com.brodogfyld.api

import kotlinx.serialization.json.Json

/** Shared JSON configuration for responses, requests and WebSocket frames. */
val ApiJson: Json = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
    encodeDefaults = true
}

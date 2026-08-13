package com.brodogfyld.api

/**
 * Server configuration read from environment variables. Secrets are never
 * logged or serialized. See docs/21-configuration.md.
 */
data class AppConfig(
    val host: String,
    val port: Int,
    val restaurantId: String,
    val restaurantName: String,
    val databaseUrl: String?,
    val jwtSecret: String?,
    val jwtIssuer: String,
    val jwtAudience: String,
    val logLevel: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig = AppConfig(
            host = "0.0.0.0",
            port = env["SERVER_PORT"]?.toIntOrNull() ?: 8080,
            restaurantId = env["RESTAURANT_ID"] ?: "brod-og-fyld",
            restaurantName = env["RESTAURANT_NAME"] ?: "Brød & Fyld",
            databaseUrl = env["DATABASE_URL"],
            jwtSecret = env["JWT_SECRET"],
            jwtIssuer = env["JWT_ISSUER"] ?: "brod-og-fyld",
            jwtAudience = env["JWT_AUDIENCE"] ?: "brod-og-fyld",
            logLevel = env["LOG_LEVEL"] ?: "INFO",
        )
    }
}

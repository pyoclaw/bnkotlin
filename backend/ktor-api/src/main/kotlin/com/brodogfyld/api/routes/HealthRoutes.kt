package com.brodogfyld.api.routes

import com.brodogfyld.api.dto.HealthStatus
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureHealthRoutes() {
    routing {
        get("/health/live") {
            call.respond(HealthStatus(status = "ok"))
        }
        get("/health/ready") {
            // Readiness will verify PostgreSQL connectivity once the database
            // layer lands (Slice 3).
            call.respond(HealthStatus(status = "ok", ready = true))
        }
    }
}

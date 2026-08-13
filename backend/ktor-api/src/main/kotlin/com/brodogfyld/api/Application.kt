package com.brodogfyld.api

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.brodogfyld.api.db.Database
import com.brodogfyld.api.menu.SampleMenu
import com.brodogfyld.api.orders.InMemoryOrderRepository
import com.brodogfyld.api.orders.OrderRepository
import com.brodogfyld.api.orders.OrderService
import com.brodogfyld.api.orders.PostgresOrderRepository
import com.brodogfyld.api.routes.configureHealthRoutes
import com.brodogfyld.api.routes.configurePublicRoutes
import com.brodogfyld.api.routes.configureWebSocketRoutes
import com.brodogfyld.api.routes.kitchenRoutes
import com.brodogfyld.api.routes.orderRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import org.slf4j.LoggerFactory

fun main() {
    val config = AppConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = config.host) {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: AppConfig = AppConfig.fromEnv()) {
    val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    root.level = Level.toLevel(config.logLevel, Level.INFO)

    install(ContentNegotiation) {
        json(ApiJson)
    }
    install(WebSockets)

    val database = config.databaseUrl?.let { Database(it) }
    val repository: OrderRepository = if (database != null) {
        PostgresOrderRepository(database)
    } else {
        log.warn("DATABASE_URL not set; using in-memory order repository (development only)")
        InMemoryOrderRepository()
    }
    val orderService = OrderService(repository)
    monitor.subscribe(ApplicationStopped) { database?.close() }

    configureHealthRoutes()
    configurePublicRoutes(config)
    configureWebSocketRoutes()
    routing {
        orderRoutes(orderService) { restaurantId -> SampleMenu.menu(restaurantId) }
        kitchenRoutes(orderService, config.restaurantId)
    }
}

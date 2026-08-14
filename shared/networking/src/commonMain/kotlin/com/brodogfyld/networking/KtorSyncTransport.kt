package com.brodogfyld.networking

import com.brodogfyld.sync.KitchenAction
import com.brodogfyld.sync.MutationResult
import com.brodogfyld.sync.OutboxMutation
import com.brodogfyld.sync.RemoteOrder
import com.brodogfyld.sync.SyncTransport
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/** Wire body for the backend kitchen-action endpoints (docs/06-api.md). */
@Serializable
internal data class KitchenActionBody(
    val actorId: String? = null,
    val reasonCode: String? = null,
    val newEta: String? = null,
    val mutationId: String? = null,
)

/** Backend error envelope (matches `ErrorResponse` in `backend:ktor-api`). */
@Serializable
internal data class ErrorBody(
    val code: String,
    val details: List<String> = emptyList(),
)

/**
 * Real [SyncTransport] over the backend HTTP API (docs/07-sync-engine.md).
 *
 * HTTP is used for recovery and outbox replay; live events arrive over the
 * WebSocket (`/v1/ws/orders`) and are wired separately with the kitchen UI.
 * The transport is deliberately a thin adapter: all reconciliation behaviour
 * lives in `shared:sync`, so this class only maps requests/responses onto the
 * [SyncTransport] contract.
 *
 * [client] must be configured with JSON content negotiation (see
 * `createDefaultHttpClient`); it is injected so tests can use a MockEngine.
 */
class KtorSyncTransport(
    private val client: HttpClient,
    private val baseUrl: String,
) : SyncTransport {

    override suspend fun applyMutation(mutation: OutboxMutation): MutationResult {
        val action = KitchenAction.fromWire(mutation.action)
            ?: return MutationResult.Stale("unknown_action")
        val body = KitchenActionBody(
            actorId = mutation.actorId,
            reasonCode = mutation.reasonCode,
            newEta = mutation.newEta,
            mutationId = mutation.id,
        )
        return try {
            val response = client.post("$baseUrl/v1/kitchen/orders/${mutation.orderId}/${action.wire}") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            when {
                response.status.isSuccess() -> MutationResult.Applied(response.body())
                // 5xx (and any transport-level failure) is retryable; a 4xx is
                // a permanent rejection (stale/conflict/not-found) to reconcile.
                response.status.value in 500..599 ->
                    MutationResult.Retryable("server error ${response.status.value}")
                else -> MutationResult.Stale(errorCode(response))
            }
        } catch (e: Throwable) {
            MutationResult.Retryable(e.message ?: "network error")
        }
    }

    override suspend fun fetchOrder(orderId: String): RemoteOrder? = try {
        val response = client.get("$baseUrl/v1/orders/$orderId")
        if (response.status.isSuccess()) response.body() else null
    } catch (e: Throwable) {
        null
    }

    override suspend fun fetchKitchenOrders(restaurantId: String): List<RemoteOrder> = try {
        val response = client.get("$baseUrl/v1/kitchen/orders") {
            parameter("restaurantId", restaurantId)
        }
        if (response.status.isSuccess()) response.body() else emptyList()
    } catch (e: Throwable) {
        emptyList()
    }

    private suspend fun errorCode(response: HttpResponse): String =
        runCatching { response.body<ErrorBody>().code }
            .getOrElse { response.status.description.lowercase().replace(' ', '_') }
}

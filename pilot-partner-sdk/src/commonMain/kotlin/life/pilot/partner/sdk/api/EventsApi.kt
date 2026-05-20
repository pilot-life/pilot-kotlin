package life.pilot.partner.sdk.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import life.pilot.partner.sdk.model.EventDetail
import life.pilot.partner.sdk.model.EventList
import life.pilot.partner.sdk.model.InventorySnapshot

class EventsApi internal constructor(private val http: HttpClient) {

    suspend fun list(
        startsAfter: String? = null,
        cursor: String? = null,
        limit: Int? = null,
    ): EventList = http.get("events") {
        startsAfter?.let { parameter("startsAfter", it) }
        cursor?.let { parameter("cursor", it) }
        limit?.let { parameter("limit", it) }
    }.body()

    suspend fun get(eventUuid: String): EventDetail =
        http.get("events/$eventUuid").body()

    /**
     * Inventory snapshot with [ETag][InventoryResponse.etag] handling.
     *
     * The KMP rewrite replaced Retrofit's `Response<InventorySnapshot>`
     * wrapper with this hand-rolled equivalent — callers need the
     * status code (for the `304` short-circuit) and the `ETag` header
     * (to round-trip on the next call) without dragging a JVM-only
     * Retrofit dep into iOS.
     */
    suspend fun inventory(
        eventUuid: String,
        ifNoneMatch: String? = null,
    ): InventoryResponse {
        val response: HttpResponse = http.get("events/$eventUuid/inventory") {
            ifNoneMatch?.let { headers { append("If-None-Match", it) } }
        }
        val parsed = if (response.status.value == 200) response.body<InventorySnapshot>() else null
        return InventoryResponse(
            code = response.status.value,
            etag = response.headers["ETag"],
            body = parsed,
        )
    }
}

/**
 * Lightweight replacement for Retrofit's `Response<T>` exposing the bits
 * partners actually need from `GET /events/{uuid}/inventory`: status
 * (for the 304 short-circuit), the ETag (to round-trip), and the parsed
 * body when present.
 */
data class InventoryResponse(
    val code: Int,
    val etag: String?,
    val body: InventorySnapshot?,
) {
    val isSuccessful: Boolean get() = code in 200..299
}

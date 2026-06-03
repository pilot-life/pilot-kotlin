package life.pilot.partner.sdk.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import life.pilot.partner.sdk.http.partnerDelete
import life.pilot.partner.sdk.http.partnerGet
import life.pilot.partner.sdk.http.partnerPost
import life.pilot.partner.sdk.model.CheckoutRequest
import life.pilot.partner.sdk.model.CheckoutResponse
import life.pilot.partner.sdk.model.ClaimCreateRequest
import life.pilot.partner.sdk.model.ClaimCreateResponse
import life.pilot.partner.sdk.model.ClaimStatus

class ClaimsApi internal constructor(private val http: HttpClient) {

    suspend fun create(
        eventUuid: String,
        idempotencyKey: String,
        body: ClaimCreateRequest,
    ): ClaimCreateResponse = http.partnerPost("events/$eventUuid/claims") {
        headers { append("Idempotency-Key", idempotencyKey) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body()

    suspend fun status(claimId: String): ClaimStatus =
        http.partnerGet("claims/$claimId/status").body()

    suspend fun release(claimId: String) {
        http.partnerDelete("claims/$claimId")
    }

    suspend fun checkout(
        claimId: String,
        idempotencyKey: String,
        body: CheckoutRequest,
    ): CheckoutResponse = http.partnerPost("claims/$claimId/checkout") {
        headers { append("Idempotency-Key", idempotencyKey) }
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body()
}

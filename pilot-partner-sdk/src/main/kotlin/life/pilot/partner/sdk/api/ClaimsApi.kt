package life.pilot.partner.sdk.api

import life.pilot.partner.sdk.model.ClaimCreateRequest
import life.pilot.partner.sdk.model.ClaimCreateResponse
import life.pilot.partner.sdk.model.ClaimStatus
import life.pilot.partner.sdk.model.CheckoutRequest
import life.pilot.partner.sdk.model.CheckoutResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ClaimsApi {
    @POST("events/{eventUuid}/claims")
    suspend fun create(
        @Path("eventUuid") eventUuid: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ClaimCreateRequest,
    ): ClaimCreateResponse

    @GET("claims/{claimId}/status")
    suspend fun status(@Path("claimId") claimId: String): ClaimStatus

    @DELETE("claims/{claimId}")
    suspend fun release(@Path("claimId") claimId: String)

    @POST("claims/{claimId}/checkout")
    suspend fun checkout(
        @Path("claimId") claimId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CheckoutRequest,
    ): CheckoutResponse
}

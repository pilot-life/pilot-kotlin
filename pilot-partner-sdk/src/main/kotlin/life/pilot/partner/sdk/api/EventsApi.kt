package life.pilot.partner.sdk.api

import life.pilot.partner.sdk.model.EventDetail
import life.pilot.partner.sdk.model.EventList
import life.pilot.partner.sdk.model.InventorySnapshot
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface EventsApi {
    @GET("events")
    suspend fun list(
        @Query("startsAfter") startsAfter: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): EventList

    @GET("events/{eventUuid}")
    suspend fun get(@Path("eventUuid") eventUuid: String): EventDetail

    /**
     * `Response<InventorySnapshot>` instead of the raw model so callers can
     * inspect the `ETag` header and the 304 status (body will be null on 304).
     */
    @GET("events/{eventUuid}/inventory")
    suspend fun inventory(
        @Path("eventUuid") eventUuid: String,
        @Header("If-None-Match") ifNoneMatch: String? = null,
    ): Response<InventorySnapshot>
}

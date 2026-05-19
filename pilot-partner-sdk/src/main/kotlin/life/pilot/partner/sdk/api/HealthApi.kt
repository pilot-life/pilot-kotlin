package life.pilot.partner.sdk.api

import life.pilot.partner.sdk.model.Health
import retrofit2.http.GET

interface HealthApi {
    @GET("health")
    suspend fun check(): Health
}

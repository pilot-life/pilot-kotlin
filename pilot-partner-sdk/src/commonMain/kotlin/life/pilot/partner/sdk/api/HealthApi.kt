package life.pilot.partner.sdk.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import life.pilot.partner.sdk.http.partnerGet
import life.pilot.partner.sdk.model.Health

class HealthApi internal constructor(private val http: HttpClient) {
    suspend fun check(): Health = http.partnerGet("health").body()
}

package life.pilot.partner.sdk.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import life.pilot.partner.sdk.http.partnerGet
import life.pilot.partner.sdk.model.OrderDetail

class OrdersApi internal constructor(private val http: HttpClient) {
    suspend fun get(orderUuid: String): OrderDetail =
        http.partnerGet("orders/$orderUuid").body()
}

package life.pilot.partner.sdk.api

import life.pilot.partner.sdk.model.OrderDetail
import retrofit2.http.GET
import retrofit2.http.Path

interface OrdersApi {
    @GET("orders/{orderUuid}")
    suspend fun get(@Path("orderUuid") orderUuid: String): OrderDetail
}

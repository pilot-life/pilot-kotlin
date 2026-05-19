package life.pilot.partner.sdk

import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockWebServer

internal fun mockClient(server: MockWebServer): PilotPartnerClient =
    PilotPartnerClient.builder()
        .apiKey("test-key")
        .organizationUuid("11111111-1111-1111-1111-111111111111")
        .baseUrl(server.url("/").toString())
        .logging(HttpLoggingInterceptor.Level.NONE)
        .maxRateLimitRetries(2)
        .build()

package life.pilot.partner.sdk

enum class PartnerEnvironment(val baseUrl: String) {
    PRODUCTION("https://api.pilot.life/partner/v1/"),
    SANDBOX("https://sandbox.api.pilot.life/partner/v1/"),
    STAGING("https://staging.api.pilot.life/partner/v1/"),
    DEV("https://dev.api.pilot.life/partner/v1/"),
    ;

    companion object {
        fun custom(baseUrl: String): String =
            if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    }
}

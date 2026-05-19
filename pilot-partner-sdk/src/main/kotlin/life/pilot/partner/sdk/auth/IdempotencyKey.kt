package life.pilot.partner.sdk.auth

import java.util.UUID

object IdempotencyKey {
    /** Generate a fresh UUIDv4 idempotency key. */
    fun generate(): String = UUID.randomUUID().toString()
}

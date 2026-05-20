package life.pilot.partner.sdk.auth

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
object IdempotencyKey {
    /** Generate a fresh UUIDv4 idempotency key. */
    fun generate(): String = Uuid.random().toString()
}

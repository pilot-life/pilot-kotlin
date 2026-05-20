package life.pilot.partner.sdk.webhooks

import kotlinx.datetime.Clock
import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import kotlin.experimental.and

/**
 * HMAC-SHA256 verifier for partner webhooks (ADR-0008).
 *
 * The backend sends the signature as `t=<unix-seconds>,v1=<hex-hmac>` in a
 * header (e.g. `X-Pilot-Signature`). The signed string is `"$timestamp.$body"`.
 * Tolerance is configurable to mitigate replay (default 5 minutes).
 *
 * Kotlin Multiplatform: uses KotlinCrypto's HMAC-SHA256 implementation
 * so the same code runs on Android, JVM, and iOS.
 */
class HmacVerifier(
    private val secret: String,
    private val toleranceSeconds: Long = 5 * 60,
    private val clock: () -> Long = { Clock.System.now().epochSeconds },
) {
    /**
     * @return true when the signature header matches and is within tolerance.
     */
    fun verify(rawBody: String, signatureHeader: String?): Boolean {
        if (signatureHeader.isNullOrBlank()) return false
        val parts = signatureHeader.split(',')
            .mapNotNull { it.trim().split('=', limit = 2).takeIf { p -> p.size == 2 } }
            .associate { it[0] to it[1] }
        val timestamp = parts["t"]?.toLongOrNull() ?: return false
        val signature = parts["v1"] ?: return false

        if (kotlin.math.abs(clock() - timestamp) > toleranceSeconds) return false

        val expected = sign("$timestamp.$rawBody")
        return constantTimeEquals(expected, signature)
    }

    fun sign(payload: String): String {
        val mac = HmacSHA256(secret.encodeToByteArray())
        return mac.doFinal(payload.encodeToByteArray()).toHex()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.encodeToByteArray()
        val bBytes = b.encodeToByteArray()
        if (aBytes.size != bBytes.size) return false
        var diff = 0
        for (i in aBytes.indices) {
            diff = diff or (aBytes[i].toInt() xor bBytes[i].toInt())
        }
        return diff == 0
    }

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        for (i in indices) {
            val v = (this[i] and 0xFF.toByte()).toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return out.concatToString()
    }

    private companion object {
        val HEX = "0123456789abcdef".toCharArray()
    }
}

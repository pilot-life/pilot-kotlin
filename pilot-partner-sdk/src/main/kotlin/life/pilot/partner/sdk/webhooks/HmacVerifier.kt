package life.pilot.partner.sdk.webhooks

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.and

/**
 * HMAC-SHA256 verifier for partner webhooks (ADR-0008).
 *
 * The backend sends the signature as `t=<unix-seconds>,v1=<hex-hmac>` in a
 * header (e.g. `X-Pilot-Signature`). The signed string is `"$timestamp.$body"`.
 * Tolerance is configurable to mitigate replay (default 5 minutes).
 */
class HmacVerifier(
    private val secret: String,
    private val toleranceSeconds: Long = 5 * 60,
    private val clock: () -> Long = { System.currentTimeMillis() / 1_000 },
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
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        val aBytes = a.toByteArray(Charsets.UTF_8)
        val bBytes = b.toByteArray(Charsets.UTF_8)
        if (aBytes.size != bBytes.size) return false
        return MessageDigest.isEqual(aBytes, bBytes)
    }

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        for (i in indices) {
            val v = (this[i] and 0xFF.toByte()).toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        val HEX = "0123456789abcdef".toCharArray()
    }
}

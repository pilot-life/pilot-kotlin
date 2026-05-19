package life.pilot.partner.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal object DateFormat {
    private val DAY = DateTimeFormatter.ofPattern("MMMM dd, yyyy")
    private val DAY_RANGE_START = DateTimeFormatter.ofPattern("MMMM dd")
    private val DAY_RANGE_END = DateTimeFormatter.ofPattern("dd, yyyy")
    private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM")
    private val DOM = DateTimeFormatter.ofPattern("dd")
    private val YEAR = DateTimeFormatter.ofPattern("yyyy")

    fun parseIso(iso: String, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.parse(iso).atZone(zone).toLocalDate()

    /** Mirrors EventCard.tsx: `MMMM DD, YYYY` (single-day) or `MMMM DD - DD, YYYY` (multi-day). */
    fun formatEventDateRange(startIso: String, endIso: String, zone: ZoneId = ZoneId.systemDefault()): String {
        val start = parseIso(startIso, zone)
        val end = parseIso(endIso, zone)
        return if (start == end) {
            DAY.format(start)
        } else {
            "${DAY_RANGE_START.format(start)} - ${DAY_RANGE_END.format(end)}"
        }
    }

    /** Mirrors TicketBookingCard.styles' MMM / DD / YYYY stack. */
    data class TripleDate(val month: String, val day: String, val year: String)

    fun toTripleDate(iso: String, zone: ZoneId = ZoneId.systemDefault()): TripleDate {
        val d = parseIso(iso, zone)
        return TripleDate(
            month = MONTH_SHORT.format(d).uppercase(),
            day = DOM.format(d),
            year = YEAR.format(d),
        )
    }
}

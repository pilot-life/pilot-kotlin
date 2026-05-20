package life.pilot.partner.ui.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * KMP date formatting helpers backed by `kotlinx-datetime`.
 *
 * Pilot dates over the wire are ISO-8601 instants. We render in the
 * caller's [TimeZone] (defaults to `TimeZone.currentSystemDefault()`).
 *
 * Implementation note: `kotlinx-datetime` doesn't ship a Locale-aware
 * formatter API yet (KMP gap that's tracked in upstream). We render
 * month names from a hand-rolled English-locale map — same set as the
 * pilot-frontend output. Localized formatting on iOS would be a
 * follow-up using `NSDateFormatter` via expect/actual.
 */
internal object DateFormat {

    /** Mirrors EventCard.tsx: `MMMM DD, YYYY` (single-day) or `MMMM DD - DD, YYYY` (multi-day). */
    fun formatEventDateRange(startIso: String, endIso: String, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val start = parseIso(startIso, zone)
        val end = parseIso(endIso, zone)
        return if (start == end) {
            "${monthName(start.month)} ${zeroPad(start.dayOfMonth)}, ${start.year}"
        } else {
            "${monthName(start.month)} ${zeroPad(start.dayOfMonth)} - ${zeroPad(end.dayOfMonth)}, ${end.year}"
        }
    }

    /** Mirrors TicketBookingCard.styles' MMM / DD / YYYY stack. */
    data class TripleDate(val month: String, val day: String, val year: String)

    fun toTripleDate(iso: String, zone: TimeZone = TimeZone.currentSystemDefault()): TripleDate {
        val d = parseIso(iso, zone)
        return TripleDate(
            month = monthShort(d.month).uppercase(),
            day = zeroPad(d.dayOfMonth),
            year = d.year.toString(),
        )
    }

    private fun parseIso(iso: String, zone: TimeZone): LocalDate =
        Instant.parse(iso).toLocalDateTime(zone).date

    private fun zeroPad(n: Int): String = if (n < 10) "0$n" else n.toString()

    private fun monthName(m: Month): String = when (m) {
        Month.JANUARY -> "January"
        Month.FEBRUARY -> "February"
        Month.MARCH -> "March"
        Month.APRIL -> "April"
        Month.MAY -> "May"
        Month.JUNE -> "June"
        Month.JULY -> "July"
        Month.AUGUST -> "August"
        Month.SEPTEMBER -> "September"
        Month.OCTOBER -> "October"
        Month.NOVEMBER -> "November"
        Month.DECEMBER -> "December"
        else -> m.name
    }

    private fun monthShort(m: Month): String = monthName(m).take(3)
}

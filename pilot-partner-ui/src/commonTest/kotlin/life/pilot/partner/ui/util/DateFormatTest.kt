package life.pilot.partner.ui.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.datetime.TimeZone
import kotlin.test.Test

class DateFormatTest {
    private val utc: TimeZone = TimeZone.UTC

    @Test fun `single-day event renders as MMMM DD YYYY`() {
        val out = DateFormat.formatEventDateRange(
            startIso = "2026-06-15T18:00:00Z",
            endIso = "2026-06-15T22:00:00Z",
            zone = utc,
        )
        assertThat(out).isEqualTo("June 15, 2026")
    }

    @Test fun `multi-day event renders MMMM DD - DD YYYY`() {
        val out = DateFormat.formatEventDateRange(
            startIso = "2026-07-04T18:00:00Z",
            endIso = "2026-07-06T02:00:00Z",
            zone = utc,
        )
        assertThat(out).isEqualTo("July 04 - 06, 2026")
    }

    @Test fun `triple date returns uppercase month, day, year`() {
        val td = DateFormat.toTripleDate("2026-03-09T12:00:00Z", utc)
        assertThat(td.month).isEqualTo("MAR")
        assertThat(td.day).isEqualTo("09")
        assertThat(td.year).isEqualTo("2026")
    }
}

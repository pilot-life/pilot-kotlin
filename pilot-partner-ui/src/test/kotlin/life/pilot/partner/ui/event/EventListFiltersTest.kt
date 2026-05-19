package life.pilot.partner.ui.event

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import life.pilot.partner.sdk.model.EventListItem
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class EventListFiltersTest {
    private val utc = ZoneId.of("UTC")

    private fun event(
        uuid: String,
        name: String,
        startIso: String,
        endIso: String,
        venue: String? = null,
    ) = EventListItem(
        eventUUID = uuid,
        name = name,
        startDate = startIso,
        endDate = endIso,
        venueName = venue,
    )

    private val summer = event("s", "Summer Bash", "2026-06-15T18:00:00Z", "2026-06-15T22:00:00Z", "Echo Venue")
    private val fall = event("f", "Fall Fest", "2026-09-20T18:00:00Z", "2026-09-22T02:00:00Z", "Pier 7")
    private val winter = event("w", "Winter Gala", "2026-12-31T20:00:00Z", "2027-01-01T03:00:00Z", "Echo Venue")
    private val all = listOf(summer, fall, winter)

    @Test fun `empty filter returns events unchanged`() {
        assertThat(EventListFilters().applyClientSide(all, utc)).isEqualTo(all)
    }

    @Test fun `isEmpty reports correctly`() {
        assertThat(EventListFilters().isEmpty).isEqualTo(true)
        assertThat(EventListFilters(query = "x").isEmpty).isEqualTo(false)
        assertThat(EventListFilters(startsAfter = LocalDate.now()).isEmpty).isEqualTo(false)
        assertThat(EventListFilters(endsBefore = LocalDate.now()).isEmpty).isEqualTo(false)
    }

    @Test fun `query matches event name case-insensitively`() {
        val result = EventListFilters(query = "BASH").applyClientSide(all, utc)
        assertThat(result.map { it.eventUUID }).containsOnly("s")
    }

    @Test fun `query matches venue name`() {
        val result = EventListFilters(query = "echo").applyClientSide(all, utc)
        assertThat(result.map { it.eventUUID }).containsExactlyInAnyOrder("s", "w")
    }

    @Test fun `query is trimmed`() {
        val result = EventListFilters(query = "  bash  ").applyClientSide(all, utc)
        assertThat(result.map { it.eventUUID }).containsOnly("s")
    }

    @Test fun `query with no matches returns empty list`() {
        assertThat(EventListFilters(query = "nope").applyClientSide(all, utc)).isEmpty()
    }

    @Test fun `endsBefore filters events whose endDate is strictly after`() {
        val result = EventListFilters(endsBefore = LocalDate.of(2026, 10, 1))
            .applyClientSide(all, utc)
        assertThat(result.map { it.eventUUID }).containsExactlyInAnyOrder("s", "f")
    }

    @Test fun `endsBefore allows same-day endings`() {
        val result = EventListFilters(endsBefore = LocalDate.of(2026, 6, 15))
            .applyClientSide(all, utc)
        assertThat(result.map { it.eventUUID }).containsOnly("s")
    }

    @Test fun `query and endsBefore compose with AND semantics`() {
        val result = EventListFilters(
            query = "fall",
            endsBefore = LocalDate.of(2026, 10, 1),
        ).applyClientSide(all, utc)
        assertThat(result.map { it.eventUUID }).containsOnly("f")
    }

    @Test fun `unparseable endDate keeps the event in the list under endsBefore`() {
        // Defensive: don't filter out events we can't classify.
        val mystery = event("m", "Mystery", "2026-01-01T00:00:00Z", "not-a-date")
        val result = EventListFilters(endsBefore = LocalDate.of(2026, 6, 15))
            .applyClientSide(listOf(mystery), utc)
        assertThat(result.map { it.eventUUID }).containsOnly("m")
    }

    @Test fun `default sort orders by start date ascending`() {
        val result = EventListFilters().applyClientSide(listOf(winter, summer, fall), utc)
        assertThat(result.map { it.eventUUID }).isEqualTo(listOf("s", "f", "w"))
    }

    @Test fun `start date descending sort`() {
        val result = EventListFilters(sortBy = EventSortBy.START_DATE_DESC)
            .applyClientSide(listOf(summer, fall, winter), utc)
        assertThat(result.map { it.eventUUID }).isEqualTo(listOf("w", "f", "s"))
    }

    @Test fun `name ascending sort is case-insensitive`() {
        val a = event("a", "alpha", "2026-06-01T00:00:00Z", "2026-06-01T00:00:00Z")
        val b = event("b", "Beta", "2026-05-01T00:00:00Z", "2026-05-01T00:00:00Z")
        val c = event("c", "gamma", "2026-07-01T00:00:00Z", "2026-07-01T00:00:00Z")
        val result = EventListFilters(sortBy = EventSortBy.NAME_ASC)
            .applyClientSide(listOf(c, a, b), utc)
        assertThat(result.map { it.eventUUID }).isEqualTo(listOf("a", "b", "c"))
    }

    @Test fun `name descending sort`() {
        val result = EventListFilters(sortBy = EventSortBy.NAME_DESC)
            .applyClientSide(listOf(summer, fall, winter), utc)
        // Summer Bash, Fall Fest, Winter Gala → Winter > Summer > Fall
        assertThat(result.map { it.eventUUID }).isEqualTo(listOf("w", "s", "f"))
    }

    @Test fun `sort runs after filtering`() {
        // query reduces to summer + winter (both Echo Venue), then DESC by date.
        val result = EventListFilters(query = "echo", sortBy = EventSortBy.START_DATE_DESC)
            .applyClientSide(listOf(summer, fall, winter), utc)
        assertThat(result.map { it.eventUUID }).isEqualTo(listOf("w", "s"))
    }
}

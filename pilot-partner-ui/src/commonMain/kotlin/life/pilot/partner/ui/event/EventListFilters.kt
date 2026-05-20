package life.pilot.partner.ui.event

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import life.pilot.partner.sdk.model.EventListItem

/**
 * Inputs that narrow an event list. Wire-shape decisions:
 *   - [startsAfter] maps to the partner-API's `startsAfter` query param.
 *     Changing it should trigger a refetch from page 1 with the new value.
 *   - [endsBefore] and [query] are NOT supported server-side. They filter
 *     in-memory from whatever the API returned.
 *
 * The data class is intentionally serializable-shaped (only `LocalDate`s
 * and strings) so partners can persist it in their own savedState bag.
 */
enum class EventSortBy(val label: String) {
    START_DATE_ASC("Date ↑"),
    START_DATE_DESC("Date ↓"),
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
}

data class EventListFilters(
    val query: String = "",
    val startsAfter: LocalDate? = null,
    val endsBefore: LocalDate? = null,
    val sortBy: EventSortBy = EventSortBy.START_DATE_ASC,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && startsAfter == null && endsBefore == null && sortBy == EventSortBy.START_DATE_ASC

    /**
     * Apply [query], [endsBefore], and [sortBy] to [events] — all
     * client-side. Does NOT re-apply [startsAfter] because the API has
     * already done that. Idempotent and deterministic.
     */
    fun applyClientSide(events: List<EventListItem>, zone: TimeZone = TimeZone.currentSystemDefault()): List<EventListItem> {
        val needle = query.trim().lowercase()
        val endsBeforeBound: LocalDate? = endsBefore
        val filtered = events.filter { evt ->
            val matchesText = needle.isBlank() ||
                evt.name.lowercase().contains(needle) ||
                (evt.venueName?.lowercase()?.contains(needle) == true)
            val matchesEnd = endsBeforeBound == null || run {
                val end = parseEventDate(evt.endDate, zone) ?: return@run true
                end <= endsBeforeBound
            }
            matchesText && matchesEnd
        }
        return when (sortBy) {
            EventSortBy.START_DATE_ASC -> filtered.sortedBy { it.startDate }
            EventSortBy.START_DATE_DESC -> filtered.sortedByDescending { it.startDate }
            EventSortBy.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            EventSortBy.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
        }
    }

    companion object {
        private fun parseEventDate(iso: String, zone: TimeZone): LocalDate? {
            // API returns ISO-8601 instants ("2026-06-15T20:00:00Z"). We also
            // tolerate already-local dates ("2026-06-15") for partner code
            // that pre-normalizes.
            runCatching { return Instant.parse(iso).toLocalDateTime(zone).date }
            runCatching { return LocalDate.parse(iso) }
            return null
        }
    }
}

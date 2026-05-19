package life.pilot.partner.ui.event

import life.pilot.partner.sdk.model.EventListItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
data class EventListFilters(
    val query: String = "",
    val startsAfter: LocalDate? = null,
    val endsBefore: LocalDate? = null,
) {
    val isEmpty: Boolean
        get() = query.isBlank() && startsAfter == null && endsBefore == null

    /**
     * Apply [query] and [endsBefore] to [events] — the two client-side
     * filters. Does NOT re-apply [startsAfter] because the API has
     * already done that. Idempotent and deterministic.
     */
    fun applyClientSide(events: List<EventListItem>, zone: ZoneId = ZoneId.systemDefault()): List<EventListItem> {
        if (query.isBlank() && endsBefore == null) return events
        val needle = query.trim().lowercase()
        return events.filter { evt ->
            val matchesText = needle.isBlank() ||
                evt.name.lowercase().contains(needle) ||
                (evt.venueName?.lowercase()?.contains(needle) == true)
            val matchesEnd = endsBefore == null || run {
                val end = parseEventDate(evt.endDate, zone) ?: return@run true
                !end.isAfter(endsBefore)
            }
            matchesText && matchesEnd
        }
    }

    companion object {
        private fun parseEventDate(iso: String, zone: ZoneId): LocalDate? {
            // API returns ISO-8601 instants ("2026-06-15T20:00:00Z"). We also
            // tolerate already-local dates ("2026-06-15") for partner code
            // that pre-normalizes.
            runCatching { return Instant.parse(iso).atZone(zone).toLocalDate() }
            runCatching { return LocalDate.parse(iso) }
            return null
        }
    }
}

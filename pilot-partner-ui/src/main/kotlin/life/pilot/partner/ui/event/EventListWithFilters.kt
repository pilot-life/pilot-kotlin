package life.pilot.partner.ui.event

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import life.pilot.partner.sdk.model.EventListItem
import java.time.ZoneId

/**
 * Drop-in replacement for [EventList] that adds a search field + date
 * filter chips above the list. Renders [state.events] filtered by
 * [filters] in-memory.
 *
 * Refetch semantics (caller's responsibility, but the contract is):
 *   - `query` and `endsBefore` filter in-memory — no callback required.
 *   - `startsAfter` is the only filter the API actually supports. When
 *     it changes, the caller (typically a ViewModel) should clear the
 *     current results and refetch from page 1 passing
 *     `startsAfter = filters.startsAfter`. This component does NOT
 *     re-invoke `onLoadMore` on startsAfter changes — the ViewModel
 *     needs to observe the filter state and react explicitly.
 */
@Composable
fun EventListWithFilters(
    state: EventListState,
    filters: EventListFilters,
    onFiltersChange: (EventListFilters) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    zone: ZoneId = ZoneId.systemDefault(),
    imageUrlFor: (EventListItem) -> String? = { null },
    ticketsRemainingFor: (EventListItem) -> Int? = { null },
    addressLineFor: (EventListItem) -> String? = { null },
    onEventClick: (EventListItem) -> Unit = {},
    onLoadMore: () -> Unit,
    onRetry: () -> Unit = onLoadMore,
) {
    val filteredEvents by remember(state.events, filters) {
        derivedStateOf { filters.applyClientSide(state.events, zone) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(EventListWithFiltersTestTags.Root),
    ) {
        EventSearchFilterBar(
            filters = filters,
            onFiltersChange = onFiltersChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        // When client-side filters remove every loaded result but more
        // pages are available, hint the user instead of showing a stale
        // empty state.
        val noClientResults = state.events.isNotEmpty() && filteredEvents.isEmpty()

        EventList(
            state = state.copy(events = filteredEvents),
            modifier = Modifier.weight(1f),
            contentPadding = contentPadding,
            zone = zone,
            imageUrlFor = imageUrlFor,
            ticketsRemainingFor = ticketsRemainingFor,
            addressLineFor = addressLineFor,
            onEventClick = onEventClick,
            onLoadMore = onLoadMore,
            onRetry = onRetry,
        )

        if (noClientResults && state.error == null && !state.isLoading) {
            Text(
                text = "No events match your filters in the current page." +
                    if (state.nextCursor != null) " Scroll to load more, or relax the filters." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(16.dp)
                    .testTag(EventListWithFiltersTestTags.NoMatches),
            )
        }
    }
}

object EventListWithFiltersTestTags {
    const val Root = "EventListWithFilters.root"
    const val NoMatches = "EventListWithFilters.noMatches"
}

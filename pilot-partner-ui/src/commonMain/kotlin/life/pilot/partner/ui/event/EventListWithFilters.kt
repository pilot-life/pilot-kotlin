package life.pilot.partner.ui.event

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import life.pilot.partner.sdk.model.EventListItem
import kotlinx.datetime.TimeZone

/**
 * Drop-in replacement for [EventList] that adds:
 *   - a search field + date filter chips + sort dropdown above the list
 *   - swipe-to-refresh via Material 3 [PullToRefreshBox]
 *
 * Renders [state.events] filtered + sorted by [filters] in-memory.
 *
 * Refetch / refresh semantics:
 *   - `query`, `endsBefore`, `sortBy` filter / order in-memory.
 *   - `startsAfter` is the only filter the partner API supports. When
 *     it changes, the caller should clear results and refetch from
 *     page 1; the shipped [life.pilot.partner.ui.viewmodel.EventsViewModel]
 *     does this automatically.
 *   - Swipe-down invokes [onRefresh]. The component shows the refresh
 *     spinner while [state.isLoading] is true and the list is empty —
 *     so a refresh that returns nothing still visibly resolves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListWithFilters(
    state: EventListState,
    filters: EventListFilters,
    onFiltersChange: (EventListFilters) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    zone: TimeZone = TimeZone.currentSystemDefault(),
    imageUrlFor: (EventListItem) -> String? = { it.imageUrl },
    ticketsRemainingFor: (EventListItem) -> Int? = { null },
    addressLineFor: (EventListItem) -> String? = { null },
    onEventClick: (EventListItem) -> Unit = {},
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit = onLoadMore,
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

        val noClientResults = state.events.isNotEmpty() && filteredEvents.isEmpty()

        // PullToRefreshBox wraps the list and shows the spinner while the
        // refresh is in flight. We tie the spinner to state.isLoading
        // when the page is being reloaded from scratch (events empty) so
        // it visibly resolves whether or not new events come back.
        val isRefreshing = state.isLoading && state.events.isEmpty()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .testTag(EventListWithFiltersTestTags.PullToRefresh),
            state = rememberPullToRefreshState(),
        ) {
            EventList(
                state = state.copy(events = filteredEvents),
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                zone = zone,
                imageUrlFor = imageUrlFor,
                ticketsRemainingFor = ticketsRemainingFor,
                addressLineFor = addressLineFor,
                onEventClick = onEventClick,
                onLoadMore = onLoadMore,
                onRetry = onRetry,
            )
        }

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
    const val PullToRefresh = "EventListWithFilters.pullToRefresh"
}

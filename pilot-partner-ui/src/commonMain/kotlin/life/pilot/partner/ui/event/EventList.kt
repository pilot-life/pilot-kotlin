package life.pilot.partner.ui.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import life.pilot.partner.sdk.model.EventListItem
import kotlinx.datetime.TimeZone

/**
 * Lazy list of [EventListItemCard]s, with cursor pagination triggered when
 * the user scrolls near the end. Loading and error states are baked in so
 * partners get a single drop-in component.
 */
@Composable
fun EventList(
    state: EventListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    zone: TimeZone = TimeZone.currentSystemDefault(),
    imageUrlFor: (EventListItem) -> String? = { it.imageUrl },
    ticketsRemainingFor: (EventListItem) -> Int? = { null },
    addressLineFor: (EventListItem) -> String? = { null },
    onEventClick: (EventListItem) -> Unit = {},
    onLoadMore: () -> Unit,
    onRetry: () -> Unit = onLoadMore,
) {
    LaunchedEffect(state.events.isEmpty() && !state.isLoading && state.error == null) {
        if (state.events.isEmpty() && !state.isLoading && state.error == null) {
            onLoadMore()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(EventListTestTags.Root),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = state.events, key = { it.eventUUID }) { event ->
            EventListItemCard(
                event = event,
                imageUrl = imageUrlFor(event),
                addressLine = addressLineFor(event),
                ticketsRemaining = ticketsRemainingFor(event),
                zone = zone,
                onClick = { onEventClick(event) },
            )
        }

        if (state.isLoading) {
            item {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (state.error != null) {
            item {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onRetry) { Text("Retry") }
                }
            }
        } else if (state.hasMore) {
            item {
                LaunchedEffect(state.events.size) { onLoadMore() }
            }
        }
    }
}

data class EventListState(
    val events: List<EventListItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
) {
    val hasMore: Boolean get() = nextCursor != null
}

object EventListTestTags {
    const val Root = "EventList.root"
}

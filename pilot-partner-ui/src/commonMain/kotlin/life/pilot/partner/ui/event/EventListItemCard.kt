package life.pilot.partner.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import life.pilot.partner.sdk.model.EventListItem
import life.pilot.partner.ui.util.DateFormat
import kotlinx.datetime.TimeZone

/**
 * Mirrors `EventCard.tsx`:
 *   - banner image
 *   - "{venue} Presents" chip at the top of the body when present
 *   - title
 *   - address (rendered only when supplied via [addressLine] — the
 *     partner-API surface does not include addresses today)
 *   - date row with calendar icon
 *   - footer chip "Only {n} Tickets Left" when [ticketsRemaining] is set
 */
@Composable
fun EventListItemCard(
    event: EventListItem,
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    addressLine: String? = null,
    ticketsRemaining: Int? = null,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme

    val cardModifier = modifier
        .fillMaxWidth()
        .testTag(EventListItemCardTestTags.Root)
        .let { if (onClick != null) it.clickable { onClick() } else it }

    Card(
        modifier = cardModifier,
        colors = CardDefaults.outlinedCardColors(),
        elevation = CardDefaults.outlinedCardElevation(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "${event.name} banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Event,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (event.featuredEvent) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Featured") },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = colors.tertiaryContainer,
                                disabledLabelColor = colors.onTertiaryContainer,
                            ),
                        )
                    }
                    event.venueName?.takeIf { it.isNotBlank() }?.let { venue ->
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("$venue Presents") },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = colors.primaryContainer,
                                disabledLabelColor = colors.onPrimaryContainer,
                            ),
                        )
                    }
                }

                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!addressLine.isNullOrBlank()) {
                    Text(
                        text = addressLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Event,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = DateFormat.formatEventDateRange(event.startDate, event.endDate, zone),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.primary,
                    )
                }
            }

            if (ticketsRemaining != null && ticketsRemaining > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.tertiaryContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = ticketsRemainingLabel(ticketsRemaining),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.onTertiaryContainer,
                    )
                }
            }
        }
    }
}

private fun ticketsRemainingLabel(n: Int): String =
    if (n == 1) "Only 1 Ticket Left" else "Only $n Tickets Left"

object EventListItemCardTestTags {
    const val Root = "EventListItemCard.root"
}

package life.pilot.partner.ui.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import life.pilot.partner.sdk.model.TicketTypeRow

/**
 * Single ticket-type row with a quantity stepper. Mirrors the ticket
 * selection rows on `TicketSelectHero` from pilot-frontend's event page.
 *
 * Renders a "Sold Out" badge when [TicketTypeRow.soldOut] or when
 * [TicketTypeRow.remaining] <= 0 and disables the stepper.
 */
@Composable
fun TicketTypeRowItem(
    ticket: TicketTypeRow,
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxPerOrder: Int = 50,
    currencyPrefix: String = "$",
) {
    val effectivelySoldOut = ticket.soldOut || ticket.remaining <= 0
    val cap = minOf(maxPerOrder, ticket.remaining)

    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TicketTypeRowItemTestTags.rootFor(ticket.ticketTypeUUID)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ticket.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ticket.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "$currencyPrefix${ticket.price}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!effectivelySoldOut && ticket.remaining in 1..10) {
                    Text(
                        text = "Only ${ticket.remaining} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            if (effectivelySoldOut) {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .testTag(TicketTypeRowItemTestTags.soldOutFor(ticket.ticketTypeUUID)),
                ) {
                    Text(
                        text = "Sold Out",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(
                        onClick = { onQuantityChange((quantity - 1).coerceAtLeast(0)) },
                        enabled = quantity > 0,
                        modifier = Modifier.testTag(TicketTypeRowItemTestTags.decFor(ticket.ticketTypeUUID)),
                    ) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Decrease")
                    }
                    Text(
                        text = quantity.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .width(24.dp)
                            .testTag(TicketTypeRowItemTestTags.qtyFor(ticket.ticketTypeUUID)),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(
                        onClick = { onQuantityChange((quantity + 1).coerceAtMost(cap)) },
                        enabled = quantity < cap,
                        modifier = Modifier.testTag(TicketTypeRowItemTestTags.incFor(ticket.ticketTypeUUID)),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "Increase")
                    }
                }
            }
        }
    }
}

object TicketTypeRowItemTestTags {
    fun rootFor(uuid: String) = "TicketTypeRowItem.root.$uuid"
    fun incFor(uuid: String) = "TicketTypeRowItem.inc.$uuid"
    fun decFor(uuid: String) = "TicketTypeRowItem.dec.$uuid"
    fun qtyFor(uuid: String) = "TicketTypeRowItem.qty.$uuid"
    fun soldOutFor(uuid: String) = "TicketTypeRowItem.soldOut.$uuid"
}

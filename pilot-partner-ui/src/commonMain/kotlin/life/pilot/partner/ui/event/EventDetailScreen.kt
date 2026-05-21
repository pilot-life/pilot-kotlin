package life.pilot.partner.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import life.pilot.partner.sdk.model.EventDetail
import life.pilot.partner.sdk.model.InventorySnapshot
import life.pilot.partner.sdk.model.TicketTypeRow
import life.pilot.partner.ui.util.DateFormat
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import kotlinx.datetime.TimeZone

/**
 * Single-screen event detail that mirrors pilot-frontend's
 * `EventPage` + `TicketSelectHero`:
 *
 *   - hero image (16:9)
 *   - event name + date + description
 *   - one [TicketTypeRowItem] per ticket type from
 *     `InventorySnapshot.ticketTypes`
 *   - sticky-footer "Continue to Checkout" button that surfaces the
 *     running subtotal — disabled until something is selected
 *
 * Selection state is hoisted into the [selection] parameter so the screen
 * is testable, and ViewModels can replace the in-memory map with their own.
 */
@Composable
fun EventDetailScreen(
    event: EventDetail,
    inventory: InventorySnapshot?,
    selection: TicketSelectionState = rememberTicketSelectionState(),
    modifier: Modifier = Modifier,
    /**
     * Hero image URL. Defaults to [EventDetail.imageUrl] returned by the
     * partner API. Pass an explicit value to override (CMS resolver,
     * partner-side asset map, etc.).
     */
    imageUrl: String? = event.imageUrl,
    isLoading: Boolean = false,
    error: String? = null,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    currencyPrefix: String = "$",
    /**
     * Predicate deciding whether the "Request to Attend" button shows.
     * Defaults to `event.rta?.enabled == true` — partner API emits this
     * on `EventDetail.rta` directly. Override only if you want custom
     * gating (e.g. hide RTA in some app build flavors).
     */
    isRequestToAttendEnabled: (EventDetail) -> Boolean = { it.rta?.enabled == true },
    /**
     * Source for the registration ticket types listed under the
     * "Registration" section. Defaults to `inventory.registrationTicketTypes`
     * — the partner API emits this array on the inventory response.
     * Override only if your app injects registration tickets from a
     * different source.
     */
    registrationTicketTypesFor: (InventorySnapshot) -> List<life.pilot.partner.sdk.model.TicketTypeRow> = { it.registrationTicketTypes },
    onRequestToAttend: (EventDetail) -> Unit = {},
    onRegister: (List<TicketSelection>) -> Unit = {},
    onContinue: (List<TicketSelection>) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val showRta = isRequestToAttendEnabled(event)
    val registrationTickets = inventory?.let(registrationTicketTypesFor) ?: emptyList()
    Box(modifier = modifier.fillMaxSize().testTag(EventDetailScreenTestTags.Root)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            HeroImage(imageUrl = imageUrl, contentDescription = event.name)

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = DateFormat.formatEventDateRange(event.startDate, event.endDate, zone),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                event.venueName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                (event.shortDescription ?: event.description)?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (showRta) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                EventActionsBar(
                    event = event,
                    onRequestToAttend = onRequestToAttend,
                    modifier = Modifier.padding(16.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Tickets",
                    style = MaterialTheme.typography.titleLarge,
                )
                when {
                    isLoading && inventory == null -> Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    error != null -> Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = onRetry) { Text("Retry") }
                    }

                    inventory != null && inventory.ticketTypes.isEmpty() -> Text(
                        text = "No tickets available right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    inventory != null -> inventory.ticketTypes.forEach { tt ->
                        TicketTypeRowItem(
                            ticket = tt,
                            quantity = selection.quantityOf(tt.ticketTypeUUID),
                            onQuantityChange = { q -> selection.set(tt.ticketTypeUUID, q) },
                            currencyPrefix = currencyPrefix,
                        )
                    }
                }

                if (registrationTickets.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Registration",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag(EventDetailScreenTestTags.RegistrationHeader),
                    )
                    registrationTickets.forEach { tt ->
                        TicketTypeRowItem(
                            ticket = tt,
                            quantity = selection.quantityOf(tt.ticketTypeUUID),
                            onQuantityChange = { q -> selection.set(tt.ticketTypeUUID, q) },
                            currencyPrefix = currencyPrefix,
                        )
                    }
                    val regSelections = selection.toSelections(registrationTickets)
                    Button(
                        onClick = { onRegister(regSelections) },
                        enabled = regSelections.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(EventDetailScreenTestTags.Register),
                    ) {
                        Text("Register")
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }

        if (inventory != null && inventory.ticketTypes.isNotEmpty()) {
            Footer(
                subtotal = selection.subtotal(inventory.ticketTypes),
                count = selection.totalCount(),
                currencyPrefix = currencyPrefix,
                onContinue = { onContinue(selection.toSelections(inventory.ticketTypes)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .testTag(EventDetailScreenTestTags.Footer),
            )
        }
    }
}

/**
 * Promoted action row sitting between the description and the ticket list.
 *
 * Currently surfaces only the "Request to Attend" CTA — registration
 * uses a dedicated section because it shares the ticket-selection flow.
 *
 * Mirrors pilot-frontend's `TicketSelectHero` action layout: a single
 * full-width primary button when RTA is enabled on the event.
 */
@Composable
private fun EventActionsBar(
    event: EventDetail,
    onRequestToAttend: (EventDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { onRequestToAttend(event) },
        modifier = modifier
            .fillMaxWidth()
            .testTag(EventDetailScreenTestTags.RequestToAttend),
    ) {
        Text("Request to Attend")
    }
}

@Composable
private fun HeroImage(imageUrl: String?, contentDescription: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Event,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@Composable
private fun Footer(
    subtotal: BigDecimal,
    count: Int,
    currencyPrefix: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Text(
                    text = if (count == 0) "No tickets selected" else "$count selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$currencyPrefix${subtotal.toPlainString()}",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Button(
                onClick = onContinue,
                enabled = count > 0,
                modifier = Modifier.testTag(EventDetailScreenTestTags.Continue),
            ) {
                Text("Continue to Checkout")
            }
        }
    }
}

data class TicketSelection(
    val ticketType: TicketTypeRow,
    val quantity: Int,
)

class TicketSelectionState internal constructor(
    private val backing: SnapshotStateMap<String, Int>,
) {
    fun quantityOf(uuid: String): Int = backing[uuid] ?: 0

    fun set(uuid: String, quantity: Int) {
        if (quantity <= 0) backing.remove(uuid) else backing[uuid] = quantity
    }

    fun totalCount(): Int = backing.values.sum()

    fun subtotal(tickets: List<TicketTypeRow>): BigDecimal {
        if (backing.isEmpty()) return BigDecimal.ZERO
        val priceByUuid = tickets.associate { it.ticketTypeUUID to BigDecimal.parseString(it.price) }
        return backing.entries.fold(BigDecimal.ZERO) { acc, (uuid, qty) ->
            val price = priceByUuid[uuid] ?: BigDecimal.ZERO
            acc + (price * BigDecimal.fromInt(qty))
        }
    }

    fun toSelections(tickets: List<TicketTypeRow>): List<TicketSelection> {
        val byUuid = tickets.associateBy { it.ticketTypeUUID }
        return backing.entries.mapNotNull { (uuid, qty) ->
            val tt = byUuid[uuid] ?: return@mapNotNull null
            TicketSelection(tt, qty).takeIf { qty > 0 }
        }
    }
}

@Composable
fun rememberTicketSelectionState(): TicketSelectionState {
    val map = remember { mutableStateMapOf<String, Int>() }
    return remember(map) { TicketSelectionState(map) }
}

object EventDetailScreenTestTags {
    const val Root = "EventDetailScreen.root"
    const val Footer = "EventDetailScreen.footer"
    const val Continue = "EventDetailScreen.continue"
    const val RequestToAttend = "EventDetailScreen.requestToAttend"
    const val Register = "EventDetailScreen.register"
    const val RegistrationHeader = "EventDetailScreen.registrationHeader"
}

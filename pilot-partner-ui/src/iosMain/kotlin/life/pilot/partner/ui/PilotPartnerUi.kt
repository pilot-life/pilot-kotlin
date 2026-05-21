package life.pilot.partner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import life.pilot.partner.sdk.PartnerEnvironment
import life.pilot.partner.sdk.PilotPartnerClient
import life.pilot.partner.ui.event.EventDetailScreen
import life.pilot.partner.ui.event.EventListWithFilters
import life.pilot.partner.ui.theme.PilotPartnerTheme
import life.pilot.partner.ui.viewmodel.EventsViewModel
import platform.UIKit.UIViewController

/**
 * iOS entry point: a factory that returns a `UIViewController` hosting
 * the full Compose flow (event list → event detail with RTA &
 * registration), ready to be embedded in SwiftUI via
 * `UIViewControllerRepresentable`.
 *
 * Hosts list/detail navigation internally (no Swift-side nav routing
 * needed) so partners get a one-call "drop me into the events
 * experience" entrypoint.
 *
 * The factory takes connection **primitives** rather than a
 * `PilotPartnerClient` instance — see the KDoc on [eventsScreen] for the
 * KMP framework-boundary reasoning.
 *
 * ```swift
 * struct EventsScreen: UIViewControllerRepresentable {
 *     func makeUIViewController(context: Context) -> UIViewController {
 *         PilotPartnerUi.shared.eventsScreen(
 *             apiKey: "...",
 *             organizationUuid: "...",
 *             environment: "SANDBOX",
 *             baseUrl: nil,
 *             gatewaySecret: nil,
 *         )
 *     }
 *     func updateUIViewController(_ vc: UIViewController, context: Context) {}
 * }
 * ```
 */
object PilotPartnerUi {
    val shared: PilotPartnerUi = this

    fun eventsScreen(
        apiKey: String,
        organizationUuid: String,
        environment: String = "SANDBOX",
        baseUrl: String? = null,
        gatewaySecret: String? = null,
    ): UIViewController {
        val env = runCatching { PartnerEnvironment.valueOf(environment.uppercase()) }
            .getOrDefault(PartnerEnvironment.SANDBOX)

        val client = PilotPartnerClient.builder()
            .apiKey(apiKey)
            .organizationUuid(organizationUuid)
            .also { b ->
                gatewaySecret?.let { b.gatewaySecret(it) }
                if (!baseUrl.isNullOrBlank()) b.baseUrl(baseUrl) else b.environment(env)
            }
            .build()

        return ComposeUIViewController {
            PilotPartnerTheme {
                EventsScreenRoot(client = client)
            }
        }
    }
}

/**
 * Two-screen navigation inside one Compose UIViewController: list ↔ detail.
 * Lifted out of the factory for readability; not part of the public iOS
 * API surface (Swift consumers always come through [PilotPartnerUi.shared.eventsScreen]).
 */
@Composable
private fun EventsScreenRoot(client: PilotPartnerClient) {
    val vm = remember { EventsViewModel(client) }
    val events by vm.events.collectAsState()
    val filters by vm.filters.collectAsState()
    val detail by vm.detail.collectAsState()
    val inventory by vm.inventory.collectAsState()
    val detailLoading by vm.detailLoading.collectAsState()
    val detailError by vm.detailError.collectAsState()

    var selectedEventUuid: String? by remember { mutableStateOf(null) }

    if (selectedEventUuid != null && detail != null) {
        EventDetailScreen(
            event = detail!!,
            inventory = inventory,
            isLoading = detailLoading,
            error = detailError,
            onRetry = { selectedEventUuid?.let(vm::loadEvent) },
            onRequestToAttend = { /* partners hook into this from SwiftUI via a custom screen */ },
            onRegister = { /* same — registration flow lives partner-side */ },
            onContinue = { /* same — paid checkout lives partner-side */ },
        )
    } else {
        EventListWithFilters(
            state = events,
            filters = filters,
            onFiltersChange = vm::updateFilters,
            onLoadMore = { vm.loadMoreEvents() },
            onRefresh = { vm.refreshEvents() },
            onEventClick = { item ->
                selectedEventUuid = item.eventUUID
                vm.loadEvent(item.eventUUID)
            },
        )
    }
}

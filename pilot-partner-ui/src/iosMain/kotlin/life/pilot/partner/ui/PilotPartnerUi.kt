package life.pilot.partner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
 *
 * Navigation switches as soon as the user taps an event (not when the
 * detail call completes), so the loading window is visible and a
 * failed call doesn't leave the user stranded on the list with no
 * feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val onBack: () -> Unit = { selectedEventUuid = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedEventUuid != null) "Event details" else "Events") },
                navigationIcon = {
                    if (selectedEventUuid != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (selectedEventUuid != null) {
            val loadedDetail = detail
            when {
                loadedDetail != null && loadedDetail.eventUUID == selectedEventUuid -> EventDetailScreen(
                    event = loadedDetail,
                    inventory = inventory,
                    isLoading = detailLoading,
                    error = detailError,
                    modifier = Modifier.padding(padding),
                    onRetry = { selectedEventUuid?.let(vm::loadEvent) },
                    onRequestToAttend = { /* partners hook into this from SwiftUI via a custom screen */ },
                    onRegister = { /* same — registration flow lives partner-side */ },
                    onContinue = { /* same — paid checkout lives partner-side */ },
                )

                detailError != null -> ErrorPlaceholder(
                    message = detailError ?: "Couldn't load event",
                    onRetry = { selectedEventUuid?.let(vm::loadEvent) },
                    modifier = Modifier.padding(padding),
                )

                else -> LoadingPlaceholder(modifier = Modifier.padding(padding))
            }
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
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorPlaceholder(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        IconButton(onClick = onRetry) {
            Text("Retry", style = MaterialTheme.typography.labelLarge)
        }
    }
}

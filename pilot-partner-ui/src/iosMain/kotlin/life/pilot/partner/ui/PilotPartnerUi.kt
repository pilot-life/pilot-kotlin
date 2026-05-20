package life.pilot.partner.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import life.pilot.partner.sdk.PartnerEnvironment
import life.pilot.partner.sdk.PilotPartnerClient
import life.pilot.partner.ui.event.EventListWithFilters
import life.pilot.partner.ui.theme.PilotPartnerTheme
import life.pilot.partner.ui.viewmodel.EventsViewModel
import platform.UIKit.UIViewController

/**
 * iOS entry point: a factory that returns a `UIViewController` hosting
 * the Compose events screen, ready to be embedded in SwiftUI via
 * `UIViewControllerRepresentable`.
 *
 * The factory takes connection **primitives** rather than a
 * `PilotPartnerClient` instance. This is deliberate: on iOS, Kotlin/Native
 * builds the SDK and the UI library as separate frameworks, and types
 * cross the framework boundary as *distinct* Swift types even when they
 * come from the same Kotlin class (the SDK's `PilotPartnerClient` becomes
 * `PilotPartnerSdk.PilotPartnerClient` AND `PilotPartnerUi.PilotPartnerClient`,
 * which can't be interchanged in Swift). Taking primitives lets the UI
 * framework construct its own client internally — Swift never has to bridge
 * an SDK-typed object into a UI-typed parameter.
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
            val vm = remember { EventsViewModel(client) }
            val state by vm.events.collectAsState()
            val filters by vm.filters.collectAsState()

            PilotPartnerTheme {
                EventListWithFilters(
                    state = state,
                    filters = filters,
                    onFiltersChange = vm::updateFilters,
                    onLoadMore = { vm.loadMoreEvents() },
                    onRefresh = { vm.refreshEvents() },
                )
            }
        }
    }
}

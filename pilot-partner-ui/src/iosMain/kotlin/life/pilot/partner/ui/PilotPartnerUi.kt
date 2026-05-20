package life.pilot.partner.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
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
 * ```swift
 * struct EventsScreen: UIViewControllerRepresentable {
 *     let client: PilotPartnerClient
 *     func makeUIViewController(context: Context) -> UIViewController {
 *         PilotPartnerUi.shared.eventsScreen(client: client)
 *     }
 *     func updateUIViewController(_ vc: UIViewController, context: Context) {}
 * }
 * ```
 *
 * The function is exposed via a singleton object so the Swift-bridged
 * name is the more idiomatic `PilotPartnerUi.shared.eventsScreen(...)`
 * instead of a free function with mangled module-qualified name.
 *
 * The shipped [EventsViewModel] is instantiated directly with `remember`
 * rather than via `viewModel(factory = ...)` to sidestep KMP
 * lifecycle-host plumbing — the ViewModel lives for the duration of the
 * composition. Partners with their own lifecycle infrastructure can copy
 * this composable and inject their own VM.
 */
object PilotPartnerUi {
    val shared: PilotPartnerUi = this

    fun eventsScreen(client: PilotPartnerClient): UIViewController = ComposeUIViewController {
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

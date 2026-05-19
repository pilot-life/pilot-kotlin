package life.pilot.partner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import life.pilot.partner.sdk.PilotPartnerClient
import life.pilot.partner.sdk.error.PartnerException
import life.pilot.partner.sdk.model.EventDetail
import life.pilot.partner.sdk.model.InventorySnapshot
import life.pilot.partner.ui.event.EventListFilters
import life.pilot.partner.ui.event.EventListState
import kotlinx.coroutines.CancellationException
import java.time.ZoneId

/**
 * Optional ViewModel that wires the SDK to [EventListState] and the
 * detail-screen inputs. Partners with their own DI (Hilt/Koin) can
 * subclass or copy; the SDK module has no dependency on Android, so the
 * VM lives here.
 *
 * Filter semantics:
 *   - [updateFilters] is the single entrypoint for changing filters.
 *   - When `startsAfter` changes vs. the prior filters, the event list
 *     is reset and refetched from page 1 with the new query param.
 *   - When only `query` or `endsBefore` change, no refetch happens —
 *     the list is filtered client-side by the UI layer.
 */
class EventsViewModel(
    private val client: PilotPartnerClient,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _events = MutableStateFlow(EventListState())
    val events: StateFlow<EventListState> = _events.asStateFlow()

    private val _filters = MutableStateFlow(EventListFilters())
    val filters: StateFlow<EventListFilters> = _filters.asStateFlow()

    private val _detail = MutableStateFlow<EventDetail?>(null)
    val detail: StateFlow<EventDetail?> = _detail.asStateFlow()

    private val _inventory = MutableStateFlow<InventorySnapshot?>(null)
    val inventory: StateFlow<InventorySnapshot?> = _inventory.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    fun updateFilters(next: EventListFilters) {
        val prior = _filters.value
        _filters.value = next
        if (prior.startsAfter != next.startsAfter) {
            // startsAfter is the only filter the API supports — reset
            // the pagination cursor and refetch from page 1.
            _events.value = EventListState()
            loadMoreEvents()
        }
    }

    /**
     * Reset pagination and fetch page 1 again with the current filters.
     * Wired to swipe-down on [life.pilot.partner.ui.event.EventListWithFilters].
     */
    fun refreshEvents() {
        _events.value = EventListState()
        loadMoreEvents()
    }

    fun loadMoreEvents() {
        val current = _events.value
        if (current.isLoading) return
        _events.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = client.events.list(
                    startsAfter = _filters.value.startsAfter
                        ?.atStartOfDay(zone)
                        ?.toInstant()
                        ?.toString(),
                    cursor = current.nextCursor,
                )
                _events.update {
                    it.copy(
                        events = it.events + page.events,
                        nextCursor = page.nextCursor,
                        isLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _events.update { it.copy(isLoading = false, error = describe(e)) }
            }
        }
    }

    fun loadEvent(eventUuid: String) {
        _detailLoading.value = true
        _detailError.value = null
        viewModelScope.launch {
            try {
                _detail.value = client.events.get(eventUuid)
                val inv = client.events.inventory(eventUuid)
                if (inv.isSuccessful) {
                    _inventory.value = inv.body()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _detailError.value = describe(e)
            } finally {
                _detailLoading.value = false
            }
        }
    }

    fun refreshInventory(eventUuid: String, ifNoneMatch: String? = null) {
        viewModelScope.launch {
            try {
                val resp = client.events.inventory(eventUuid, ifNoneMatch)
                if (resp.code() == 200) _inventory.value = resp.body()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // soft-fail; existing inventory remains
            }
        }
    }

    private fun describe(e: Throwable): String = when (e) {
        is PartnerException -> e.message ?: e.code
        else -> e.message ?: e.javaClass.simpleName
    }
}

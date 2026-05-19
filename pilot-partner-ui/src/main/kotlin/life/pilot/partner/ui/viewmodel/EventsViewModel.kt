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
import life.pilot.partner.ui.event.EventListState
import kotlinx.coroutines.CancellationException

/**
 * Optional ViewModel that wires the SDK to [EventListState] and the
 * detail-screen inputs. Partners with their own DI (Hilt/Koin) can
 * subclass or copy; the SDK module has no dependency on Android, so the
 * VM lives here.
 */
class EventsViewModel(
    private val client: PilotPartnerClient,
) : ViewModel() {

    private val _events = MutableStateFlow(EventListState())
    val events: StateFlow<EventListState> = _events.asStateFlow()

    private val _detail = MutableStateFlow<EventDetail?>(null)
    val detail: StateFlow<EventDetail?> = _detail.asStateFlow()

    private val _inventory = MutableStateFlow<InventorySnapshot?>(null)
    val inventory: StateFlow<InventorySnapshot?> = _inventory.asStateFlow()

    private val _detailError = MutableStateFlow<String?>(null)
    val detailError: StateFlow<String?> = _detailError.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading: StateFlow<Boolean> = _detailLoading.asStateFlow()

    fun loadMoreEvents() {
        val current = _events.value
        if (current.isLoading) return
        _events.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val page = client.events.list(cursor = current.nextCursor)
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

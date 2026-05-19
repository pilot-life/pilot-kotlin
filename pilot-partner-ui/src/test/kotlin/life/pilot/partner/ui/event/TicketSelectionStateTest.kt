package life.pilot.partner.ui.event

import androidx.compose.runtime.mutableStateMapOf
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import life.pilot.partner.sdk.model.TicketTypeRow
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TicketSelectionStateTest {

    private fun newState() = TicketSelectionState(mutableStateMapOf())

    private fun ticket(uuid: String, price: String, remaining: Int = 10, soldOut: Boolean = false) =
        TicketTypeRow(
            ticketTypeUUID = uuid,
            name = "T-$uuid",
            description = null,
            price = price,
            totalCapacity = remaining,
            remaining = remaining,
            soldOut = soldOut,
            availableFrom = "2026-01-01T00:00:00Z",
            availableTo = "2026-12-31T00:00:00Z",
        )

    @Test fun `quantityOf returns zero by default`() {
        assertThat(newState().quantityOf("missing")).isEqualTo(0)
    }

    @Test fun `set then quantityOf reflects the value`() {
        val s = newState()
        s.set("a", 3)
        assertThat(s.quantityOf("a")).isEqualTo(3)
    }

    @Test fun `set with zero removes the entry`() {
        val s = newState()
        s.set("a", 2)
        s.set("a", 0)
        assertThat(s.quantityOf("a")).isEqualTo(0)
        assertThat(s.totalCount()).isEqualTo(0)
    }

    @Test fun `subtotal multiplies price by quantity and sums`() {
        val s = newState()
        s.set("a", 2)
        s.set("b", 1)
        val tickets = listOf(ticket("a", "10.00"), ticket("b", "5.25"))
        assertThat(s.subtotal(tickets)).isEqualTo(BigDecimal("25.25"))
    }

    @Test fun `subtotal ignores unknown ticket ids`() {
        val s = newState()
        s.set("ghost", 5)
        s.set("a", 1)
        val tickets = listOf(ticket("a", "7.00"))
        assertThat(s.subtotal(tickets)).isEqualTo(BigDecimal("7.00"))
    }

    @Test fun `toSelections returns only positive-quantity rows mapped to tickets`() {
        val s = newState()
        s.set("a", 2)
        s.set("missing", 1)
        val tickets = listOf(ticket("a", "10.00"), ticket("b", "3.00"))
        val sels = s.toSelections(tickets)
        assertThat(sels.map { it.ticketType.ticketTypeUUID to it.quantity })
            .containsExactlyInAnyOrder("a" to 2)
    }
}

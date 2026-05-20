package life.pilot.partner.ui.event

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import life.pilot.partner.sdk.model.TicketTypeRow
import org.junit.Rule
import org.junit.Test

class TicketTypeRowItemTest {
    @get:Rule val composeRule = createComposeRule()

    private fun ticket(soldOut: Boolean = false, remaining: Int = 10) = TicketTypeRow(
        ticketTypeUUID = "tt-1",
        name = "General Admission",
        description = "Standing room",
        price = "25.00",
        totalCapacity = 100,
        remaining = remaining,
        soldOut = soldOut,
        availableFrom = "2026-01-01T00:00:00Z",
        availableTo = "2026-12-31T00:00:00Z",
    )

    @Test fun stepper_increments_and_decrements() {
        composeRule.setContent {
            var qty by mutableStateOf(0)
            TicketTypeRowItem(ticket = ticket(), quantity = qty, onQuantityChange = { qty = it })
        }
        composeRule.onNodeWithTag(TicketTypeRowItemTestTags.incFor("tt-1")).performClick()
        composeRule.onNodeWithTag(TicketTypeRowItemTestTags.incFor("tt-1")).performClick()
        composeRule.onNodeWithTag(TicketTypeRowItemTestTags.qtyFor("tt-1")).assertIsDisplayed()
    }

    @Test fun sold_out_disables_stepper() {
        composeRule.setContent {
            TicketTypeRowItem(ticket = ticket(soldOut = true), quantity = 0, onQuantityChange = {})
        }
        composeRule.onNodeWithTag(TicketTypeRowItemTestTags.soldOutFor("tt-1")).assertIsDisplayed()
    }

    @Test fun zero_remaining_shows_sold_out() {
        composeRule.setContent {
            TicketTypeRowItem(ticket = ticket(remaining = 0), quantity = 0, onQuantityChange = {})
        }
        composeRule.onNodeWithTag(TicketTypeRowItemTestTags.soldOutFor("tt-1")).assertIsDisplayed()
    }
}

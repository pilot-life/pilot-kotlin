package life.pilot.partner.ui.event

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import life.pilot.partner.sdk.model.EventListItem
import org.junit.Rule
import org.junit.Test

class EventListItemCardTest {
    @get:Rule val composeRule = createComposeRule()

    private val sampleEvent = EventListItem(
        eventUUID = "evt-1",
        name = "Summer Bash",
        startDate = "2026-06-15T18:00:00Z",
        endDate = "2026-06-15T22:00:00Z",
        venueName = "Echo Venue",
    )

    @Test fun renders_title_venue_chip_and_date() {
        composeRule.setContent {
            EventListItemCard(event = sampleEvent)
        }
        composeRule.onNodeWithText("Summer Bash").assertIsDisplayed()
        composeRule.onNodeWithText("Echo Venue Presents").assertIsDisplayed()
        composeRule.onNode(hasText("June 15, 2026", substring = true)).assertIsDisplayed()
    }

    @Test fun shows_only_n_tickets_left_when_remaining_supplied() {
        composeRule.setContent {
            EventListItemCard(event = sampleEvent, ticketsRemaining = 3)
        }
        composeRule.onNodeWithText("Only 3 Tickets Left").assertIsDisplayed()
    }

    @Test fun click_invokes_callback() {
        var clicked = false
        composeRule.setContent {
            EventListItemCard(event = sampleEvent, onClick = { clicked = true })
        }
        composeRule.onNodeWithText("Summer Bash").performClick()
        assert(clicked)
    }
}

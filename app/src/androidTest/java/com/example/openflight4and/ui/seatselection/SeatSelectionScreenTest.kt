package com.example.openflight4and.ui.seatselection

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.openflight4and.R
import com.example.openflight4and.ui.theme.OpenFlightTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeatSelectionScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectingSeatAndCategoryFinishesAndTransitions() {
        lateinit var selectedSeat: String
        lateinit var selectedCategory: String
        var finished by mutableStateOf(false)
        val context = composeRule.activity

        composeRule.setContent {
            OpenFlightTheme {
                if (!finished) {
                    SeatSelectionScreen(
                        onNavigateBack = {},
                        onSeatSelected = { seat, category ->
                            selectedSeat = seat
                            selectedCategory = category
                        },
                        onFinish = { finished = true },
                        hasTickets = true,
                        onTicketRequired = {}
                    )
                } else {
                    Text("Done")
                }
            }
        }

        composeRule.onNodeWithContentDescription("1A").performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.seatselection_choose_category)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.seatselection_category_focus)
        ).performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.seatselection_ready)
        ).performClick()

        composeRule.onNodeWithText("Done").assertIsDisplayed()
        assertEquals("1A", selectedSeat)
        assertEquals(context.getString(R.string.seatselection_category_focus), selectedCategory)
    }

    @Test
    fun noTicketsShowsTicketRequiredInsteadOfFinishing() {
        var ticketRequiredCalled by mutableStateOf(false)
        var finished by mutableStateOf(false)
        val context = composeRule.activity

        composeRule.setContent {
            OpenFlightTheme {
                SeatSelectionScreen(
                    onNavigateBack = {},
                    onSeatSelected = { _, _ -> },
                    onFinish = { finished = true },
                    hasTickets = false,
                    onTicketRequired = { ticketRequiredCalled = true }
                )
            }
        }

        composeRule.onNodeWithContentDescription("1A").performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.seatselection_category_focus)
        ).performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.seatselection_ready)
        ).performClick()

        assertTrue(ticketRequiredCalled)
        assertTrue(!finished)
    }
}

package com.example.openflight4and.data

import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.openflight4and.R
import com.example.openflight4and.data.local.AppDatabase
import com.example.openflight4and.model.FlightSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRepositoryInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: AppRepository

    @Before
    fun setUp() = runBlocking {
        clearStorage()
        repository = AppRepository(context)
    }

    @After
    fun tearDown() = runBlocking {
        clearStorage()
    }

    @Test
    fun dailyCheckInAndRedeemCodePersistTicketHistoryInDataStore() = runBlocking {
        val checkInResult = repository.claimDailyCheckIn()
        val redeemResult = repository.redeemCode("admin10")

        assertTrue(checkInResult is DailyCheckInResult.Success)
        assertTrue(redeemResult is RedeemCodeResult.Success)
        assertEquals(11, repository.flightTickets.first())

        val history = repository.ticketHistory.first()
        assertEquals(2, history.size)
        assertEquals(context.getString(R.string.repo_redeem_title), history[0].title)
        assertEquals(context.getString(R.string.repo_daily_check_in_title), history[1].title)
    }

    @Test
    fun saveSessionPersistsCompletedFlightsInRoomBackedFlows() = runBlocking {
        repository.saveSession(
            FlightSession(
                flightNumber = "OF101",
                originIata = "ICN",
                originName = "Incheon Intl",
                destinationIata = "NRT",
                destinationName = "Narita Intl",
                seatNumber = "1A",
                focusCategory = "Focus",
                distanceKm = 1200,
                durationMinutes = 95,
                startTime = 1000L,
                endTime = 2000L,
                isCompleted = true
            )
        )

        val recent = repository.recentSessions.first()
        val totalFlights = repository.totalFlights.first()
        val totalDistance = repository.totalDistance.first()
        val totalFocusMinutes = repository.totalFocusMinutes.first()

        assertEquals(1, recent.size)
        assertEquals("OF101", recent.first().flightNumber)
        assertEquals(1, totalFlights)
        assertEquals(1200, totalDistance)
        assertEquals(95, totalFocusMinutes)
    }

    private suspend fun clearStorage() {
        context.dataStore.edit { it.clear() }
        AppDatabase.resetForTests()
        context.deleteDatabase("openflight_db")
    }
}

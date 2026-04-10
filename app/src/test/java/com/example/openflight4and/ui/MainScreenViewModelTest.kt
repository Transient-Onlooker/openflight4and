package com.example.openflight4and.ui

import com.example.openflight4and.FakeAppRepository
import com.example.openflight4and.MainDispatcherRule
import com.example.openflight4and.TestAirports
import com.example.openflight4and.data.TicketSpendResult
import com.example.openflight4and.model.FlightDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun requestBoardingPass_withoutDestination_emitsToast() = runTest {
        val repository = FakeAppRepository(listOf(TestAirports.icn, TestAirports.hnd))
        val viewModel = MainScreenViewModel(
            repository = repository,
            startFlightService = { _, _ -> },
            estimateFlight = { _, _ -> 1200 to 115 }
        )
        val eventDeferred = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.first()
        }

        viewModel.requestBoardingPass(ticketBalance = 1)
        advanceUntilIdle()

        assertTrue(eventDeferred.await() is MainScreenEvent.ShowToast)
    }

    @Test
    fun requestBoardingPass_whenValid_navigatesToBoardingPass() = runTest {
        val repository = FakeAppRepository(listOf(TestAirports.icn, TestAirports.hnd))
        repository.nextStartFlightResult = TicketSpendResult(success = true, spent = 0)
        val startedDrafts = mutableListOf<Pair<FlightDraft, Int>>()
        val viewModel = MainScreenViewModel(
            repository = repository,
            startFlightService = { draft, updateSeconds -> startedDrafts += draft to updateSeconds },
            estimateFlight = { _, _ -> 1200 to 115 }
        )
        val eventDeferred = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.first()
        }

        viewModel.updateDestination(TestAirports.hnd)
        viewModel.requestBoardingPass(ticketBalance = 1)
        advanceUntilIdle()

        assertTrue(startedDrafts.isEmpty())
        assertEquals(MainScreenEvent.NavigateToBoardingPass, eventDeferred.await())
    }

    @Test
    fun startFlightAfterBoardingPass_whenValid_startsServiceAndNavigates() = runTest {
        val repository = FakeAppRepository(listOf(TestAirports.icn, TestAirports.hnd))
        repository.nextStartFlightResult = TicketSpendResult(success = true, spent = 0)
        val startedDrafts = mutableListOf<Pair<FlightDraft, Int>>()
        val viewModel = MainScreenViewModel(
            repository = repository,
            startFlightService = { draft, updateSeconds -> startedDrafts += draft to updateSeconds },
            estimateFlight = { _, _ -> 1200 to 115 }
        )
        val eventDeferred = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.first()
        }

        viewModel.updateDestination(TestAirports.hnd)
        viewModel.startFlightAfterBoardingPass(
            sandboxTimeScale = 4f,
            notificationUpdateSeconds = 20
        )
        advanceUntilIdle()

        assertEquals(1, startedDrafts.size)
        assertEquals(20, startedDrafts.single().second)
        assertEquals(4f, startedDrafts.single().first.timeScale)
        assertEquals(MainScreenEvent.NavigateToInFlight, eventDeferred.await())
    }

    @Test
    fun validateSeatSelection_withoutTickets_returnsFalse() = runTest {
        val repository = FakeAppRepository(listOf(TestAirports.icn, TestAirports.hnd))
        val viewModel = MainScreenViewModel(
            repository = repository,
            startFlightService = { _, _ -> },
            estimateFlight = { _, _ -> 1200 to 115 }
        )

        viewModel.updateDestination(TestAirports.hnd)

        assertFalse(viewModel.validateSeatSelection(ticketBalance = 0))
    }
}

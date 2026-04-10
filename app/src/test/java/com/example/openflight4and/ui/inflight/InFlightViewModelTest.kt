package com.example.openflight4and.ui.inflight

import com.example.openflight4and.FakeAppRepository
import com.example.openflight4and.MainDispatcherRule
import com.example.openflight4and.data.AdTicketRewardResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InFlightViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun adReward_startsAndLaunchesRewardedAd() = runTest {
        val repository = FakeAppRepository()
        val viewModel = InFlightViewModel(repository)
        val eventDeferred = backgroundScope.async { viewModel.events.first() }

        viewModel.startAdReward()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAdRewardRunning)
        assertEquals(0, repository.rewardedInflightAds)
        assertEquals(InFlightEvent.LaunchRewardedAd, eventDeferred.await())
    }

    @Test
    fun adReward_complete_grantsTicketAndShowsToast() = runTest {
        val repository = FakeAppRepository()
        val viewModel = InFlightViewModel(repository)
        backgroundScope.async { viewModel.events.first() }

        viewModel.startAdReward()
        advanceUntilIdle()
        val eventDeferred = backgroundScope.async { viewModel.events.first() }
        viewModel.completeAdReward()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAdRewardRunning)
        assertEquals(1, repository.rewardedInflightAds)
        assertEquals(
            InFlightEvent.ShowToast("광고 보상으로 비행권 1개가 지급되었습니다."),
            eventDeferred.await()
        )
    }

    @Test
    fun adReward_complete_withoutTicket_showsProgressToast() = runTest {
        val repository = FakeAppRepository().apply {
            nextInflightAdRewardResult = AdTicketRewardResult(
                grantedAmount = 0,
                remainingAdsUntilNextTicket = 2,
                currentTierAdsRequired = 3
            )
        }
        val viewModel = InFlightViewModel(repository)
        backgroundScope.async { viewModel.events.first() }

        viewModel.startAdReward()
        advanceUntilIdle()
        val eventDeferred = backgroundScope.async { viewModel.events.first() }
        viewModel.completeAdReward()
        advanceUntilIdle()

        assertEquals(
            InFlightEvent.ShowToast("현재 구간은 광고 3회당 비행권 1장입니다. 광고를 2회 더 보면 비행권이 지급됩니다."),
            eventDeferred.await()
        )
    }

    @Test
    fun cameraTracking_canBeDisabledAndEnabled() {
        val viewModel = InFlightViewModel(FakeAppRepository())

        viewModel.disableCameraTracking()
        assertFalse(viewModel.uiState.value.isCameraTracking)

        viewModel.enableCameraTracking()
        assertTrue(viewModel.uiState.value.isCameraTracking)
    }
}

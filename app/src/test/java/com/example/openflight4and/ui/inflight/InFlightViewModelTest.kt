package com.example.openflight4and.ui.inflight

import com.example.openflight4and.FakeAppRepository
import com.example.openflight4and.MainDispatcherRule
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
            InFlightEvent.ShowToast("愿묎퀬 蹂댁긽?쇰줈 鍮꾪뻾沅?1媛쒓? 吏湲됰릺?덉뒿?덈떎."),
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

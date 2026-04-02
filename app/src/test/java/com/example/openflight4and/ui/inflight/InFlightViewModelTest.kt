package com.example.openflight4and.ui.inflight

import com.example.openflight4and.FakeAppRepository
import com.example.openflight4and.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
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
    fun adReward_completesAfter30Seconds() = runTest {
        val repository = FakeAppRepository()
        val viewModel = InFlightViewModel(repository)
        val eventDeferred = backgroundScope.async { viewModel.events.first() }

        viewModel.startAdReward()
        advanceTimeBy(30_000)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAdRewardRunning)
        assertEquals(1, repository.rewardedInflightAds)
        assertEquals(
            InFlightEvent.ShowToast("비행권 1개가 지급되었습니다."),
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

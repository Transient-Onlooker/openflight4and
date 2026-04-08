package com.example.openflight4and.ui.tickets

import com.example.openflight4and.FakeAppRepository
import com.example.openflight4and.MainDispatcherRule
import com.example.openflight4and.data.DailyCheckInResult
import com.example.openflight4and.data.RedeemCodeResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TicketCenterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun claimDailyCheckIn_success_updatesStateAndShowsToast() = runTest {
        val repository = FakeAppRepository()
        repository.nextDailyCheckInResult = DailyCheckInResult.Success(1)
        val events = mutableListOf<TicketCenterEvent>()
        val viewModel = TicketCenterViewModel(repository)

        backgroundScope.launch { viewModel.events.collect(events::add) }
        advanceUntilIdle()

        viewModel.claimDailyCheckIn()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasCheckedInToday)
        assertEquals(1, viewModel.uiState.value.ticketBalance)
        assertEquals(
            TicketCenterEvent.ShowToast("출석체크 보상으로 비행권 1개가 지급되었습니다."),
            events.last()
        )
    }

    @Test
    fun startAdReward_setsLoadingStateAndLaunchesRewardedAd() = runTest {
        val repository = FakeAppRepository()
        val events = mutableListOf<TicketCenterEvent>()
        val viewModel = TicketCenterViewModel(repository)

        backgroundScope.launch { viewModel.events.collect(events::add) }
        advanceUntilIdle()

        viewModel.startAdReward()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isWatchingAd)
        assertEquals(0, repository.rewardedAds)
        assertEquals(TicketCenterEvent.LaunchRewardedAd, events.last())
    }

    @Test
    fun completeAdReward_grantsTicketAndShowsToast() = runTest {
        val repository = FakeAppRepository()
        val events = mutableListOf<TicketCenterEvent>()
        val viewModel = TicketCenterViewModel(repository)

        backgroundScope.launch { viewModel.events.collect(events::add) }
        advanceUntilIdle()

        viewModel.startAdReward()
        advanceUntilIdle()
        viewModel.completeAdReward()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isWatchingAd)
        assertEquals(1, repository.rewardedAds)
        assertEquals(
            TicketCenterEvent.ShowToast("광고 보상으로 비행권 1개가 지급되었습니다."),
            events.last()
        )
    }

    @Test
    fun handleAdLoadFailed_resetsLoadingStateAndShowsFallbackToast() = runTest {
        val repository = FakeAppRepository()
        val events = mutableListOf<TicketCenterEvent>()
        val viewModel = TicketCenterViewModel(repository)

        backgroundScope.launch { viewModel.events.collect(events::add) }
        advanceUntilIdle()

        viewModel.startAdReward()
        advanceUntilIdle()
        viewModel.handleAdLoadFailed(null)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isWatchingAd)
        assertEquals(
            TicketCenterEvent.ShowToast("광고를 불러오지 못했습니다. 잠시 다시 시도해주세요."),
            events.last()
        )
    }

    @Test
    fun redeemCode_blank_emitsToast() = runTest {
        val repository = FakeAppRepository()
        val viewModel = TicketCenterViewModel(repository)
        val eventDeferred = backgroundScope.async { viewModel.events.first() }

        viewModel.redeemCode()
        advanceUntilIdle()

        assertEquals(TicketCenterEvent.ShowToast("코드를 입력해 주세요."), eventDeferred.await())
    }

    @Test
    fun redeemCode_success_clearsInput() = runTest {
        val repository = FakeAppRepository()
        repository.nextRedeemCodeResult = RedeemCodeResult.Success(10)
        val viewModel = TicketCenterViewModel(repository)

        viewModel.updateRedeemCode("admin10")
        viewModel.redeemCode()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.redeemCode)
        assertEquals(10, viewModel.uiState.value.ticketBalance)
    }
}

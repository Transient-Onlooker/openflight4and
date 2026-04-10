package com.example.openflight4and.ui.tickets

import android.app.Application
import com.example.openflight4and.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.data.AppRepositoryDataSource
import com.example.openflight4and.data.DailyCheckInResult
import com.example.openflight4and.data.RedeemCodeResult
import com.example.openflight4and.model.FlightTicketHistoryEntry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TicketCenterUiState(
    val ticketBalance: Int = 0,
    val hasCheckedInToday: Boolean = false,
    val ticketHistory: List<FlightTicketHistoryEntry> = emptyList(),
    val redeemCode: String = "",
    val isWatchingAd: Boolean = false,
    val adSecondsRemaining: Int = 30
)

sealed interface TicketCenterEvent {
    data class ShowToast(val message: String) : TicketCenterEvent
    data object LaunchRewardedAd : TicketCenterEvent
}

class TicketCenterViewModel(
    private val repository: AppRepositoryDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(TicketCenterUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TicketCenterEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.flightTickets,
                repository.hasCheckedInToday,
                repository.ticketHistory
            ) { ticketBalance, hasCheckedInToday, ticketHistory ->
                Triple(ticketBalance, hasCheckedInToday, ticketHistory)
            }.collectLatest { (ticketBalance, hasCheckedInToday, ticketHistory) ->
                _uiState.update { state ->
                    state.copy(
                        ticketBalance = ticketBalance,
                        hasCheckedInToday = hasCheckedInToday,
                        ticketHistory = ticketHistory
                    )
                }
            }
        }
    }

    fun updateRedeemCode(value: String) {
        _uiState.update { it.copy(redeemCode = value) }
    }

    fun claimDailyCheckIn() {
        viewModelScope.launch {
            when (val result = repository.claimDailyCheckIn()) {
                is DailyCheckInResult.Success -> {
                    _events.emit(
                        TicketCenterEvent.ShowToast(
                            messageFor(R.string.tickets_toast_daily_check_in_reward, result.amount)
                        )
                    )
                }

                is DailyCheckInResult.Error -> {
                    _events.emit(TicketCenterEvent.ShowToast(result.message))
                }
            }
        }
    }

    fun startAdReward() {
        if (_uiState.value.isWatchingAd) return
        _uiState.update { it.copy(isWatchingAd = true, adSecondsRemaining = 0) }
        viewModelScope.launch {
            _events.emit(TicketCenterEvent.LaunchRewardedAd)
        }
    }

    fun cancelAdReward() {
        _uiState.update { it.copy(isWatchingAd = false) }
    }

    fun completeAdReward() {
        viewModelScope.launch {
            val result = repository.rewardTicketsFromAd()
            _uiState.update { it.copy(isWatchingAd = false) }
            _events.emit(
                TicketCenterEvent.ShowToast(
                    if (result.grantedAmount > 0) {
                        messageFor(R.string.tickets_toast_ad_reward, result.grantedAmount)
                    } else {
                        messageFor(
                            R.string.tickets_toast_ad_reward_progress,
                            result.currentTierAdsRequired,
                            result.remainingAdsUntilNextTicket
                        )
                    }
                )
            )
        }
    }

    fun handleAdClosedWithoutReward() {
        _uiState.update { it.copy(isWatchingAd = false) }
    }

    fun handleAdLoadFailed(message: String?) {
        _uiState.update { it.copy(isWatchingAd = false) }
        viewModelScope.launch {
            _events.emit(
                TicketCenterEvent.ShowToast(
                    message ?: messageFor(R.string.tickets_ad_reward_unavailable)
                )
            )
        }
    }

    fun redeemCode() {
        val code = _uiState.value.redeemCode
        if (code.isBlank()) {
            viewModelScope.launch {
                _events.emit(TicketCenterEvent.ShowToast(messageFor(R.string.repo_redeem_enter_code)))
            }
            return
        }

        viewModelScope.launch {
            when (val result = repository.redeemCode(code)) {
                is RedeemCodeResult.Success -> {
                    _uiState.update { it.copy(redeemCode = "") }
                    _events.emit(
                        TicketCenterEvent.ShowToast(
                            messageFor(R.string.tickets_toast_redeem_success, result.amount)
                        )
                    )
                }

                is RedeemCodeResult.Error -> {
                    _events.emit(TicketCenterEvent.ShowToast(result.message))
                }
            }
        }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TicketCenterViewModel::class.java)) {
                return TicketCenterViewModel(AppRepository(application)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private fun messageFor(resId: Int, vararg formatArgs: Any): String {
        return repository.getString(resId, *formatArgs)
    }
}

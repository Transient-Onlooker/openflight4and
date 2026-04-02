package com.example.openflight4and.ui.tickets

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.data.AppRepositoryDataSource
import com.example.openflight4and.data.DailyCheckInResult
import com.example.openflight4and.data.RedeemCodeResult
import com.example.openflight4and.model.FlightTicketHistoryEntry
import kotlinx.coroutines.delay
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
                            "\uCD9C\uC11D\uCCB4\uD06C \uBCF4\uC0C1\uC73C\uB85C \uBE44\uD589\uAD8C ${result.amount}\uAC1C\uAC00 \uC9C0\uAE09\uB418\uC5C8\uC2B5\uB2C8\uB2E4."
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
        _uiState.update { it.copy(isWatchingAd = true, adSecondsRemaining = 30) }

        viewModelScope.launch {
            while (_uiState.value.adSecondsRemaining > 0 && _uiState.value.isWatchingAd) {
                delay(1_000)
                _uiState.update { state ->
                    state.copy(adSecondsRemaining = (state.adSecondsRemaining - 1).coerceAtLeast(0))
                }
            }

            if (_uiState.value.isWatchingAd) {
                repository.rewardTicketsFromAd()
                _uiState.update { it.copy(isWatchingAd = false) }
                _events.emit(
                    TicketCenterEvent.ShowToast(
                        "\uAD11\uACE0 \uBCF4\uC0C1\uC73C\uB85C \uBE44\uD589\uAD8C 1\uAC1C\uAC00 \uC9C0\uAE09\uB418\uC5C8\uC2B5\uB2C8\uB2E4."
                    )
                )
            }
        }
    }

    fun cancelAdReward() {
        _uiState.update { it.copy(isWatchingAd = false) }
    }

    fun redeemCode() {
        val code = _uiState.value.redeemCode
        if (code.isBlank()) {
            viewModelScope.launch {
                _events.emit(TicketCenterEvent.ShowToast("\uCF54\uB4DC\uB97C \uC785\uB825\uD574 \uC8FC\uC138\uC694."))
            }
            return
        }

        viewModelScope.launch {
            when (val result = repository.redeemCode(code)) {
                is RedeemCodeResult.Success -> {
                    _uiState.update { it.copy(redeemCode = "") }
                    _events.emit(
                        TicketCenterEvent.ShowToast(
                            "\uBE44\uD589\uAD8C ${result.amount}\uAC1C\uAC00 \uC9C0\uAE09\uB418\uC5C8\uC2B5\uB2C8\uB2E4."
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
}

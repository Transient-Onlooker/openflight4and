package com.example.openflight4and.ui.inflight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.openflight4and.data.AppRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InFlightUiState(
    val showGiveUpDialog: Boolean = false,
    val showAdRewardDialog: Boolean = false,
    val isAdRewardRunning: Boolean = false,
    val adRewardSecondsRemaining: Int = 30,
    val isCameraTracking: Boolean = true
)

sealed interface InFlightEvent {
    data class ShowToast(val message: String) : InFlightEvent
}

class InFlightViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _uiState = MutableStateFlow(InFlightUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<InFlightEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun showGiveUpDialog() {
        _uiState.update { it.copy(showGiveUpDialog = true) }
    }

    fun hideGiveUpDialog() {
        _uiState.update { it.copy(showGiveUpDialog = false) }
    }

    fun showAdRewardDialog() {
        _uiState.update { it.copy(showAdRewardDialog = true) }
    }

    fun hideAdRewardDialog() {
        _uiState.update { it.copy(showAdRewardDialog = false) }
    }

    fun startAdReward() {
        if (_uiState.value.isAdRewardRunning) return
        _uiState.update {
            it.copy(
                showAdRewardDialog = false,
                isAdRewardRunning = true,
                adRewardSecondsRemaining = 30
            )
        }

        viewModelScope.launch {
            while (_uiState.value.isAdRewardRunning && _uiState.value.adRewardSecondsRemaining > 0) {
                delay(1_000)
                _uiState.update { state ->
                    state.copy(adRewardSecondsRemaining = (state.adRewardSecondsRemaining - 1).coerceAtLeast(0))
                }
            }

            if (_uiState.value.isAdRewardRunning) {
                repository.rewardSingleTicketFromInFlightAd()
                _uiState.update { it.copy(isAdRewardRunning = false) }
                _events.emit(InFlightEvent.ShowToast("\uBE44\uD589\uAD8C 1\uAC1C\uAC00 \uC9C0\uAE09\uB418\uC5C8\uC2B5\uB2C8\uB2E4."))
            }
        }
    }

    fun cancelAdReward() {
        _uiState.update { it.copy(isAdRewardRunning = false) }
    }

    fun enableCameraTracking() {
        _uiState.update { it.copy(isCameraTracking = true) }
    }

    fun disableCameraTracking() {
        _uiState.update { it.copy(isCameraTracking = false) }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(InFlightViewModel::class.java)) {
                return InFlightViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

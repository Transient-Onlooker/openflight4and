package com.example.openflight4and.ui.inflight

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.openflight4and.R
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.data.AppRepositoryDataSource
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
    val isCameraTracking: Boolean = true
)

sealed interface InFlightEvent {
    data class ShowToast(val message: String) : InFlightEvent
    data object LaunchRewardedAd : InFlightEvent
}

class InFlightViewModel(
    private val repository: AppRepositoryDataSource
) : ViewModel() {

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
                isAdRewardRunning = true
            )
        }

        viewModelScope.launch {
            _events.emit(InFlightEvent.LaunchRewardedAd)
        }
    }

    fun cancelAdReward() {
        _uiState.update { it.copy(isAdRewardRunning = false) }
    }

    fun completeAdReward() {
        viewModelScope.launch {
            val result = repository.rewardSingleTicketFromInFlightAd()
            _uiState.update { it.copy(isAdRewardRunning = false) }
            _events.emit(
                InFlightEvent.ShowToast(
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
        _uiState.update { it.copy(isAdRewardRunning = false) }
    }

    fun handleAdLoadFailed(message: String?) {
        _uiState.update { it.copy(isAdRewardRunning = false) }
        viewModelScope.launch {
            _events.emit(
                InFlightEvent.ShowToast(
                    message ?: messageFor(R.string.tickets_ad_reward_unavailable)
                )
            )
        }
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
                return InFlightViewModel(AppRepository(application)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private fun messageFor(resId: Int, vararg formatArgs: Any): String {
        return repository.getString(resId, *formatArgs)
    }
}

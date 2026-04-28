package com.example.openflight4and.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.openflight4and.R
import com.example.openflight4and.data.AccountActionResult
import com.example.openflight4and.data.AccountState
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.FlightBackgroundSound
import com.example.openflight4and.model.FlightTimeDisplayMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val unitSystem: String = "km",
    val appLanguage: String = "system",
    val mapStyle: String = "standard",
    val notificationsEnabled: Boolean = true,
    val notificationUpdateSeconds: Int = 10,
    val flightBackgroundSound: String = FlightBackgroundSound.AIRPLANE_WHITE_NOISE,
    val flightBackgroundSoundCustomUri: String? = null,
    val flightBackgroundSoundCustomName: String? = null,
    val flightTimeDisplayMode: String = FlightTimeDisplayMode.REMAINING,
    val accountState: AccountState = AccountState.SignedOut,
    val focusLockEnabled: Boolean = false,
    val advancedLockEnabled: Boolean = false,
    val focusLockPinEnabled: Boolean = false,
    val focusLockAllowedPackages: Set<String> = emptySet(),
    val screenOrientationMode: String = "auto",
    val isSigningIn: Boolean = false
)

sealed interface SettingsEvent {
    data class ShowToast(val message: String) : SettingsEvent
}

class SettingsViewModel(
    private val repository: AppRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.unitSystem,
                repository.appLanguage,
                repository.mapStyle,
                repository.notificationsEnabled,
                repository.notificationUpdateSeconds,
                repository.flightBackgroundSound,
                repository.flightBackgroundSoundCustomUri,
                repository.flightBackgroundSoundCustomName,
                repository.flightTimeDisplayMode,
                repository.accountState,
                repository.focusLockEnabled,
                repository.advancedLockEnabled,
                repository.focusLockPinEnabled,
                repository.focusLockAllowedApps,
                repository.screenOrientationMode
            ) { values ->
                SettingsUiState(
                    unitSystem = values[0] as String,
                    appLanguage = values[1] as String,
                    mapStyle = values[2] as String,
                    notificationsEnabled = values[3] as Boolean,
                    notificationUpdateSeconds = values[4] as Int,
                    flightBackgroundSound = values[5] as String,
                    flightBackgroundSoundCustomUri = values[6] as String?,
                    flightBackgroundSoundCustomName = values[7] as String?,
                    flightTimeDisplayMode = values[8] as String,
                    accountState = values[9] as AccountState,
                    focusLockEnabled = values[10] as Boolean,
                    advancedLockEnabled = values[11] as Boolean,
                    focusLockPinEnabled = values[12] as Boolean,
                    focusLockAllowedPackages = (values[13] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet(),
                    screenOrientationMode = values[14] as String,
                    isSigningIn = _uiState.value.isSigningIn
                )
            }.collectLatest { state ->
                _uiState.update {
                    state.copy(isSigningIn = it.isSigningIn)
                }
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        if (_uiState.value.isSigningIn) return
        _uiState.update { it.copy(isSigningIn = true) }
        viewModelScope.launch {
            when (val result = repository.signInWithGoogle(activityContext)) {
                is AccountActionResult.Success -> {
                    _events.emit(
                        SettingsEvent.ShowToast(
                            repository.getString(
                                R.string.settings_account_signed_in_format,
                                result.account.userCode
                            )
                        )
                    )
                }

                is AccountActionResult.Error -> {
                    _events.emit(SettingsEvent.ShowToast(result.message))
                }
            }
            _uiState.update { it.copy(isSigningIn = false) }
        }
    }

    fun syncPendingTicketEvents() {
        viewModelScope.launch {
            repository.syncPendingTicketEvents()
            _events.emit(
                SettingsEvent.ShowToast(
                    repository.getString(R.string.settings_account_sync_done)
                )
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _events.emit(
                SettingsEvent.ShowToast(
                    repository.getString(R.string.settings_account_signed_out_toast)
                )
            )
        }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(AppRepository(application)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

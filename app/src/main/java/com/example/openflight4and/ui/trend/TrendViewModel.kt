package com.example.openflight4and.ui.trend

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.openflight4and.R
import com.example.openflight4and.data.AppRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrendUiState(
    val totalFlights: Int = 0,
    val totalDistanceKm: Int = 0,
    val totalFocusMinutes: Int = 0,
    val unitSystem: String = "km",
    val debugFlightMode: Boolean = false,
    val visitedAirportsCount: Int = 0
)

sealed interface TrendEvent {
    data class ShowToast(val message: String) : TrendEvent
    data object NavigateToSandbox : TrendEvent
}

class TrendViewModel(
    private val repository: AppRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrendUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TrendEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var distanceClickCount = 0
    private var distanceClickTimestamp = 0L
    private var debugClickCount = 0
    private var debugClickTimestamp = 0L

    init {
        viewModelScope.launch {
            combine(
                repository.totalFlights,
                repository.totalDistance,
                repository.totalFocusMinutes,
                repository.unitSystem,
                repository.debugFlightMode,
                repository.allSessions
            ) { values ->
                val totalFlights = values[0] as Int
                val totalDistanceKm = values[1] as Int?
                val totalFocusMinutes = values[2] as Int?
                val unitSystem = values[3] as String
                val debugFlightMode = values[4] as Boolean
                val sessions = values[5] as List<*>
                TrendUiState(
                    totalFlights = totalFlights,
                    totalDistanceKm = totalDistanceKm ?: 0,
                    totalFocusMinutes = totalFocusMinutes ?: 0,
                    unitSystem = unitSystem,
                    debugFlightMode = debugFlightMode,
                    visitedAirportsCount = sessions
                        .filterIsInstance<com.example.openflight4and.model.FlightSession>()
                        .filter { it.isCompleted }
                        .flatMap { listOf(it.originIata, it.destinationIata) }
                        .distinct()
                        .size
                )
            }.collectLatest { state ->
                _uiState.update { state }
            }
        }
    }

    fun onDebugTap() {
        val now = System.currentTimeMillis()
        val elapsed = now - debugClickTimestamp
        debugClickCount = if (elapsed < 5000) debugClickCount + 1 else 1
        debugClickTimestamp = now

        if (debugClickCount >= 5) {
            debugClickCount = 0
            toggleDebugMode()
        }
    }

    fun onDistanceTap() {
        val now = System.currentTimeMillis()
        val elapsed = now - distanceClickTimestamp
        distanceClickCount = if (elapsed < 5000) distanceClickCount + 1 else 1
        distanceClickTimestamp = now

        if (distanceClickCount >= 5) {
            distanceClickCount = 0
            _events.tryEmit(TrendEvent.NavigateToSandbox)
        }
    }

    private fun toggleDebugMode() {
        val nextValue = !_uiState.value.debugFlightMode
        viewModelScope.launch {
            repository.setDebugFlightMode(nextValue)
            _events.emit(
                TrendEvent.ShowToast(
                    repository.getString(
                        if (nextValue) R.string.trend_debug_enabled else R.string.trend_debug_disabled
                    )
                )
            )
        }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TrendViewModel::class.java)) {
                return TrendViewModel(AppRepository(application)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

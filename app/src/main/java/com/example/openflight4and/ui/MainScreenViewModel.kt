package com.example.openflight4and.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.openflight4and.InFlightLaunchRequest
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.data.AppRepositoryDataSource
import com.example.openflight4and.model.Airport
import com.example.openflight4and.model.FlightDraft
import com.example.openflight4and.model.FlightSession
import com.example.openflight4and.service.FlightService
import com.example.openflight4and.utils.FlightUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainScreenUiState(
    val allAirports: List<Airport> = emptyList(),
    val currentLocation: Airport? = null,
    val recentSessions: List<FlightSession> = emptyList(),
    val currentDraft: FlightDraft,
    val showAirplaneModeDialog: Boolean = false
)

sealed interface MainScreenEvent {
    data class ShowToast(val message: String) : MainScreenEvent
    data object NavigateToBoardingPass : MainScreenEvent
    data object NavigateToInFlight : MainScreenEvent
}

class MainScreenViewModel(
    private val repository: AppRepositoryDataSource,
    private val isAirplaneModeOn: () -> Boolean,
    private val startFlightService: (FlightDraft, Int) -> Unit,
    private val estimateFlight: (Airport, Airport) -> Pair<Int, Int> = { origin, destination ->
        val distance = FlightUtils.calculateDistance(origin, destination)
        distance.toInt() to FlightUtils.estimateDurationMinutes(distance)
    },
    private val allAirports: List<Airport> = repository.getAirports()
) : ViewModel() {

    private val fallbackOrigin = allAirports.find { it.iata == "ICN" }
        ?: allAirports.firstOrNull()
        ?: Airport(
            iata = "ICN",
            nameKo = "Incheon Intl",
            nameEn = "Incheon Intl",
            cityKo = "Incheon Seoul",
            cityEn = "Incheon Seoul",
            country = "KR",
            latitude = 37.46,
            longitude = 126.44
        )

    private val _uiState = MutableStateFlow(
        MainScreenUiState(
            allAirports = allAirports,
            currentDraft = FlightDraft(origin = fallbackOrigin)
        )
    )
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainScreenEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentLocation,
                repository.recentSessions
            ) { currentLocation, recentSessions ->
                currentLocation to recentSessions
            }.collect { (currentLocation, recentSessions) ->
                val defaultOrigin = resolveDefaultOrigin(currentLocation, recentSessions)
                _uiState.update { state ->
                    val updatedDraft = when {
                        currentLocation != null && state.currentDraft.origin.iata != currentLocation.iata ->
                            state.currentDraft.copy(origin = currentLocation)
                        currentLocation == null && state.currentDraft.origin.iata != defaultOrigin.iata ->
                            state.currentDraft.copy(origin = defaultOrigin)
                        else -> state.currentDraft
                    }

                    state.copy(
                        currentLocation = currentLocation,
                        recentSessions = recentSessions,
                        currentDraft = updatedDraft
                    )
                }
            }
        }
    }

    fun prepareNewFlight() {
        _uiState.update { state ->
            state.copy(
                currentDraft = FlightDraft(
                    origin = state.currentLocation ?: resolveDefaultOrigin(
                        state.currentLocation,
                        state.recentSessions
                    )
                )
            )
        }
    }

    fun updateDestination(destination: Airport) {
        _uiState.update { state ->
            val (distance, duration) = estimateFlight(state.currentDraft.origin, destination)
            state.copy(
                currentDraft = state.currentDraft.copy(
                    destination = destination,
                    distanceKm = distance,
                    estimatedMinutes = duration
                )
            )
        }
    }

    fun updateSeatAndCategory(seat: String, category: String?) {
        _uiState.update { state ->
            state.copy(
                currentDraft = state.currentDraft.copy(
                    seatNumber = seat,
                    focusCategory = category
                )
            )
        }
    }

    fun prepareBoardingPass() {
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        _uiState.update { state ->
            state.copy(currentDraft = state.currentDraft.copy(boardingTime = now))
        }
    }

    fun dismissAirplaneModeDialog() {
        _uiState.update { it.copy(showAirplaneModeDialog = false) }
    }

    fun confirmAirplaneModeStart() {
        _uiState.update { it.copy(showAirplaneModeDialog = false) }
        prepareBoardingPass()
        _events.tryEmit(MainScreenEvent.NavigateToBoardingPass)
    }

    fun requestBoardingPass(
        ticketBalance: Int,
        airplaneModeCheckEnabled: Boolean
    ) {
        val draft = _uiState.value.currentDraft
        if (draft.destination == null) {
            _events.tryEmit(MainScreenEvent.ShowToast("\uBAA9\uC801\uC9C0\uB97C \uBA3C\uC800 \uC120\uD0DD\uD558\uC138\uC694."))
            return
        }

        if (ticketBalance <= 0) {
            _events.tryEmit(MainScreenEvent.ShowToast("\uBE44\uD589\uAD8C\uC774 \uBD80\uC871\uD569\uB2C8\uB2E4."))
            return
        }

        if (airplaneModeCheckEnabled && !isAirplaneModeOn()) {
            _uiState.update { it.copy(showAirplaneModeDialog = true) }
            return
        }

        prepareBoardingPass()
        _events.tryEmit(MainScreenEvent.NavigateToBoardingPass)
    }

    fun validateSeatSelection(ticketBalance: Int): Boolean {
        val draft = _uiState.value.currentDraft
        return when {
            draft.destination == null -> {
                _events.tryEmit(MainScreenEvent.ShowToast("\uBAA9\uC801\uC9C0\uB97C \uBA3C\uC800 \uC120\uD0DD\uD558\uC138\uC694."))
                false
            }

            ticketBalance <= 0 -> {
                _events.tryEmit(MainScreenEvent.ShowToast("\uBE44\uD589\uAD8C\uC774 \uBD80\uC871\uD569\uB2C8\uB2E4."))
                false
            }

            else -> true
        }
    }

    suspend fun handleInflightLaunchRequest(request: InFlightLaunchRequest?): Boolean {
        val launchRequest = request ?: return false
        if (!FlightService.isServiceRunning()) {
            return false
        }

        val state = _uiState.value
        val origin = launchRequest.originIata?.let { requestedIata ->
            allAirports.find { it.iata == requestedIata }
        } ?: state.currentDraft.origin
        val destination = launchRequest.destinationIata
            ?.takeUnless { it == "N/A" }
            ?.let { requestedIata -> allAirports.find { it.iata == requestedIata } }
            ?: state.currentDraft.destination
        val estimatedMinutes = launchRequest.durationMinutes
            .takeIf { it > 0 }
            ?: (FlightService.getTotalSeconds() / 60L).toInt()
        val distanceKm = destination
            ?.let { FlightUtils.calculateDistance(origin, it).toInt() }
            ?: state.currentDraft.distanceKm

        _uiState.update {
            it.copy(
                currentDraft = it.currentDraft.copy(
                    origin = origin,
                    destination = destination,
                    estimatedMinutes = estimatedMinutes,
                    distanceKm = distanceKm
                )
            )
        }
        return true
    }

    private fun startFlightInternal(
        sandboxTimeScale: Float,
        notificationUpdateSeconds: Int
    ) {
        viewModelScope.launch {
            val spendResult = repository.canStartFlight(_uiState.value.currentDraft.estimatedMinutes)
            if (!spendResult.success) {
                _events.emit(
                    MainScreenEvent.ShowToast(
                        spendResult.message ?: "\uBE44\uD589\uAD8C\uC774 \uBD80\uC871\uD569\uB2C8\uB2E4."
                    )
                )
                return@launch
            }

            val draftToStart = _uiState.value.currentDraft.copy(timeScale = sandboxTimeScale)
            _uiState.update { it.copy(currentDraft = draftToStart) }
            startFlightService(draftToStart, notificationUpdateSeconds)
            _events.emit(MainScreenEvent.NavigateToInFlight)
        }
    }

    fun startFlightAfterBoardingPass(
        sandboxTimeScale: Float,
        notificationUpdateSeconds: Int
    ) {
        startFlightInternal(sandboxTimeScale, notificationUpdateSeconds)
    }

    private fun resolveDefaultOrigin(
        currentLocation: Airport?,
        recentSessions: List<FlightSession>
    ): Airport {
        currentLocation?.let { return it }
        val lastDestIata = recentSessions.firstOrNull()?.destinationIata
        return allAirports.find { it.iata == lastDestIata }
            ?: allAirports.find { it.iata == "ICN" }
            ?: fallbackOrigin
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainScreenViewModel::class.java)) {
                val repository = AppRepository(application)
                return MainScreenViewModel(
                    repository = repository,
                    isAirplaneModeOn = { FlightUtils.isAirplaneModeOn(application) },
                    startFlightService = { draftToStart, notificationUpdateSeconds ->
                        val languageTag = application.resources.configuration.locales[0]?.toLanguageTag()
                            ?: Locale.getDefault().toLanguageTag()
                        val intent = Intent(application, FlightService::class.java).apply {
                            putExtra("origin_iata", draftToStart.origin.iata)
                            putExtra("destination_iata", draftToStart.destination?.iata ?: "N/A")
                            putExtra("origin_name", draftToStart.origin.localizedName(languageTag))
                            putExtra("destination_name", draftToStart.destination?.localizedName(languageTag) ?: "N/A")
                            putExtra("duration_minutes", draftToStart.estimatedMinutes)
                            putExtra("time_scale", draftToStart.timeScale)
                            putExtra("notification_update_seconds", notificationUpdateSeconds)
                        }
                        application.startForegroundService(intent)
                    },
                    estimateFlight = { origin, destination ->
                        val distance = FlightUtils.calculateDistance(origin, destination)
                        distance.toInt() to FlightUtils.estimateDurationMinutes(distance)
                    }
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

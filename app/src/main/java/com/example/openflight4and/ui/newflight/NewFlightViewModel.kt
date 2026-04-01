package com.example.openflight4and.ui.newflight

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.Airport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NewFlightUiState(
    val allAirports: List<Airport> = emptyList(),
    val selectedDestination: Airport? = null,
    val searchRadiusKm: Int = 1000,
    val showSameAirportDialog: Boolean = false,
    val showQuickFlightDialog: Boolean = false,
    val searchQuery: String = "",
    val initializedOriginIata: String? = null
)

class NewFlightViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository = AppRepository(application)

    private val _uiState = MutableStateFlow(
        NewFlightUiState(allAirports = repository.getAirports())
    )
    val uiState = _uiState.asStateFlow()

    fun initialize(
        originIata: String,
        destination: Airport?
    ) {
        _uiState.update { state ->
            if (state.initializedOriginIata == originIata && state.selectedDestination?.iata == destination?.iata) {
                state
            } else {
                state.copy(
                    initializedOriginIata = originIata,
                    selectedDestination = destination,
                    showSameAirportDialog = false,
                    showQuickFlightDialog = false
                )
            }
        }
    }

    fun selectDestination(airport: Airport) {
        _uiState.update { it.copy(selectedDestination = airport) }
    }

    fun updateSearchRadius(searchRadiusKm: Int) {
        _uiState.update { it.copy(searchRadiusKm = searchRadiusKm) }
    }

    fun updateSearchQuery(searchQuery: String) {
        _uiState.update { it.copy(searchQuery = searchQuery) }
    }

    fun showSameAirportDialog() {
        _uiState.update { it.copy(showSameAirportDialog = true) }
    }

    fun hideSameAirportDialog() {
        _uiState.update { it.copy(showSameAirportDialog = false) }
    }

    fun showQuickFlightDialog() {
        _uiState.update { it.copy(showQuickFlightDialog = true) }
    }

    fun hideQuickFlightDialog() {
        _uiState.update { it.copy(showQuickFlightDialog = false) }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NewFlightViewModel::class.java)) {
                return NewFlightViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

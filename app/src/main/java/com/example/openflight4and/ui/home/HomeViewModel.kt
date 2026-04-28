package com.example.openflight4and.ui.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.openflight4and.data.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val mapStyle: String = "standard",
    val mapOverlayStyle: String = "dark",
    val totalFlights: Int = 0
)

class HomeViewModel(
    repository: AppRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.mapStyle,
                repository.mapOverlayStyle,
                repository.totalFlights
            ) { mapStyle, mapOverlayStyle, totalFlights ->
                HomeUiState(
                    mapStyle = mapStyle,
                    mapOverlayStyle = mapOverlayStyle,
                    totalFlights = totalFlights
                )
            }.collectLatest { state ->
                _uiState.update { state }
            }
        }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                return HomeViewModel(AppRepository(application)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

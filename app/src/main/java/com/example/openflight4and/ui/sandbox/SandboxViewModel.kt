package com.example.openflight4and.ui.sandbox

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.Airport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SandboxUiState(
    val currentLocation: Airport? = null,
    val timeScale: Float = 1f
)

class SandboxViewModel(
    private val repository: AppRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SandboxUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.currentLocation,
                repository.sandboxTimeScale
            ) { currentLocation, timeScale ->
                SandboxUiState(
                    currentLocation = currentLocation,
                    timeScale = timeScale
                )
            }.collectLatest { state ->
                _uiState.update { state }
            }
        }
    }

    fun setTimeScale(scale: Float) {
        viewModelScope.launch {
            repository.setSandboxTimeScale(scale)
        }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SandboxViewModel::class.java)) {
                return SandboxViewModel(AppRepository(application)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

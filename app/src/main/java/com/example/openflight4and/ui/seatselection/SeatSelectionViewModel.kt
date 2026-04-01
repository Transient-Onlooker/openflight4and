package com.example.openflight4and.ui.seatselection

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SeatSelectionUiState(
    val selectedSeat: String? = null,
    val selectedCategory: String? = null,
    val showBottomSheet: Boolean = false
)

class SeatSelectionViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SeatSelectionUiState())
    val uiState = _uiState.asStateFlow()

    fun selectSeat(seat: String) {
        _uiState.update {
            it.copy(
                selectedSeat = seat,
                showBottomSheet = true
            )
        }
    }

    fun selectCategory(category: String) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                showBottomSheet = false
            )
        }
    }

    fun hideBottomSheet() {
        _uiState.update { it.copy(showBottomSheet = false) }
    }
}

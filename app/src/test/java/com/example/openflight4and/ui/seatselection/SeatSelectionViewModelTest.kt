package com.example.openflight4and.ui.seatselection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatSelectionViewModelTest {

    @Test
    fun selectSeat_opensBottomSheet() {
        val viewModel = SeatSelectionViewModel()

        viewModel.selectSeat("12A")

        assertEquals("12A", viewModel.uiState.value.selectedSeat)
        assertTrue(viewModel.uiState.value.showBottomSheet)
    }

    @Test
    fun selectCategory_setsCategoryAndClosesBottomSheet() {
        val viewModel = SeatSelectionViewModel()
        viewModel.selectSeat("12A")

        viewModel.selectCategory("집중")

        assertEquals("집중", viewModel.uiState.value.selectedCategory)
        assertFalse(viewModel.uiState.value.showBottomSheet)
    }
}

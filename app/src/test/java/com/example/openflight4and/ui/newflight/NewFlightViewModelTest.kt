package com.example.openflight4and.ui.newflight

import com.example.openflight4and.FakeAppRepository
import com.example.openflight4and.MainDispatcherRule
import com.example.openflight4and.TestAirports
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewFlightViewModelTest {

    private companion object {
        const val UNLIMITED_RADIUS_KM = 20000
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialize_setsOriginAndDestination() {
        val viewModel = NewFlightViewModel(
            FakeAppRepository(listOf(TestAirports.icn, TestAirports.hnd, TestAirports.lax))
        )

        viewModel.initialize("ICN", TestAirports.hnd)

        val state = viewModel.uiState.value
        assertEquals("ICN", state.initializedOriginIata)
        assertEquals("HND", state.selectedDestination?.iata)
    }

    @Test
    fun dialogAndSearchActions_updateUiState() {
        val viewModel = NewFlightViewModel(FakeAppRepository())

        viewModel.updateSearchRadius(2500)
        viewModel.updateSearchQuery("tokyo", UNLIMITED_RADIUS_KM)
        viewModel.showQuickFlightDialog()
        viewModel.showSameAirportDialog()

        assertEquals("tokyo", viewModel.uiState.value.searchQuery)
        assertEquals(UNLIMITED_RADIUS_KM, viewModel.uiState.value.searchRadiusKm)
        assertEquals(2500, viewModel.uiState.value.previousManualSearchRadiusKm)
        assertTrue(viewModel.uiState.value.showQuickFlightDialog)
        assertTrue(viewModel.uiState.value.showSameAirportDialog)

        viewModel.updateSearchQuery("", UNLIMITED_RADIUS_KM)
        viewModel.hideQuickFlightDialog()
        viewModel.hideSameAirportDialog()

        assertEquals(2500, viewModel.uiState.value.searchRadiusKm)
        assertFalse(viewModel.uiState.value.showQuickFlightDialog)
        assertFalse(viewModel.uiState.value.showSameAirportDialog)
    }

    @Test
    fun initialize_resetsTransientDialogsWhenOriginChanges() {
        val viewModel = NewFlightViewModel(
            FakeAppRepository(listOf(TestAirports.icn, TestAirports.hnd, TestAirports.lax))
        )

        viewModel.showQuickFlightDialog()
        viewModel.showSameAirportDialog()
        viewModel.initialize("HND", TestAirports.lax)

        val state = viewModel.uiState.value
        assertEquals("HND", state.initializedOriginIata)
        assertEquals("LAX", state.selectedDestination?.iata)
        assertFalse(state.showQuickFlightDialog)
        assertFalse(state.showSameAirportDialog)
    }

    @Test
    fun selectDestination_updatesSelectedAirport() {
        val viewModel = NewFlightViewModel(
            FakeAppRepository(listOf(TestAirports.icn, TestAirports.hnd, TestAirports.lax))
        )

        viewModel.selectDestination(TestAirports.lax)

        assertEquals("LAX", viewModel.uiState.value.selectedDestination?.iata)
    }

    @Test
    fun updateSearchRadius_whileSearching_onlyUpdatesManualRadiusSnapshot() {
        val viewModel = NewFlightViewModel(FakeAppRepository())

        viewModel.updateSearchRadius(1200)
        viewModel.updateSearchQuery("seoul", UNLIMITED_RADIUS_KM)
        viewModel.updateSearchRadius(3400)

        val state = viewModel.uiState.value
        assertEquals("seoul", state.searchQuery)
        assertEquals(UNLIMITED_RADIUS_KM, state.searchRadiusKm)
        assertEquals(3400, state.previousManualSearchRadiusKm)
    }

    @Test
    fun updateSearchQuery_clearAfterManualRadiusChange_restoresLatestManualRadius() {
        val viewModel = NewFlightViewModel(FakeAppRepository())

        viewModel.updateSearchRadius(900)
        viewModel.updateSearchQuery("tokyo", UNLIMITED_RADIUS_KM)
        viewModel.updateSearchRadius(1800)
        viewModel.updateSearchQuery("", UNLIMITED_RADIUS_KM)

        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        assertEquals(1800, state.searchRadiusKm)
        assertEquals(1800, state.previousManualSearchRadiusKm)
    }

    @Test
    fun updateSearchQuery_blankWithoutActiveSearch_keepsManualRadius() {
        val viewModel = NewFlightViewModel(FakeAppRepository())

        viewModel.updateSearchRadius(2600)
        viewModel.updateSearchQuery("", UNLIMITED_RADIUS_KM)

        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        assertEquals(2600, state.searchRadiusKm)
        assertEquals(2600, state.previousManualSearchRadiusKm)
    }
}

package com.example.openflight4and.data

import android.content.Context
import android.util.Log
import com.example.openflight4and.model.Airport
import com.example.openflight4and.model.FlightSession
import com.example.openflight4and.model.FlightTicketHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AppRepositoryDataSource {
    fun getAirports(): List<Airport>
    fun getString(resId: Int, vararg formatArgs: Any): String

    val recentSessions: Flow<List<FlightSession>>
    val currentLocation: Flow<Airport?>
    val flightTickets: Flow<Int>
    val hasCheckedInToday: Flow<Boolean>
    val ticketHistory: Flow<List<FlightTicketHistoryEntry>>

    suspend fun claimDailyCheckIn(): DailyCheckInResult
    suspend fun canStartFlight(estimatedMinutes: Int): TicketSpendResult
    suspend fun rewardTicketsFromAd(): AdTicketRewardResult
    suspend fun rewardSingleTicketFromInFlightAd(): AdTicketRewardResult
    suspend fun redeemCode(code: String): RedeemCodeResult
    suspend fun fetchVersionStatus(): VersionStatus?
}

class AppRepository(private val context: Context) : AppRepositoryDataSource {
    private val _lastDataError = MutableStateFlow<String?>(null)
    @Suppress("unused")
    val lastDataError: StateFlow<String?> = _lastDataError.asStateFlow()

    private val airportRepository = AirportRepository(context, ::reportDataError)
    private val settingsRepository = SettingsRepository(context, ::reportDataError)
    private val ticketRepository = TicketRepository(context, ::reportDataError)
    private val flightSessionRepository = FlightSessionRepository(context)
    private val versionRepository = VersionRepository(::reportDataError)

    companion object {
        private const val TAG = "AppRepository"
        val KEY_CURRENT_LOCATION = AppPreferenceKeys.KEY_CURRENT_LOCATION
    }

    override fun getAirports(): List<Airport> = airportRepository.getAirports()

    override fun getString(resId: Int, vararg formatArgs: Any): String {
        return context.getString(resId, *formatArgs)
    }

    val allSessions: Flow<List<FlightSession>> = flightSessionRepository.allSessions
    override val recentSessions: Flow<List<FlightSession>> = flightSessionRepository.recentSessions
    val totalFlights: Flow<Int> = flightSessionRepository.totalFlights
    val totalDistance: Flow<Int?> = flightSessionRepository.totalDistance
    val totalFocusMinutes: Flow<Int?> = flightSessionRepository.totalFocusMinutes

    val unitSystem: Flow<String> = settingsRepository.unitSystem
    val appLanguage: Flow<String> = settingsRepository.appLanguage
    val mapStyle: Flow<String> = settingsRepository.mapStyle
    val mapOverlayStyle: Flow<String> = settingsRepository.mapOverlayStyle
    val mapPerspective: Flow<String> = settingsRepository.mapPerspective
    val notificationsEnabled: Flow<Boolean> = settingsRepository.notificationsEnabled
    val notificationUpdateSeconds: Flow<Int> = settingsRepository.notificationUpdateSeconds
    val focusLockEnabled: Flow<Boolean> = settingsRepository.focusLockEnabled
    val focusLockAllowedApps: Flow<Set<String>> = settingsRepository.focusLockAllowedApps
    val screenOrientationMode: Flow<String> = settingsRepository.screenOrientationMode
    override val currentLocation: Flow<Airport?> = settingsRepository.currentLocation
    val sandboxTimeScale: Flow<Float> = settingsRepository.sandboxTimeScale
    val debugFlightMode: Flow<Boolean> = settingsRepository.debugFlightMode

    override val flightTickets: Flow<Int> = ticketRepository.flightTickets
    override val hasCheckedInToday: Flow<Boolean> = ticketRepository.hasCheckedInToday
    override val ticketHistory: Flow<List<FlightTicketHistoryEntry>> = ticketRepository.ticketHistory

    suspend fun saveSession(session: FlightSession) {
        flightSessionRepository.saveSession(session)
    }

    suspend fun setUnitSystem(unit: String) {
        settingsRepository.setUnitSystem(unit)
    }

    suspend fun setAppLanguage(language: String) {
        settingsRepository.setAppLanguage(language)
    }

    suspend fun setMapStyle(style: String) {
        settingsRepository.setMapStyle(style)
    }

    suspend fun setMapPerspective(perspective: String) {
        settingsRepository.setMapPerspective(perspective)
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        settingsRepository.setNotificationsEnabled(enabled)
    }

    suspend fun setNotificationUpdateSeconds(seconds: Int) {
        settingsRepository.setNotificationUpdateSeconds(seconds)
    }

    suspend fun setFocusLockEnabled(enabled: Boolean) {
        settingsRepository.setFocusLockEnabled(enabled)
    }

    suspend fun setFocusLockAllowedApps(packages: Set<String>) {
        settingsRepository.setFocusLockAllowedApps(packages)
    }

    suspend fun setScreenOrientationMode(mode: String) {
        settingsRepository.setScreenOrientationMode(mode)
    }

    suspend fun setCurrentLocation(airport: Airport) {
        settingsRepository.setCurrentLocation(airport)
    }

    suspend fun setSandboxTimeScale(scale: Float) {
        settingsRepository.setSandboxTimeScale(scale)
    }

    suspend fun setDebugFlightMode(enabled: Boolean) {
        settingsRepository.setDebugFlightMode(enabled)
    }

    override suspend fun claimDailyCheckIn(): DailyCheckInResult = ticketRepository.claimDailyCheckIn()

    override suspend fun canStartFlight(estimatedMinutes: Int): TicketSpendResult =
        ticketRepository.canStartFlight()

    suspend fun consumeTicketForLongFlight(): TicketSpendResult =
        ticketRepository.consumeTicketForLongFlight()

    override suspend fun rewardTicketsFromAd(): AdTicketRewardResult = ticketRepository.rewardTicketsFromAd()

    override suspend fun rewardSingleTicketFromInFlightAd(): AdTicketRewardResult =
        ticketRepository.rewardSingleTicketFromInFlightAd()

    override suspend fun redeemCode(code: String): RedeemCodeResult =
        ticketRepository.redeemCode(code)

    override suspend fun fetchVersionStatus(): VersionStatus? =
        versionRepository.fetchVersionStatus()

    private fun reportDataError(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
        _lastDataError.value = "$message: ${throwable.message ?: throwable::class.java.simpleName}"
    }
}

data class TicketSpendResult(
    val success: Boolean,
    val spent: Int,
    val message: String? = null
)

data class AdTicketRewardResult(
    val grantedAmount: Int,
    val remainingAdsUntilNextTicket: Int,
    val currentTierAdsRequired: Int
)

sealed class RedeemCodeResult {
    data class Success(val amount: Int) : RedeemCodeResult()
    data class Error(val message: String) : RedeemCodeResult()
}

sealed class DailyCheckInResult {
    data class Success(val amount: Int) : DailyCheckInResult()
    data class Error(val message: String) : DailyCheckInResult()
}

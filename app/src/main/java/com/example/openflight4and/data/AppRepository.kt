package com.example.openflight4and.data

import android.content.Context
import android.util.Log
import com.example.openflight4and.R
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.openflight4and.data.local.AppDatabase
import com.example.openflight4and.model.Airport
import com.example.openflight4and.model.FlightSession
import com.example.openflight4and.model.FlightTicketHistoryEntry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore by preferencesDataStore(name = "settings")

interface AppRepositoryDataSource {
    fun getAirports(): List<Airport>

    val recentSessions: Flow<List<FlightSession>>
    val currentLocation: Flow<Airport?>
    val flightTickets: Flow<Int>
    val hasCheckedInToday: Flow<Boolean>
    val ticketHistory: Flow<List<FlightTicketHistoryEntry>>

    suspend fun claimDailyCheckIn(): DailyCheckInResult
    suspend fun canStartFlight(_estimatedMinutes: Int): TicketSpendResult
    suspend fun rewardTicketsFromAd(): Int
    suspend fun rewardSingleTicketFromInFlightAd(): Int
    suspend fun redeemCode(code: String): RedeemCodeResult
}

class AppRepository(private val context: Context) : AppRepositoryDataSource {

    private val database = AppDatabase.getDatabase(context)
    private val flightDao = database.flightSessionDao()

    override fun getAirports(): List<Airport> {
        return try {
            val inputStream = context.assets.open("airports.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            Json.decodeFromString<List<Airport>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    val allSessions: Flow<List<FlightSession>> = flightDao.getAllSessions()
    override val recentSessions: Flow<List<FlightSession>> = flightDao.getRecentCompletedSessions()
    val totalFlights: Flow<Int> = flightDao.getTotalFlightsCount()
    val totalDistance: Flow<Int?> = flightDao.getTotalDistance()
    val totalFocusMinutes: Flow<Int?> = flightDao.getTotalFocusMinutes()

    suspend fun saveSession(session: FlightSession) {
        flightDao.insertSession(session)
    }

    companion object {
        val KEY_UNIT_SYSTEM = stringPreferencesKey("unit_system")
        val KEY_MAP_STYLE = stringPreferencesKey("map_style")
        val KEY_MAP_OVERLAY_STYLE = stringPreferencesKey("map_overlay_style")
        val KEY_MAP_PERSPECTIVE = stringPreferencesKey("map_perspective")
        val KEY_AIRPLANE_MODE_CHECK = booleanPreferencesKey("airplane_mode_check")
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val KEY_NOTIFICATION_UPDATE_SECONDS = intPreferencesKey("notification_update_seconds")
        val KEY_LOCK_LEVEL = stringPreferencesKey("lock_level")
        val KEY_FOCUS_LOCK_ENABLED = booleanPreferencesKey("focus_lock_enabled")
        val KEY_SCREEN_ORIENTATION_MODE = stringPreferencesKey("screen_orientation_mode")
        val KEY_CURRENT_LOCATION = stringPreferencesKey("current_location")
        val KEY_SANDBOX_TIME_SCALE = stringPreferencesKey("sandbox_time_scale")
        val KEY_FLIGHT_TICKETS = intPreferencesKey("flight_tickets")
        val KEY_LAST_DAILY_TICKET_DATE = stringPreferencesKey("last_daily_ticket_date")
        val KEY_TICKET_HISTORY = stringPreferencesKey("ticket_history")
        val KEY_USED_REDEEM_CODES = stringPreferencesKey("used_redeem_codes")
        val KEY_DEBUG_FLIGHT_MODE = booleanPreferencesKey("debug_flight_mode")
    }

    val unitSystem: Flow<String> = context.dataStore.data.map { it[KEY_UNIT_SYSTEM] ?: "km" }
    val mapStyle: Flow<String> = context.dataStore.data.map { it[KEY_MAP_STYLE] ?: "standard" }
    val mapOverlayStyle: Flow<String> = context.dataStore.data.map { it[KEY_MAP_OVERLAY_STYLE] ?: "dark" }
    val mapPerspective: Flow<String> = context.dataStore.data.map { it[KEY_MAP_PERSPECTIVE] ?: "2_5d" }
    val airplaneModeCheck: Flow<Boolean> = context.dataStore.data.map { it[KEY_AIRPLANE_MODE_CHECK] ?: true }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFICATIONS] ?: true }
    val notificationUpdateSeconds: Flow<Int> = context.dataStore.data.map {
        (it[KEY_NOTIFICATION_UPDATE_SECONDS] ?: 10).coerceIn(1, 30)
    }
    val focusLockEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_FOCUS_LOCK_ENABLED] ?: false
    }
    val lockLevel: Flow<String> = context.dataStore.data.map { it[KEY_LOCK_LEVEL] ?: "soft" }
    val screenOrientationMode: Flow<String> = context.dataStore.data.map {
        it[KEY_SCREEN_ORIENTATION_MODE] ?: "auto"
    }
    override val currentLocation: Flow<Airport?> = context.dataStore.data.map { preferences ->
        val json = preferences[KEY_CURRENT_LOCATION]
        Log.d("AppRepository", "Reading current location from DataStore: $json")
        json?.let {
            try {
                val airport = Json.decodeFromString<Airport>(it)
                Log.d("AppRepository", "Decoded airport: ${airport.iata}")
                airport
            } catch (e: Exception) {
                Log.e("AppRepository", "Failed to decode airport", e)
                null
            }
        }
    }
    val sandboxTimeScale: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_SANDBOX_TIME_SCALE]?.toFloatOrNull() ?: 1f
    }
    override val flightTickets: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_FLIGHT_TICKETS] ?: 0
    }
    override val hasCheckedInToday: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_LAST_DAILY_TICKET_DATE] == LocalDate.now().toString()
    }
    val debugFlightMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEBUG_FLIGHT_MODE] ?: false
    }
    override val ticketHistory: Flow<List<FlightTicketHistoryEntry>> = context.dataStore.data.map { preferences ->
        decodeTicketHistory(preferences[KEY_TICKET_HISTORY])
            .sortedByDescending { it.timestamp }
    }

    suspend fun setUnitSystem(unit: String) {
        context.dataStore.edit { it[KEY_UNIT_SYSTEM] = unit }
    }

    suspend fun setMapStyle(style: String) {
        context.dataStore.edit { it[KEY_MAP_STYLE] = style }
    }

    suspend fun setMapOverlayStyle(style: String) {
        context.dataStore.edit { it[KEY_MAP_OVERLAY_STYLE] = style }
    }

    suspend fun setMapPerspective(perspective: String) {
        context.dataStore.edit { it[KEY_MAP_PERSPECTIVE] = perspective }
    }

    suspend fun setAirplaneModeCheck(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AIRPLANE_MODE_CHECK] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun setNotificationUpdateSeconds(seconds: Int) {
        context.dataStore.edit {
            it[KEY_NOTIFICATION_UPDATE_SECONDS] = seconds.coerceIn(1, 30)
        }
    }

    suspend fun setFocusLockEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_FOCUS_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setLockLevel(level: String) {
        context.dataStore.edit { it[KEY_LOCK_LEVEL] = level }
    }

    suspend fun setScreenOrientationMode(mode: String) {
        context.dataStore.edit { it[KEY_SCREEN_ORIENTATION_MODE] = mode }
    }

    suspend fun setCurrentLocation(airport: Airport) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CURRENT_LOCATION] = Json.encodeToString(Airport.serializer(), airport)
        }
    }

    suspend fun setSandboxTimeScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SANDBOX_TIME_SCALE] = scale.toString()
        }
    }

    suspend fun setDebugFlightMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEBUG_FLIGHT_MODE] = enabled
        }
    }

    override suspend fun claimDailyCheckIn(): DailyCheckInResult {
        var result: DailyCheckInResult = DailyCheckInResult.Error(context.getString(R.string.repo_daily_check_in_already_done))
        val today = LocalDate.now().toString()

        context.dataStore.edit { preferences ->
            if (preferences[KEY_LAST_DAILY_TICKET_DATE] == today) {
                result = DailyCheckInResult.Error(context.getString(R.string.repo_daily_check_in_already_done))
                return@edit
            }

            val currentBalance = preferences[KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = currentBalance + 1
            preferences[KEY_FLIGHT_TICKETS] = updatedBalance
            preferences[KEY_LAST_DAILY_TICKET_DATE] = today
            preferences[KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[KEY_TICKET_HISTORY],
                FlightTicketHistoryEntry(
                    amount = 1,
                    balanceAfter = updatedBalance,
                    title = context.getString(R.string.repo_daily_check_in_title),
                    detail = context.getString(R.string.repo_daily_check_in_detail)
                )
            )
            result = DailyCheckInResult.Success(1)
        }

        return result
    }

    override suspend fun canStartFlight(_estimatedMinutes: Int): TicketSpendResult {
        val currentBalance = context.dataStore.data.map { preferences ->
            preferences[KEY_FLIGHT_TICKETS] ?: 0
        }

        val balance = currentBalance.first()
        return if (balance > 0) {
            TicketSpendResult(success = true, spent = 0)
        } else {
            TicketSpendResult(
                success = false,
                spent = 0,
                message = context.getString(R.string.repo_ticket_insufficient)
            )
        }
    }

    suspend fun consumeTicketForLongFlight(): TicketSpendResult {
        var result = TicketSpendResult(success = false, spent = 0, message = context.getString(R.string.repo_ticket_insufficient))

        context.dataStore.edit { preferences ->
            val currentBalance = preferences[KEY_FLIGHT_TICKETS] ?: 0
            if (currentBalance <= 0) {
                result = TicketSpendResult(
                    success = false,
                    spent = 0,
                    message = context.getString(R.string.repo_ticket_insufficient)
                )
                return@edit
            }

            val updatedBalance = currentBalance - 1
            preferences[KEY_FLIGHT_TICKETS] = updatedBalance
            preferences[KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[KEY_TICKET_HISTORY],
                FlightTicketHistoryEntry(
                    amount = -1,
                    balanceAfter = updatedBalance,
                    title = context.getString(R.string.repo_ticket_use_title),
                    detail = context.getString(R.string.repo_ticket_use_detail)
                )
            )
            result = TicketSpendResult(success = true, spent = 1)
        }

        return result
    }

    override suspend fun rewardTicketsFromAd(): Int {
        val rewardAmount = 1
        context.dataStore.edit { preferences ->
            val currentBalance = preferences[KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = currentBalance + rewardAmount
            preferences[KEY_FLIGHT_TICKETS] = updatedBalance
            preferences[KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[KEY_TICKET_HISTORY],
                FlightTicketHistoryEntry(
                    amount = rewardAmount,
                    balanceAfter = updatedBalance,
                    title = context.getString(R.string.repo_ad_reward_title),
                    detail = context.getString(R.string.repo_ad_reward_detail)
                )
            )
        }
        return rewardAmount
    }

    override suspend fun rewardSingleTicketFromInFlightAd(): Int {
        val rewardAmount = 1
        context.dataStore.edit { preferences ->
            val currentBalance = preferences[KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = currentBalance + rewardAmount
            preferences[KEY_FLIGHT_TICKETS] = updatedBalance
            preferences[KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[KEY_TICKET_HISTORY],
                FlightTicketHistoryEntry(
                    amount = rewardAmount,
                    balanceAfter = updatedBalance,
                    title = context.getString(R.string.repo_ad_reward_title),
                    detail = context.getString(R.string.repo_inflight_ad_reward_detail)
                )
            )
        }
        return rewardAmount
    }

    override suspend fun redeemCode(code: String): RedeemCodeResult {
        val normalized = code.trim().lowercase()
        if (normalized.isBlank()) {
            return RedeemCodeResult.Error(context.getString(R.string.repo_redeem_enter_code))
        }
        val isReusableAdminCode = normalized.startsWith("admin")

        var result: RedeemCodeResult = RedeemCodeResult.Error(context.getString(R.string.repo_redeem_invalid))
        val rewardAmount = when (normalized) {
            "admin" -> 1
            "admin10" -> 10
            "admin100" -> 100
            else -> null
        }

        context.dataStore.edit { preferences ->
            val usedCodes = decodeStringList(preferences[KEY_USED_REDEEM_CODES]).toMutableSet()

            if (!isReusableAdminCode && normalized in usedCodes) {
                result = RedeemCodeResult.Error(context.getString(R.string.repo_redeem_already_used))
                return@edit
            }

            if (rewardAmount == null) {
                result = RedeemCodeResult.Error(context.getString(R.string.repo_redeem_invalid))
                return@edit
            }

            val currentBalance = preferences[KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = currentBalance + rewardAmount

            preferences[KEY_FLIGHT_TICKETS] = updatedBalance
            if (!isReusableAdminCode) {
                usedCodes += normalized
                preferences[KEY_USED_REDEEM_CODES] = Json.encodeToString(usedCodes.toList())
            }
            preferences[KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[KEY_TICKET_HISTORY],
                FlightTicketHistoryEntry(
                    amount = rewardAmount,
                    balanceAfter = updatedBalance,
                    title = context.getString(R.string.repo_redeem_title),
                    detail = context.getString(R.string.repo_redeem_detail_format, normalized.uppercase(), rewardAmount)
                )
            )
            result = RedeemCodeResult.Success(rewardAmount)
        }

        return result
    }

    private fun appendHistoryEntry(
        existingJson: String?,
        entry: FlightTicketHistoryEntry
    ): String {
        val updated = decodeTicketHistory(existingJson).toMutableList().apply {
            add(entry)
            sortByDescending { it.timestamp }
            if (size > 100) {
                subList(100, size).clear()
            }
        }
        return Json.encodeToString(updated)
    }

    private fun decodeTicketHistory(json: String?): List<FlightTicketHistoryEntry> {
        return try {
            if (json.isNullOrBlank()) emptyList() else Json.decodeFromString(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun decodeStringList(json: String?): List<String> {
        return try {
            if (json.isNullOrBlank()) emptyList() else Json.decodeFromString(json)
        } catch (_: Exception) {
            emptyList()
        }
    }

}

data class TicketSpendResult(
    val success: Boolean,
    val spent: Int,
    val message: String? = null
)

sealed class RedeemCodeResult {
    data class Success(val amount: Int) : RedeemCodeResult()
    data class Error(val message: String) : RedeemCodeResult()
}

sealed class DailyCheckInResult {
    data class Success(val amount: Int) : DailyCheckInResult()
    data class Error(val message: String) : DailyCheckInResult()
}

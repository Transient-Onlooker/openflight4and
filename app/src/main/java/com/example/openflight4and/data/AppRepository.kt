package com.example.openflight4and.data

import android.content.Context
import android.util.Log
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

class AppRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val flightDao = database.flightSessionDao()

    fun getAirports(): List<Airport> {
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
    val recentSessions: Flow<List<FlightSession>> = flightDao.getRecentCompletedSessions()
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
    val lockLevel: Flow<String> = context.dataStore.data.map { it[KEY_LOCK_LEVEL] ?: "soft" }
    val currentLocation: Flow<Airport?> = context.dataStore.data.map { preferences ->
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
    val flightTickets: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_FLIGHT_TICKETS] ?: 0
    }
    val debugFlightMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEBUG_FLIGHT_MODE] ?: false
    }
    val ticketHistory: Flow<List<FlightTicketHistoryEntry>> = context.dataStore.data.map { preferences ->
        decodeTicketHistory(preferences[KEY_TICKET_HISTORY])
            .map(::localizeLegacyTicketHistoryEntry)
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

    suspend fun setLockLevel(level: String) {
        context.dataStore.edit { it[KEY_LOCK_LEVEL] = level }
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

    suspend fun grantDailyTicketIfNeeded(): Int {
        var granted = 0
        val today = LocalDate.now().toString()

        context.dataStore.edit { preferences ->
            if (preferences[KEY_LAST_DAILY_TICKET_DATE] == today) return@edit

            val currentBalance = preferences[KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = currentBalance + 1
            preferences[KEY_FLIGHT_TICKETS] = updatedBalance
            preferences[KEY_LAST_DAILY_TICKET_DATE] = today
            preferences[KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[KEY_TICKET_HISTORY],
                FlightTicketHistoryEntry(
                    amount = 1,
                    balanceAfter = updatedBalance,
                    title = "?쇱씪 鍮꾪뻾沅?,
                    detail = "?쇱씪 鍮꾪뻾沅?1媛쒓? 吏湲됰릺?덉뒿?덈떎."
                )
            )
            granted = 1
        }

        return granted
    }

    suspend fun canStartFlight(estimatedMinutes: Int): TicketSpendResult {
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
                message = "鍮꾪뻾沅뚯씠 遺議깊빀?덈떎."
            )
        }
    }

    suspend fun consumeTicketForLongFlight(): TicketSpendResult {
        var result = TicketSpendResult(success = false, spent = 0, message = "鍮꾪뻾沅뚯씠 遺議깊빀?덈떎.")

        context.dataStore.edit { preferences ->
            val currentBalance = preferences[KEY_FLIGHT_TICKETS] ?: 0
            if (currentBalance <= 0) {
                result = TicketSpendResult(
                    success = false,
                    spent = 0,
                    message = "鍮꾪뻾沅뚯씠 遺議깊빀?덈떎."
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
                    title = "鍮꾪뻾沅??ъ슜",
                    detail = "10遺??댁긽 鍮꾪뻾?쇰줈 鍮꾪뻾沅?1媛쒓? 李④컧?섏뿀?듬땲??"
                )
            )
            result = TicketSpendResult(success = true, spent = 1)
        }

        return result
    }

    suspend fun rewardTicketsFromAd(): Int {
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
                    title = "愿묎퀬 蹂댁긽",
                    detail = "30초 광고 보상으로 비행권 1개가 지급되었습니다."
                )
            )
        }
        return rewardAmount
    }

    suspend fun rewardSingleTicketFromInFlightAd(): Int {
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
                    title = "愿묎퀬 蹂댁긽",
                    detail = "鍮꾪뻾 以?30珥?愿묎퀬 蹂댁긽?쇰줈 鍮꾪뻾沅?1媛쒓? 吏湲됰릺?덉뒿?덈떎."
                )
            )
        }
        return rewardAmount
    }

    suspend fun redeemCode(code: String): RedeemCodeResult {
        val normalized = code.trim().lowercase()
        if (normalized.isBlank()) {
            return RedeemCodeResult.Error("肄붾뱶瑜??낅젰??二쇱꽭??")
        }

        var result: RedeemCodeResult = RedeemCodeResult.Error("?좏슚?섏? ?딆? 肄붾뱶?낅땲??")
        val rewardAmount = when (normalized) {
            "admin" -> 1
            "admin10" -> 10
            "admin100" -> 100
            else -> null
        }

        context.dataStore.edit { preferences ->
            val usedCodes = decodeStringList(preferences[KEY_USED_REDEEM_CODES]).toMutableSet()

            if (normalized in usedCodes) {
                result = RedeemCodeResult.Error("?대? ?ъ슜??肄붾뱶?낅땲??")
                return@edit
            }

            if (rewardAmount == null) {
                result = RedeemCodeResult.Error("?좏슚?섏? ?딆? 肄붾뱶?낅땲??")
                return@edit
            }

            val currentBalance = preferences[KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = currentBalance + rewardAmount

            usedCodes += normalized
            preferences[KEY_FLIGHT_TICKETS] = updatedBalance
            preferences[KEY_USED_REDEEM_CODES] = Json.encodeToString(usedCodes.toList())
            preferences[KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[KEY_TICKET_HISTORY],
                FlightTicketHistoryEntry(
                    amount = rewardAmount,
                    balanceAfter = updatedBalance,
                    title = "由щ뵥 肄붾뱶",
                    detail = "${normalized.uppercase()} 肄붾뱶濡?鍮꾪뻾沅?${rewardAmount}媛쒓? 吏湲됰릺?덉뒿?덈떎."
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

    private fun localizeLegacyTicketHistoryEntry(entry: FlightTicketHistoryEntry): FlightTicketHistoryEntry {
        val localizedTitle = when (entry.title.trim()) {
            "Daily ticket" -> "?쇱씪 鍮꾪뻾沅?
            "Flight ticket used" -> "鍮꾪뻾沅??ъ슜"
            "Ad reward" -> "愿묎퀬 蹂댁긽"
            "Redeem code" -> "由щ뵥 肄붾뱶"
            else -> entry.title
        }

        val localizedDetail = localizeLegacyTicketHistoryDetail(entry.detail)

        return if (localizedTitle == entry.title && localizedDetail == entry.detail) {
            entry
        } else {
            entry.copy(title = localizedTitle, detail = localizedDetail)
        }
    }

    private fun localizeLegacyTicketHistoryDetail(detail: String): String {
        val trimmed = detail.trim()
        if (trimmed.isEmpty()) return detail

        if (trimmed.equals("Daily ticket granted.", ignoreCase = true)) {
            return "?쇱씪 鍮꾪뻾沅?1媛쒓? 吏湲됰릺?덉뒿?덈떎."
        }

        if (
            trimmed.equals("Consumed 1 ticket for a 10+ minute flight.", ignoreCase = true) ||
            trimmed.equals("Consumed 1 ticket for a flight over 10 minutes.", ignoreCase = true) ||
            trimmed.equals("Consumed 1 ticket.", ignoreCase = true)
        ) {
            return "10遺??댁긽 鍮꾪뻾?쇰줈 鍮꾪뻾沅?1媛쒓? 李④컧?섏뿀?듬땲??"
        }

        if (
            trimmed.equals("Rewarded 3 tickets from ad reward.", ignoreCase = true) ||
            trimmed.equals("Earned 3 tickets by watching an ad.", ignoreCase = true)
        ) {
            return "30珥?愿묎퀬 蹂댁긽?쇰줈 鍮꾪뻾沅?3媛쒓? 吏湲됰릺?덉뒿?덈떎."
        }

        val redeemMatch = Regex(
            pattern = """Redeemed\s+(\d+)\s+tickets?\s+with\s+([A-Za-z0-9_-]+)\s+code\.?""",
            option = RegexOption.IGNORE_CASE
        ).matchEntire(trimmed)
        if (redeemMatch != null) {
            val amount = redeemMatch.groupValues[1]
            val code = redeemMatch.groupValues[2].uppercase()
            return "${code} 肄붾뱶濡?鍮꾪뻾沅?${amount}媛쒓? 吏湲됰릺?덉뒿?덈떎."
        }

        return detail
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

package com.example.openflight4and.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.openflight4and.BuildConfig
import com.example.openflight4and.R
import com.example.openflight4and.model.FlightTicketHistoryEntry
import java.time.LocalDate
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TicketRepository(
    private val context: Context,
    private val reportDataError: (String, Throwable) -> Unit
) {
    private val json = Json { ignoreUnknownKeys = true }

    val flightTickets: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] ?: 0
    }
    val hasCheckedInToday: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AppPreferenceKeys.KEY_LAST_DAILY_TICKET_DATE] == LocalDate.now().toString()
    }
    val ticketHistory: Flow<List<FlightTicketHistoryEntry>> = context.dataStore.data.map { preferences ->
        decodeTicketHistory(preferences[AppPreferenceKeys.KEY_TICKET_HISTORY]).sortedByDescending { it.timestamp }
    }

    suspend fun claimDailyCheckIn(): DailyCheckInResult {
        var result: DailyCheckInResult = DailyCheckInResult.Error(context.getString(R.string.repo_daily_check_in_already_done))
        val today = LocalDate.now().toString()

        context.dataStore.edit { preferences ->
            if (preferences[AppPreferenceKeys.KEY_LAST_DAILY_TICKET_DATE] == today) {
                result = DailyCheckInResult.Error(context.getString(R.string.repo_daily_check_in_already_done))
                return@edit
            }

            val currentBalance = preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = currentBalance + 1
            preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] = updatedBalance
            preferences[AppPreferenceKeys.KEY_LAST_DAILY_TICKET_DATE] = today
            preferences[AppPreferenceKeys.KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[AppPreferenceKeys.KEY_TICKET_HISTORY],
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

    suspend fun canStartFlight(): TicketSpendResult {
        val balance = flightTickets.first()
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
            val currentBalance = preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] ?: 0
            if (currentBalance <= 0) {
                result = TicketSpendResult(
                    success = false,
                    spent = 0,
                    message = context.getString(R.string.repo_ticket_insufficient)
                )
                return@edit
            }

            val updatedBalance = currentBalance - 1
            preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] = updatedBalance
            preferences[AppPreferenceKeys.KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[AppPreferenceKeys.KEY_TICKET_HISTORY],
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

    suspend fun rewardTicketsFromAd(): Int {
        val rewardAmount = 1
        context.dataStore.edit { preferences ->
            val currentBalance = preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = currentBalance + rewardAmount
            preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] = updatedBalance
            preferences[AppPreferenceKeys.KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[AppPreferenceKeys.KEY_TICKET_HISTORY],
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

    suspend fun rewardSingleTicketFromInFlightAd(): Int {
        val rewardAmount = 1
        context.dataStore.edit { preferences ->
            val currentBalance = preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = currentBalance + rewardAmount
            preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] = updatedBalance
            preferences[AppPreferenceKeys.KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[AppPreferenceKeys.KEY_TICKET_HISTORY],
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

    suspend fun redeemCode(code: String): RedeemCodeResult {
        val normalized = code.trim().lowercase()
        if (normalized.isBlank()) {
            return RedeemCodeResult.Error(context.getString(R.string.repo_redeem_enter_code))
        }

        val response = try {
            redeemCodeRemote(normalized)
        } catch (e: Exception) {
            reportDataError("Failed to redeem code from remote API", e)
            return RedeemCodeResult.Error(context.getString(R.string.repo_redeem_network_error))
        }

        if (!response.ok || response.rewardAmount == null) {
            return RedeemCodeResult.Error(mapRedeemError(response.error))
        }

        val rewardAmount = response.rewardAmount
        context.dataStore.edit { preferences ->
            val currentBalance = preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = currentBalance + rewardAmount
            preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] = updatedBalance
            preferences[AppPreferenceKeys.KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[AppPreferenceKeys.KEY_TICKET_HISTORY],
                FlightTicketHistoryEntry(
                    amount = rewardAmount,
                    balanceAfter = updatedBalance,
                    title = context.getString(R.string.repo_redeem_title),
                    detail = context.getString(
                        R.string.repo_redeem_detail_format,
                        normalized.uppercase(),
                        rewardAmount
                    )
                )
            )
        }

        return RedeemCodeResult.Success(rewardAmount)
    }

    private fun appendHistoryEntry(existingJson: String?, entry: FlightTicketHistoryEntry): String {
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
        } catch (e: Exception) {
            reportDataError("Failed to decode ticket history", e)
            emptyList()
        }
    }

    private suspend fun redeemCodeRemote(code: String): RedeemApiResponse = withContext(Dispatchers.IO) {
        val connection = (URL("${BuildConfig.REDEEM_API_BASE_URL}/redeem").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val payload = json.encodeToString(RedeemApiRequest(code = code.uppercase()))
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(payload)
            }

            val stream = if (connection.responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            json.decodeFromString<RedeemApiResponse>(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun mapRedeemError(error: String?): String {
        return when (error) {
            "Code already fully used" -> context.getString(R.string.repo_redeem_already_used)
            "Code expired" -> context.getString(R.string.repo_redeem_expired)
            "Code is disabled" -> context.getString(R.string.repo_redeem_invalid)
            "Code is required" -> context.getString(R.string.repo_redeem_enter_code)
            "Invalid code" -> context.getString(R.string.repo_redeem_invalid)
            else -> context.getString(R.string.repo_redeem_unavailable)
        }
    }
}

@Serializable
private data class RedeemApiRequest(
    val code: String
)

@Serializable
private data class RedeemApiResponse(
    val ok: Boolean,
    val error: String? = null,
    val rewardAmount: Int? = null
)

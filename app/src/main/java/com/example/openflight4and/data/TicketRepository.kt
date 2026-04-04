package com.example.openflight4and.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.openflight4and.R
import com.example.openflight4and.model.FlightTicketHistoryEntry
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TicketRepository(
    private val context: Context,
    private val reportDataError: (String, Throwable) -> Unit
) {
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
        val isReusableAdminCode = normalized.startsWith("admin")

        var result: RedeemCodeResult = RedeemCodeResult.Error(context.getString(R.string.repo_redeem_invalid))
        val rewardAmount = when (normalized) {
            "admin" -> 1
            "admin10" -> 10
            "admin100" -> 100
            else -> null
        }

        context.dataStore.edit { preferences ->
            val usedCodes = decodeStringList(preferences[AppPreferenceKeys.KEY_USED_REDEEM_CODES]).toMutableSet()

            if (!isReusableAdminCode && normalized in usedCodes) {
                result = RedeemCodeResult.Error(context.getString(R.string.repo_redeem_already_used))
                return@edit
            }

            if (rewardAmount == null) {
                result = RedeemCodeResult.Error(context.getString(R.string.repo_redeem_invalid))
                return@edit
            }

            val currentBalance = preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = currentBalance + rewardAmount

            preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] = updatedBalance
            if (!isReusableAdminCode) {
                usedCodes += normalized
                preferences[AppPreferenceKeys.KEY_USED_REDEEM_CODES] = Json.encodeToString(usedCodes.toList())
            }
            preferences[AppPreferenceKeys.KEY_TICKET_HISTORY] = appendHistoryEntry(
                preferences[AppPreferenceKeys.KEY_TICKET_HISTORY],
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

    private fun decodeStringList(json: String?): List<String> {
        return try {
            if (json.isNullOrBlank()) emptyList() else Json.decodeFromString(json)
        } catch (e: Exception) {
            reportDataError("Failed to decode used redeem codes", e)
            emptyList()
        }
    }
}

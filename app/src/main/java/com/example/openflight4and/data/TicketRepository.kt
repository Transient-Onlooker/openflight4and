package com.example.openflight4and.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.example.openflight4and.BuildConfig
import com.example.openflight4and.R
import com.example.openflight4and.model.FlightTicketHistoryEntry
import java.time.LocalDate
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
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
    private val accountRepository: AccountRepository,
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

        if (result is DailyCheckInResult.Success) {
            syncOrQueueTicketEvent(
                eventCode = TicketEventCode.DAILY_CHECK_IN,
                delta = 1,
                title = context.getString(R.string.repo_daily_check_in_title),
                detail = context.getString(R.string.repo_daily_check_in_detail)
            )
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

        if (result.success && result.spent > 0) {
            syncOrQueueTicketEvent(
                eventCode = TicketEventCode.FLIGHT_USE,
                delta = -result.spent,
                title = context.getString(R.string.repo_ticket_use_title),
                detail = context.getString(R.string.repo_ticket_use_detail)
            )
        }
        return result
    }

    suspend fun rewardTicketsFromAd(): AdTicketRewardResult {
        return rewardTicketFromAdInternal(R.string.repo_ad_reward_detail_format)
    }

    suspend fun rewardSingleTicketFromInFlightAd(): AdTicketRewardResult {
        return rewardTicketFromAdInternal(R.string.repo_inflight_ad_reward_detail_format)
    }

    suspend fun getAdRewardTierWarningMessage(): String? {
        val today = LocalDate.now().toString()
        val preferences = context.dataStore.data.first()
        val watchedToday = if (preferences[AppPreferenceKeys.KEY_AD_REWARD_DATE] == today) {
            preferences[AppPreferenceKeys.KEY_AD_WATCH_COUNT_TODAY] ?: 0
        } else {
            0
        }
        val nextWatchCount = watchedToday + 1
        return when (nextWatchCount) {
            3 -> context.getString(R.string.tickets_toast_ad_reward_tier_two_notice)
            7 -> context.getString(R.string.tickets_toast_ad_reward_tier_three_notice)
            else -> null
        }
    }

    suspend fun redeemCode(code: String): RedeemCodeResult {
        val normalized = code.trim().lowercase()
        if (normalized.isBlank()) {
            return RedeemCodeResult.Error(context.getString(R.string.repo_redeem_enter_code))
        }

        val installationId = getOrCreateInstallationId()

        val response = try {
            redeemCodeRemote(normalized, installationId)
        } catch (e: Exception) {
            reportDataError("Failed to redeem code from remote API", e)
            return RedeemCodeResult.Error(context.getString(R.string.repo_redeem_network_error))
        }

        if (!response.ok || response.rewardAmount == null) {
            return RedeemCodeResult.Error(mapRedeemError(response.error))
        }

        val rewardAmount = response.rewardAmount
        val serverTicketCount = response.ticketCount
        context.dataStore.edit { preferences ->
            val currentBalance = preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] ?: 0
            val updatedBalance = serverTicketCount ?: (currentBalance + rewardAmount)
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

    private suspend fun rewardTicketFromAdInternal(detailResId: Int): AdTicketRewardResult {
        val today = LocalDate.now().toString()
        var grantedAdsRequired = 0
        var result = AdTicketRewardResult(
            grantedAmount = 0,
            remainingAdsUntilNextTicket = 1,
            currentTierAdsRequired = 1
        )

        context.dataStore.edit { preferences ->
            val isSameDay = preferences[AppPreferenceKeys.KEY_AD_REWARD_DATE] == today
            val watchedToday = if (isSameDay) {
                preferences[AppPreferenceKeys.KEY_AD_WATCH_COUNT_TODAY] ?: 0
            } else {
                0
            }
            val progress = if (isSameDay) {
                preferences[AppPreferenceKeys.KEY_AD_REWARD_PROGRESS] ?: 0
            } else {
                0
            }
            val updatedWatchCount = watchedToday + 1
            val adsRequired = adsRequiredFor(updatedWatchCount)
            val updatedProgress = progress + 1

            preferences[AppPreferenceKeys.KEY_AD_REWARD_DATE] = today
            preferences[AppPreferenceKeys.KEY_AD_WATCH_COUNT_TODAY] = updatedWatchCount

            if (updatedProgress >= adsRequired) {
                val rewardAmount = 1
                val currentBalance = preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] ?: 0
                val updatedBalance = currentBalance + rewardAmount
                preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] = updatedBalance
                preferences[AppPreferenceKeys.KEY_AD_REWARD_PROGRESS] = 0
                preferences[AppPreferenceKeys.KEY_TICKET_HISTORY] = appendHistoryEntry(
                    preferences[AppPreferenceKeys.KEY_TICKET_HISTORY],
                    FlightTicketHistoryEntry(
                        amount = rewardAmount,
                        balanceAfter = updatedBalance,
                        title = context.getString(R.string.repo_ad_reward_title),
                        detail = context.getString(detailResId, adsRequired)
                    )
                )
                val nextRequired = adsRequiredFor(updatedWatchCount + 1)
                result = AdTicketRewardResult(
                    grantedAmount = rewardAmount,
                    remainingAdsUntilNextTicket = nextRequired,
                    currentTierAdsRequired = adsRequired
                )
                grantedAdsRequired = adsRequired
            } else {
                preferences[AppPreferenceKeys.KEY_AD_REWARD_PROGRESS] = updatedProgress
                result = AdTicketRewardResult(
                    grantedAmount = 0,
                    remainingAdsUntilNextTicket = adsRequired - updatedProgress,
                    currentTierAdsRequired = adsRequired
                )
            }
        }

        if (result.grantedAmount > 0) {
            syncOrQueueTicketEvent(
                eventCode = TicketEventCode.AD_REWARD,
                delta = result.grantedAmount,
                title = context.getString(R.string.repo_ad_reward_title),
                detail = context.getString(detailResId, grantedAdsRequired)
            )
        }
        return result
    }

    private fun adsRequiredFor(watchCountToday: Int): Int {
        return when {
            watchCountToday >= 7 -> 3
            watchCountToday >= 3 -> 2
            else -> 1
        }
    }

    private suspend fun redeemCodeRemote(code: String, installationId: String): RedeemApiResponse = withContext(Dispatchers.IO) {
        val idToken = accountRepository.getIdTokenOrNull()
        val connection = (URL("${BuildConfig.REDEEM_API_BASE_URL}/redeem").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (!idToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $idToken")
            }
        }

        try {
            val payload = json.encodeToString(
                RedeemApiRequest(
                    code = code.uppercase(),
                    deviceId = installationId
                )
            )
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
            "Already redeemed by this device" -> context.getString(R.string.repo_redeem_already_used)
            "Code already fully used" -> context.getString(R.string.repo_redeem_already_used)
            "Code expired" -> context.getString(R.string.repo_redeem_expired)
            "Code is disabled" -> context.getString(R.string.repo_redeem_invalid)
            "Code is required" -> context.getString(R.string.repo_redeem_enter_code)
            "Invalid code" -> context.getString(R.string.repo_redeem_invalid)
            else -> context.getString(R.string.repo_redeem_unavailable)
        }
    }

    private suspend fun getOrCreateInstallationId(): String {
        var installationId = ""
        context.dataStore.edit { preferences ->
            installationId = preferences[AppPreferenceKeys.KEY_INSTALLATION_ID]
                ?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().also {
                    preferences[AppPreferenceKeys.KEY_INSTALLATION_ID] = it
                }
        }
        return installationId
    }

    suspend fun resetTicketData() {
        context.dataStore.edit { preferences ->
            preferences.remove(AppPreferenceKeys.KEY_FLIGHT_TICKETS)
            preferences.remove(AppPreferenceKeys.KEY_LAST_DAILY_TICKET_DATE)
            preferences.remove(AppPreferenceKeys.KEY_AD_REWARD_DATE)
            preferences.remove(AppPreferenceKeys.KEY_AD_WATCH_COUNT_TODAY)
            preferences.remove(AppPreferenceKeys.KEY_AD_REWARD_PROGRESS)
            preferences.remove(AppPreferenceKeys.KEY_TICKET_HISTORY)
            preferences.remove(AppPreferenceKeys.KEY_USED_REDEEM_CODES)
            preferences.remove(AppPreferenceKeys.KEY_INSTALLATION_ID)
            preferences.remove(AppPreferenceKeys.KEY_PENDING_TICKET_EVENTS)
        }
    }

    suspend fun syncPendingTicketEvents() {
        val idToken = accountRepository.getIdTokenOrNull() ?: return
        val pendingEvents = context.dataStore.data.first()[AppPreferenceKeys.KEY_PENDING_TICKET_EVENTS]
            ?.let(::decodePendingEvents)
            .orEmpty()
        if (pendingEvents.isEmpty()) return

        val remaining = mutableListOf<PendingTicketEvent>()
        var latestTicketCount: Int? = null
        pendingEvents.forEach { event ->
            val response = try {
                postTicketEvent(idToken, event)
            } catch (e: Exception) {
                reportDataError("Failed to sync pending ticket event", e)
                remaining += event
                return@forEach
            }
            if (response.ok && response.ticketCount != null) {
                latestTicketCount = response.ticketCount
            } else {
                remaining += event
            }
        }

        context.dataStore.edit { preferences ->
            if (latestTicketCount != null) {
                preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] = latestTicketCount!!
            }
            if (remaining.isEmpty()) {
                preferences.remove(AppPreferenceKeys.KEY_PENDING_TICKET_EVENTS)
            } else {
                preferences[AppPreferenceKeys.KEY_PENDING_TICKET_EVENTS] = json.encodeToString(remaining)
            }
        }
    }

    suspend fun mergeLocalTicketsWithAccount(serverTicketCount: Int): Int {
        val localTicketCount = flightTickets.first()
        val mergedTicketCount = maxOf(serverTicketCount, localTicketCount)
        val idToken = accountRepository.getIdTokenOrNull()
        val confirmedTicketCount = if (idToken == null) {
            mergedTicketCount
        } else {
            try {
                mergeTicketsRemote(idToken, mergedTicketCount).ticketCount ?: mergedTicketCount
            } catch (e: Exception) {
                reportDataError("Failed to merge local tickets with account", e)
                mergedTicketCount
            }
        }
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] = confirmedTicketCount
        }
        syncPendingTicketEvents()
        return confirmedTicketCount
    }

    private suspend fun syncOrQueueTicketEvent(
        eventCode: String,
        delta: Int,
        title: String,
        detail: String
    ) {
        val idToken = accountRepository.getIdTokenOrNull()
        if (idToken == null) return
        val event = PendingTicketEvent(
            clientEventId = UUID.randomUUID().toString(),
            eventCode = eventCode,
            delta = delta,
            title = title,
            detail = detail,
            createdAt = System.currentTimeMillis()
        )
        try {
            val response = postTicketEvent(idToken, event)
            if (response.ok && response.ticketCount != null) {
                context.dataStore.edit { preferences ->
                    preferences[AppPreferenceKeys.KEY_FLIGHT_TICKETS] = response.ticketCount
                }
                syncPendingTicketEvents()
                return
            }
        } catch (e: Exception) {
            reportDataError("Failed to sync ticket event", e)
        }
        queuePendingTicketEvent(event)
    }

    private suspend fun queuePendingTicketEvent(event: PendingTicketEvent) {
        context.dataStore.edit { preferences ->
            val current = decodePendingEvents(preferences[AppPreferenceKeys.KEY_PENDING_TICKET_EVENTS]).toMutableList()
            current += event
            preferences[AppPreferenceKeys.KEY_PENDING_TICKET_EVENTS] = json.encodeToString(current.takeLast(200))
        }
    }

    private fun decodePendingEvents(value: String?): List<PendingTicketEvent> {
        return try {
            if (value.isNullOrBlank()) emptyList() else json.decodeFromString(value)
        } catch (e: Exception) {
            reportDataError("Failed to decode pending ticket events", e)
            emptyList()
        }
    }

    private suspend fun postTicketEvent(idToken: String, event: PendingTicketEvent): TicketEventApiResponse =
        withContext(Dispatchers.IO) {
            val connection = (URL("${BuildConfig.REDEEM_API_BASE_URL}/tickets/events").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $idToken")
            }
            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(json.encodeToString(event))
                }
                val stream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                }
                json.decodeFromString(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun mergeTicketsRemote(idToken: String, localTicketCount: Int): TicketEventApiResponse =
        withContext(Dispatchers.IO) {
            val connection = (URL("${BuildConfig.REDEEM_API_BASE_URL}/tickets/merge").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer $idToken")
            }
            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(json.encodeToString(TicketMergeRequest(localTicketCount = localTicketCount, strategy = "max")))
                }
                val stream = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                }
                json.decodeFromString(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }
}

@Serializable
private data class RedeemApiRequest(
    val code: String,
    val deviceId: String
)

@Serializable
private data class RedeemApiResponse(
    val ok: Boolean,
    val error: String? = null,
    val rewardAmount: Int? = null,
    val ticketCount: Int? = null
)

private object TicketEventCode {
    const val FLIGHT_USE = "A"
    const val TRANSFER_SENT = "B1"
    const val TRANSFER_RECEIVED = "B2"
    const val REDEEM = "C"
    const val DAILY_CHECK_IN = "D"
    const val AD_REWARD = "E"
    const val CORRECTION = "G"
    const val LOGIN_MERGE = "I"
}

@Serializable
private data class PendingTicketEvent(
    val clientEventId: String,
    val eventCode: String,
    val delta: Int,
    val title: String,
    val detail: String,
    val createdAt: Long
)

@Serializable
private data class TicketMergeRequest(
    val localTicketCount: Int,
    val strategy: String
)

@Serializable
private data class TicketEventApiResponse(
    val ok: Boolean,
    val error: String? = null,
    val ticketCount: Int? = null
)

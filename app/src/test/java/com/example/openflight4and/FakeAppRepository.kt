package com.example.openflight4and

import com.example.openflight4and.data.AdTicketRewardResult
import com.example.openflight4and.data.AppRepositoryDataSource
import com.example.openflight4and.data.DailyCheckInResult
import com.example.openflight4and.data.RedeemCodeResult
import com.example.openflight4and.data.TicketSpendResult
import com.example.openflight4and.data.VersionStatus
import com.example.openflight4and.model.Airport
import com.example.openflight4and.model.FlightSession
import com.example.openflight4and.model.FlightTicketHistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeAppRepository(
    private val airports: List<Airport> = emptyList()
) : AppRepositoryDataSource {
    override fun getAirports(): List<Airport> = airports

    override fun getString(resId: Int, vararg formatArgs: Any): String {
        return when (resId) {
            R.string.tickets_toast_daily_check_in_reward -> "출석체크 보상으로 비행권 ${formatArgs[0]}개가 지급되었습니다."
            R.string.tickets_toast_ad_reward -> "광고 보상으로 비행권 ${formatArgs[0]}개가 지급되었습니다."
            R.string.tickets_toast_ad_reward_progress ->
                "현재 구간은 광고 ${formatArgs[0]}회당 비행권 1장입니다. 광고를 ${formatArgs[1]}회 더 보면 비행권이 지급됩니다."
            R.string.tickets_ad_reward_unavailable -> "광고를 불러오지 못했습니다. 잠시 후 다시 시도해주세요."
            R.string.tickets_toast_redeem_success -> "비행권 ${formatArgs[0]}개가 지급되었습니다."
            R.string.repo_redeem_enter_code -> "코드를 입력해주세요."
            else -> error("Unsupported string resource id: $resId")
        }
    }

    override val recentSessions = MutableStateFlow<List<FlightSession>>(emptyList())
    override val currentLocation = MutableStateFlow<Airport?>(null)
    override val flightTickets = MutableStateFlow(0)
    override val hasCheckedInToday = MutableStateFlow(false)
    override val ticketHistory = MutableStateFlow<List<FlightTicketHistoryEntry>>(emptyList())

    var nextDailyCheckInResult: DailyCheckInResult = DailyCheckInResult.Success(1)
    var nextStartFlightResult: TicketSpendResult = TicketSpendResult(success = true, spent = 0)
    var nextRedeemCodeResult: RedeemCodeResult = RedeemCodeResult.Success(1)
    var nextVersionStatus: VersionStatus? = null
    var rewardedAds = 0
    var rewardedInflightAds = 0
    var nextAdRewardResult = AdTicketRewardResult(1, 1, 1)
    var nextInflightAdRewardResult = AdTicketRewardResult(1, 1, 1)
    var nextAdRewardTierWarningMessage: String? = null

    override suspend fun claimDailyCheckIn(): DailyCheckInResult {
        return when (val result = nextDailyCheckInResult) {
            is DailyCheckInResult.Success -> {
                flightTickets.update { it + result.amount }
                hasCheckedInToday.value = true
                ticketHistory.update {
                    listOf(
                        FlightTicketHistoryEntry(
                            amount = result.amount,
                            balanceAfter = flightTickets.value,
                            title = "출석 체크",
                            detail = "출석체크 보상으로 비행권 ${result.amount}개가 지급되었습니다."
                        )
                    ) + it
                }
                result
            }

            is DailyCheckInResult.Error -> result
        }
    }

    override suspend fun canStartFlight(estimatedMinutes: Int): TicketSpendResult {
        return nextStartFlightResult
    }

    override suspend fun rewardTicketsFromAd(): AdTicketRewardResult {
        rewardedAds += 1
        flightTickets.update { it + nextAdRewardResult.grantedAmount }
        return nextAdRewardResult
    }

    override suspend fun rewardSingleTicketFromInFlightAd(): AdTicketRewardResult {
        rewardedInflightAds += 1
        flightTickets.update { it + nextInflightAdRewardResult.grantedAmount }
        return nextInflightAdRewardResult
    }

    override suspend fun getAdRewardTierWarningMessage(): String? = nextAdRewardTierWarningMessage

    override suspend fun redeemCode(code: String): RedeemCodeResult {
        return when (val result = nextRedeemCodeResult) {
            is RedeemCodeResult.Success -> {
                flightTickets.update { it + result.amount }
                result
            }

            is RedeemCodeResult.Error -> result
        }
    }

    override suspend fun fetchVersionStatus(): VersionStatus? = nextVersionStatus
}

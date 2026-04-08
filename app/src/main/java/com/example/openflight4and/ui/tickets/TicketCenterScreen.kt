package com.example.openflight4and.ui.tickets

import android.app.Application
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openflight4and.R
import com.example.openflight4and.BuildConfig
import com.example.openflight4and.model.FlightTicketHistoryEntry
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.theme.FlightBlack
import com.example.openflight4and.ui.theme.FlightGray
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketCenterScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val viewModel: TicketCenterViewModel = viewModel(
        factory = TicketCenterViewModel.Factory(context.applicationContext as Application)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TicketCenterEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                TicketCenterEvent.LaunchRewardedAd -> {
                    if (activity == null) {
                        viewModel.handleAdLoadFailed(context.getString(R.string.tickets_ad_reward_unavailable))
                        return@collect
                    }

                    val adRequest = AdRequest.Builder().build()
                    RewardedAd.load(
                        context,
                        BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
                        adRequest,
                        object : RewardedAdLoadCallback() {
                            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                viewModel.handleAdLoadFailed(loadAdError.message)
                            }

                            override fun onAdLoaded(rewardedAd: RewardedAd) {
                                var rewardEarned = false
                                rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                                    override fun onAdDismissedFullScreenContent() {
                                        if (!rewardEarned) {
                                            viewModel.handleAdClosedWithoutReward()
                                        }
                                    }

                                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                                        viewModel.handleAdLoadFailed(adError.message)
                                    }
                                }
                                rewardedAd.show(activity) {
                                    rewardEarned = true
                                    viewModel.completeAdReward()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tickets_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
            )
        },
        containerColor = FlightBlack
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.tickets_current), color = FlightGray, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "${uiState.ticketBalance}",
                                color = Color.White,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.tickets_long_flight_notice),
                                color = FlightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ConfirmationNumber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.tickets_daily_check_in),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (uiState.hasCheckedInToday) {
                                stringResource(R.string.tickets_daily_check_in_done_description)
                            } else {
                                stringResource(R.string.tickets_daily_check_in_ready_description)
                            },
                            color = FlightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        PrimaryFlightButton(
                            text = if (uiState.hasCheckedInToday) {
                                stringResource(R.string.tickets_daily_check_in_done)
                            } else {
                                stringResource(R.string.tickets_daily_check_in_action)
                            },
                            enabled = !uiState.hasCheckedInToday,
                            onClick = { viewModel.claimDailyCheckIn() }
                        )
                    }
                }
            }

            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.tickets_ad_reward_title),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.tickets_ad_reward_description),
                            color = FlightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        PrimaryFlightButton(
                            text = if (uiState.isWatchingAd) {
                                stringResource(R.string.tickets_ad_reward_running)
                            } else {
                                stringResource(R.string.tickets_ad_reward_action)
                            },
                            enabled = !uiState.isWatchingAd,
                            onClick = { viewModel.startAdReward() }
                        )
                    }
                }
            }

            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.tickets_redeem_title),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = uiState.redeemCode,
                            onValueChange = { viewModel.updateRedeemCode(it) },
                            label = { Text(stringResource(R.string.tickets_redeem_code_input)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PrimaryFlightButton(
                            text = stringResource(R.string.tickets_redeem_action),
                            enabled = uiState.redeemCode.isNotBlank(),
                            onClick = { viewModel.redeemCode() }
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.tickets_history_title),
                    color = FlightGray,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (uiState.ticketHistory.isEmpty()) {
                item {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.tickets_history_empty), color = FlightGray)
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = uiState.ticketHistory,
                    key = { index, entry ->
                        "${entry.id}-${entry.timestamp}-${entry.amount}-${entry.balanceAfter}-$index"
                    }
                ) { _, entry ->
                    TicketHistoryItem(entry = entry)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (uiState.isWatchingAd) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.tickets_ad_dialog_title), color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                        ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(stringResource(R.string.tickets_ad_dialog_description), color = FlightGray)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelAdReward() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            containerColor = Color(0xFF0D0000)
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun TicketHistoryItem(entry: FlightTicketHistoryEntry) {
    val amountLabel = if (entry.amount > 0) "+${entry.amount}" else entry.amount.toString()
    val amountColor = if (entry.amount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val formatter = remember { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()) }

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(entry.detail, color = FlightGray, style = MaterialTheme.typography.bodySmall)
                }
                Text(amountLabel, color = amountColor, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatter.format(Date(entry.timestamp)),
                    color = FlightGray,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    stringResource(R.string.label_remaining_format, entry.balanceAfter),
                    color = FlightGray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

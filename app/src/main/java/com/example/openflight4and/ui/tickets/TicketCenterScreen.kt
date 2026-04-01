package com.example.openflight4and.ui.tickets

import android.app.Application
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openflight4and.model.FlightTicketHistoryEntry
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.theme.FlightBlack
import com.example.openflight4and.ui.theme.FlightGray
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
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("\uBE44\uD589\uAD8C \uC13C\uD130", color = Color.White) },
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
                            Text("\uD604\uC7AC \uBE44\uD589\uAD8C", color = FlightGray, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "${uiState.ticketBalance}",
                                color = Color.White,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "10\uBD84 \uC774\uC0C1 \uBE44\uD589\uD558\uBA74 \uBE44\uD589\uAD8C 1\uAC1C\uAC00 \uCC28\uAC10\uB429\uB2C8\uB2E4.",
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
                            "\uCD9C\uC11D \uCCB4\uD06C",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (uiState.hasCheckedInToday) {
                                "\uC624\uB298\uC740 \uCD9C\uC11D\uCCB4\uD06C\uB97C \uC644\uB8CC\uD588\uC2B5\uB2C8\uB2E4."
                            } else {
                                "\uD558\uB8E8\uC5D0 \uD55C \uBC88 \uCD9C\uC11D\uCCB4\uD06C\uB97C \uD558\uBA74 \uBE44\uD589\uAD8C 1\uAC1C\uB97C \uBC1B\uC744 \uC218 \uC788\uC2B5\uB2C8\uB2E4."
                            },
                            color = FlightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        PrimaryFlightButton(
                            text = if (uiState.hasCheckedInToday) "\uC624\uB298 \uCD9C\uC11D \uC644\uB8CC" else "\uCD9C\uC11D\uCCB4\uD06C \uD558\uAE30",
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
                            "\uAD11\uACE0 \uBCF4\uACE0 \uBE44\uD589\uAD8C \uBC1B\uAE30",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "30\uCD08 \uAD11\uACE0\uB97C \uBCF4\uBA74 \uBE44\uD589\uAD8C 1\uAC1C\uB97C \uBC1B\uC2B5\uB2C8\uB2E4.",
                            color = FlightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        PrimaryFlightButton(
                            text = if (uiState.isWatchingAd) "\uAD11\uACE0 \uC7AC\uC0DD \uC911..." else "\uAD11\uACE0 \uBCF4\uAE30",
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
                            "\uB9AC\uB51C \uCF54\uB4DC",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "\uD14C\uC2A4\uD2B8 \uCF54\uB4DC: admin / admin10 / admin100",
                            color = FlightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = uiState.redeemCode,
                            onValueChange = { viewModel.updateRedeemCode(it) },
                            label = { Text("\uCF54\uB4DC \uC785\uB825") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PrimaryFlightButton(
                            text = "\uB4F1\uB85D",
                            enabled = uiState.redeemCode.isNotBlank(),
                            onClick = { viewModel.redeemCode() }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "\uBE44\uD589\uAD8C \uC0AC\uC6A9 \uB0B4\uC5ED",
                    color = FlightGray,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (uiState.ticketHistory.isEmpty()) {
                item {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.padding(20.dp)) {
                            Text("\uC544\uC9C1 \uBE44\uD589\uAD8C \uB0B4\uC5ED\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.", color = FlightGray)
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
            title = { Text("\uAD11\uACE0 \uC7AC\uC0DD \uC911", color = Color.White) },
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
                        Text("\uD0C0\uC774\uBA38\uAC00 \uB05D\uB098\uBA74 \uBE44\uD589\uAD8C\uC774 \uC9C0\uAE09\uB429\uB2C8\uB2E4.", color = FlightGray)
                    }
                    Text("${uiState.adSecondsRemaining}\uCD08 \uB0A8\uC74C", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelAdReward() }) {
                    Text("\uCDE8\uC18C")
                }
            },
            containerColor = Color(0xFF0D0000)
        )
    }
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
                    "\uC794\uC5EC ${entry.balanceAfter}",
                    color = FlightGray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

package com.example.openflight4and.ui.tickets

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.data.DailyCheckInResult
import com.example.openflight4and.data.RedeemCodeResult
import com.example.openflight4and.model.FlightTicketHistoryEntry
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.theme.FlightBlack
import com.example.openflight4and.ui.theme.FlightGray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketCenterScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()
    val ticketBalance by repository.flightTickets.collectAsState(initial = 0)
    val hasCheckedInToday by repository.hasCheckedInToday.collectAsState(initial = false)
    val ticketHistory by repository.ticketHistory.collectAsState(initial = emptyList())

    var redeemCode by rememberSaveable { mutableStateOf("") }
    var isWatchingAd by rememberSaveable { mutableStateOf(false) }
    var adSecondsRemaining by rememberSaveable { mutableIntStateOf(30) }

    LaunchedEffect(isWatchingAd) {
        if (!isWatchingAd) return@LaunchedEffect

        adSecondsRemaining = 30
        while (adSecondsRemaining > 0) {
            delay(1000)
            adSecondsRemaining--
        }

        repository.rewardTicketsFromAd()
        isWatchingAd = false
        Toast.makeText(context, "광고 보상으로 비행권 1개가 지급되었습니다.", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("비행권 센터", color = Color.White) },
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
                            Text("현재 비행권", color = FlightGray, style = MaterialTheme.typography.labelLarge)
                            Text(
                                text = "$ticketBalance",
                                color = Color.White,
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "10분 이상 비행하면 비행권 1개가 차감됩니다.",
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
                            "출석 체크",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (hasCheckedInToday) {
                                "오늘은 출석체크를 완료했습니다."
                            } else {
                                "하루에 한 번 출석체크를 하면 비행권 1개를 받을 수 있습니다."
                            },
                            color = FlightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        PrimaryFlightButton(
                            text = if (hasCheckedInToday) "오늘 출석 완료" else "출석체크 하기",
                            enabled = !hasCheckedInToday,
                            onClick = {
                                scope.launch {
                                    when (val result = repository.claimDailyCheckIn()) {
                                        is DailyCheckInResult.Success -> {
                                            Toast.makeText(
                                                context,
                                                "출석체크 보상으로 비행권 ${result.amount}개가 지급되었습니다.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        is DailyCheckInResult.Error -> {
                                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
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
                            "광고 보고 비행권 받기",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "30초 광고를 보면 비행권 1개를 받습니다.",
                            color = FlightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        PrimaryFlightButton(
                            text = if (isWatchingAd) "광고 재생 중..." else "광고 보기",
                            enabled = !isWatchingAd,
                            onClick = { isWatchingAd = true }
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
                            "리딤 코드",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "테스트 코드: admin / admin10 / admin100",
                            color = FlightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = redeemCode,
                            onValueChange = { redeemCode = it },
                            label = { Text("코드 입력") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PrimaryFlightButton(
                            text = "등록",
                            enabled = redeemCode.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    when (val result = repository.redeemCode(redeemCode)) {
                                        is RedeemCodeResult.Success -> {
                                            Toast.makeText(
                                                context,
                                                "비행권 ${result.amount}개가 지급되었습니다.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            redeemCode = ""
                                        }
                                        is RedeemCodeResult.Error -> {
                                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "비행권 사용 내역",
                    color = FlightGray,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (ticketHistory.isEmpty()) {
                item {
                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.padding(20.dp)) {
                            Text("아직 비행권 내역이 없습니다.", color = FlightGray)
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = ticketHistory,
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

    if (isWatchingAd) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("광고 재생 중", color = Color.White) },
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
                        Text("타이머가 끝나면 비행권이 지급됩니다.", color = FlightGray)
                    }
                    Text("${adSecondsRemaining}초 남음", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = { isWatchingAd = false }) {
                    Text("취소")
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
                    "잔여 ${entry.balanceAfter}",
                    color = FlightGray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

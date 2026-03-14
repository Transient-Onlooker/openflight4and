package com.example.openflight4and.ui.trend

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.theme.FlightGray
import com.example.openflight4and.utils.FlightUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSandbox: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }

    val totalFlights by repository.totalFlights.collectAsState(initial = 0)
    val totalDistanceKm by repository.totalDistance.collectAsState(initial = 0)
    val totalFocusMinutes by repository.totalFocusMinutes.collectAsState(initial = 0)
    val unitSystem by repository.unitSystem.collectAsState(initial = "km")
    val sessions by repository.allSessions.collectAsState(initial = emptyList())

    val visitedAirportsCount = remember(sessions) {
        sessions.filter { it.isCompleted }
            .flatMap { listOf(it.originIata, it.destinationIata) }
            .distinct()
            .size
    }

    // 자유모드 언락 카운트 (누적 거리 5 번 클릭)
    var clickCount by remember { mutableStateOf(0) }
    var clickTimestamp by remember { mutableStateOf(0L) }

    FlightMapBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("비행 통계 리포트", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(text = "누적 집중 데이터", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(text = "완료된 여정을 바탕으로 집계된 통계입니다.", color = FlightGray, style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCard(
                        label = "총 비행",
                        value = "${totalFlights}회",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    StatCard(
                        label = "방문 공항",
                        value = "${visitedAirportsCount}곳",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    val distVal = if (totalDistanceKm != null) FlightUtils.convertDistance(totalDistanceKm!!, unitSystem) else 0
                    val currentTime = System.currentTimeMillis()
                    StatCard(
                        label = "누적 거리",
                        value = "$distVal $unitSystem",
                        modifier = Modifier.weight(1f).clickable {
                            val elapsed = currentTime - clickTimestamp
                            if (elapsed < 5000) {
                                // 5 초 이내 클릭
                                clickCount++
                                if (clickCount >= 5) {
                                    onNavigateToSandbox()
                                    clickCount = 0
                                }
                            } else {
                                // 5 초 초과면 리셋
                                clickCount = 1
                            }
                            clickTimestamp = currentTime
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    StatCard(
                        label = "집중 시간",
                        value = FlightUtils.formatDuration(totalFocusMinutes ?: 0),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    GlassPanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = FlightGray, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

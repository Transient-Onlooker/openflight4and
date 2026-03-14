package com.example.openflight4and.ui.sandbox

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
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.Airport
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.theme.FlightGray
import com.example.openflight4and.ui.theme.FlightPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAirportSelection: (Boolean) -> Unit, // isSettingCurrentLocation
    currentLocation: Airport?,
    timeScale: Float,
    onTimeScaleChanged: (Float) -> Unit,
    onSaveCompleted: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val allAirports = remember { repository.getAirports() }
    
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    FlightMapBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("자유 모드", color = Color.White, fontWeight = FontWeight.Bold) },
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

                Text(
                    text = "집중모드 설정",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "집중모드 시작 시 적용될 설정을 구성하세요.",
                    color = FlightGray,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 현재 위치 설정 섹션
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("내 현재 위치", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                val locationText = if (currentLocation != null) {
                                    "${currentLocation.iata} - ${currentLocation.nameKo}"
                                } else {
                                    "설정되지 않음"
                                }
                                Text(
                                    text = locationText,
                                    color = FlightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { onNavigateToAirportSelection(true) }) {
                                Text("변경", color = FlightPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "집중모드 시작 시 이 공항이 기본 출발지로 설정됩니다.",
                            color = FlightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 시간 배율 섹션
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("시간 배율", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${timeScale.toInt()}x", color = FlightPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                            Slider(
                                value = timeScale,
                                onValueChange = onTimeScaleChanged,
                                valueRange = 1f..100f,
                                steps = 98,
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                            )
                            Text("100x", color = FlightGray, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when {
                            timeScale < 10f -> Text("실시간에 가깝게 진행됩니다.", color = FlightGray, style = MaterialTheme.typography.bodySmall)
                            timeScale < 50f -> Text("빠르게 진행됩니다.", color = FlightGray, style = MaterialTheme.typography.bodySmall)
                            else -> Text("매우 빠르게 진행됩니다!", color = FlightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
                
                Text(
                    text = "설정을 완료한 후 홈에서 집중모드를 시작하세요.",
                    color = FlightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))
                
                // 설정 완료 버튼
                PrimaryFlightButton(
                    text = "설정 완료",
                    onClick = onSaveCompleted,
                    isDestructive = false
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

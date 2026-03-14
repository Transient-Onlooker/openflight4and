package com.example.openflight4and.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.openflight4and.BuildConfig
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.components.SectionHeader
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.theme.FlightBlack
import com.example.openflight4and.ui.theme.FlightGray
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()

    // DataStore States
    val unitSystem by repository.unitSystem.collectAsState(initial = "km")
    val mapStyle by repository.mapStyle.collectAsState(initial = "standard")
    val airplaneModeCheck by repository.airplaneModeCheck.collectAsState(initial = true)
    val notificationsEnabled by repository.notificationsEnabled.collectAsState(initial = true)
    val lockLevel by repository.lockLevel.collectAsState(initial = "soft")

    FlightMapBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("설정", color = Color.White) },
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
                // 1. 측정 단위
                SectionHeader("측정 단위")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = unitSystem == "km",
                        onClick = { scope.launch { repository.setUnitSystem("km") } },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Kilometers (km)") }
                    SegmentedButton(
                        selected = unitSystem == "mi",
                        onClick = { scope.launch { repository.setUnitSystem("mi") } },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Miles (mi)") }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 2. 지도 스타일
                SectionHeader("홈 지도 스타일")
                val styles = listOf("standard" to "일반", "satellite" to "위성", "hybrid" to "하이브리드")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    styles.forEachIndexed { index, (id, label) ->
                        SegmentedButton(
                            selected = mapStyle == id,
                            onClick = { scope.launch { repository.setMapStyle(id) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = styles.size)
                        ) { Text(label) }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. 비행기 모드 확인
                ToggleSettingItem(
                    title = "비행기 모드 확인",
                    description = "세션 시작 전 비행기 모드 활성 여부를 체크합니다.",
                    checked = airplaneModeCheck,
                    onCheckedChange = { scope.launch { repository.setAirplaneModeCheck(it) } }
                )

                // 4. 알림 설정
                ToggleSettingItem(
                    title = "알림",
                    description = "세션 완료 및 중요 알림을 받습니다.",
                    checked = notificationsEnabled,
                    onCheckedChange = { scope.launch { repository.setNotificationsEnabled(it) } }
                )

                // 배터리 최적화 제외 설정
                BatteryOptimizationItem()

                Spacer(modifier = Modifier.height(24.dp))

                // 5. 집중 잠금 강도
                SectionHeader("집중 잠금 강도")
                val levels = listOf("soft" to "Soft", "strong" to "Strong", "hardcore" to "Hardcore")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    levels.forEachIndexed { index, (id, label) ->
                        SegmentedButton(
                            selected = lockLevel == id,
                            onClick = { scope.launch { repository.setLockLevel(id) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size)
                        ) { Text(label) }
                    }
                }
                Text(
                    text = when(lockLevel) {
                        "soft" -> "이탈 시 경고만 표시합니다."
                        "strong" -> "다른 앱 사용을 제한하고 복귀를 유도합니다."
                        else -> "비행기 모드 및 모든 방해 요소를 강력히 차단합니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightGray,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // 6. 앱 정보
                Text(
                    text = "버전 1.2",
                    style = MaterialTheme.typography.labelSmall,
                    color = FlightGray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ToggleSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = description, color = FlightGray, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = FlightGray,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
fun BatteryOptimizationItem() {
    val context = LocalContext.current
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable {
                val intent = if (isIgnoring) {
                    // 이미 제외된 경우, 설정 목록을 보여줌
                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                } else {
                    // 제외되지 않은 경우, 직접 추가 요청
                    android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback: 일반 설정 화면으로
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "배터리 최적화 제외",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isIgnoring) {
                    Text(
                        text = "ON",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "백그라운드에서 비행이 중단되지 않도록 설정합니다.",
                color = FlightGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = FlightGray
        )
    }
}

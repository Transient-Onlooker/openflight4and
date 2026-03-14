package com.example.openflight4and.ui

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.Airport
import com.example.openflight4and.model.FlightDraft
import com.example.openflight4and.service.FlightService
import com.example.openflight4and.service.FlightStatusManager
import com.example.openflight4and.ui.boardingpass.BoardingPassScreen
import com.example.openflight4and.ui.history.HistoryScreen
import com.example.openflight4and.ui.home.HomeScreen
import com.example.openflight4and.ui.inflight.InFlightScreen
import com.example.openflight4and.ui.navigation.Screen
import com.example.openflight4and.ui.newflight.NewFlightScreen
import com.example.openflight4and.ui.seatselection.SeatSelectionScreen
import com.example.openflight4and.ui.settings.SettingsScreen
import com.example.openflight4and.ui.sandbox.SandboxScreen
import com.example.openflight4and.ui.trend.TrendScreen
import com.example.openflight4and.utils.FlightUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()

    val unitSystem by repository.unitSystem.collectAsState(initial = "km")
    val airplaneModeCheckEnabled by repository.airplaneModeCheck.collectAsState(initial = true)
    val currentLocation by repository.currentLocation.collectAsState(initial = null)
    val sandboxTimeScale by repository.sandboxTimeScale.collectAsState(initial = 1f)

    val allAirports = remember { repository.getAirports() }
    val recentSessions by repository.recentSessions.collectAsState(initial = emptyList())

    // 현재 위치가 변경되면 자동으로 업데이트
    val defaultOrigin = remember(currentLocation, recentSessions, allAirports) {
        if (allAirports.isEmpty()) return@remember com.example.openflight4and.model.Airport("ICN", "인천국제공항", "Incheon Int'l", "인천/서울", "Incheon/Seoul", "KR", 37.46, 126.44)

        // 1. 현재 위치가 설정되어 있으면 우선 사용
        currentLocation?.let { return@remember it }

        // 2. 최근 세션의 도착지
        val lastDestIata = recentSessions.firstOrNull()?.destinationIata
        allAirports.find { it.iata == lastDestIata }
            ?: allAirports.find { it.iata == "ICN" }
            ?: allAirports.first()
    }

    var currentDraft by remember {
        mutableStateOf(FlightDraft(origin = defaultOrigin))
    }

    // 현재 위치가 변경되면 currentDraft 도 업데이트
    LaunchedEffect(currentLocation) {
        currentLocation?.let { airport ->
            Log.d("MainScreen", "Current location changed to: ${airport.iata}")
            currentDraft = currentDraft.copy(origin = airport)
        }
    }

    var showAirplaneModeDialog by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToNewFlight = {
                        // 현재 위치가 설정되어 있으면 그 공항으로, 없으면 defaultOrigin
                        val origin = currentLocation ?: defaultOrigin
                        currentDraft = FlightDraft(origin = origin)
                        navController.navigate(Screen.NewFlight.route)
                    },
                    onNavigateToHistory = { navController.navigate(Screen.History.route) },
                    onNavigateToTrend = { navController.navigate(Screen.Trend.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(
                route = "${Screen.NewFlight.route}?sandboxMode={sandboxMode}&isSettingCurrentLocation={isSettingCurrentLocation}",
                arguments = listOf(
                    navArgument("sandboxMode") {
                        type = NavType.StringType
                        defaultValue = "false"
                    },
                    navArgument("isSettingCurrentLocation") {
                        type = NavType.StringType
                        defaultValue = "false"
                    }
                )
            ) { backStackEntry ->
                // 샌드박스 모드인지 확인
                val isSandboxMode = backStackEntry.arguments?.getString("sandboxMode") == "true"
                val isSettingCurrentLocation = backStackEntry.arguments?.getString("isSettingCurrentLocation") == "true"
                
                NewFlightScreen(
                    currentDraft = currentDraft,
                    onAirportSelected = { destination ->
                        val distance = FlightUtils.calculateDistance(currentDraft.origin, destination)
                        val duration = FlightUtils.estimateDurationMinutes(distance)
                        currentDraft = currentDraft.copy(
                            destination = destination,
                            distanceKm = distance.toInt(),
                            estimatedMinutes = duration
                        )
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToBoardingPass = {
                        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                        currentDraft = currentDraft.copy(boardingTime = now)
                        navController.navigate(Screen.BoardingPass.route)
                    },
                    unitSystem = unitSystem,
                    isSandboxMode = isSandboxMode,
                    isSettingCurrentLocation = isSettingCurrentLocation,
                    onSandboxAirportSelected = { origin, destination ->
                        // 샌드박스로 결과 전달 - 현재 백스택의 Sandbox 엔트리를 찾음
                        val sandboxEntry = navController.getBackStackEntry(Screen.Sandbox.route)
                        sandboxEntry.savedStateHandle["selected_origin"] = origin.iata
                        sandboxEntry.savedStateHandle["selected_destination"] = destination?.iata
                        navController.popBackStack()
                    },
                    onCurrentLocationSet = { airport ->
                        // 현재 위치 설정 - Sandbox 로 결과 전달
                        val sandboxEntry = navController.getBackStackEntry(Screen.Sandbox.route)
                        scope.launch {
                            repository.setCurrentLocation(airport)
                        }
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.BoardingPass.route) {
                BoardingPassScreen(
                    draft = currentDraft,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSeatSelection = { navController.navigate(Screen.SeatSelection.route) },
                    unitSystem = unitSystem
                )
            }

            composable(Screen.SeatSelection.route) {
                SeatSelectionScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSeatSelected = { seat, category ->
                        currentDraft = currentDraft.copy(seatNumber = seat, focusCategory = category)
                    },
                    onFinish = {
                        // Check for airplane mode if enabled
                        if (airplaneModeCheckEnabled && !FlightUtils.isAirplaneModeOn(context)) {
                            showAirplaneModeDialog = true
                        } else {
                            // Start the FlightService
                            val intent = Intent(context, FlightService::class.java).apply {
                                val draft = currentDraft
                                putExtra("origin_iata", draft.origin.iata)
                                putExtra("destination_iata", draft.destination?.iata ?: "N/A")
                                putExtra("origin_name", draft.origin.nameKo)
                                putExtra("destination_name", draft.destination?.nameKo ?: "N/A")
                                putExtra("duration_minutes", draft.estimatedMinutes)
                                // 샌드박스에서 설정한 시간 배율 적용
                                putExtra("time_scale", sandboxTimeScale)
                            }
                            context.startForegroundService(intent)

                            // Navigate to the In-Flight screen
                            navController.navigate(Screen.InFlight.route) {
                                popUpTo(Screen.Home.route) { inclusive = false }
                            }
                        }
                    }
                )
            }
            
            composable(Screen.InFlight.route) {
                InFlightScreen(
                    draft = currentDraft,
                    onFlightEnd = {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Trend.route) {
                TrendScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSandbox = { navController.navigate(Screen.Sandbox.route) }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Sandbox.route) { sandboxEntry ->
                // 현재 위치와 시간 배율
                val currentLocation by repository.currentLocation.collectAsState(initial = null)
                val sandboxTimeScale by repository.sandboxTimeScale.collectAsState(initial = 1f)
                
                SandboxScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAirportSelection = { isSettingCurrentLocation ->
                        // NewFlightScreen 으로 이동 (현재 위치 설정 모드)
                        navController.navigate(
                            "${Screen.NewFlight.route}?sandboxMode=true&isSettingCurrentLocation=$isSettingCurrentLocation"
                        )
                    },
                    currentLocation = currentLocation,
                    timeScale = sandboxTimeScale,
                    onTimeScaleChanged = { newScale ->
                        scope.launch {
                            repository.setSandboxTimeScale(newScale)
                        }
                    },
                    onSaveCompleted = {
                        // 설정 완료 - 홈으로 이동
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    if (showAirplaneModeDialog) {
        AlertDialog(
            onDismissRequest = { showAirplaneModeDialog = false },
            title = { Text("비행기 모드 확인", color = Color.White) },
            text = { Text("집중을 위해 비행기 모드를 켜는 것을 권장합니다. 시스템 설정에서 비행기 모드를 활성화해주세요.", color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = { 
                    showAirplaneModeDialog = false
                    navController.navigate(Screen.InFlight.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }) { Text("그대로 시작") }
            },
            dismissButton = {
                TextButton(onClick = { showAirplaneModeDialog = false }) { Text("취소") }
            },
            containerColor = Color(0xFF0D0000)
        )
    }
}

package com.example.openflight4and.ui

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.Airport
import com.example.openflight4and.model.FlightDraft
import com.example.openflight4and.service.FlightService
import com.example.openflight4and.ui.boardingpass.BoardingPassScreen
import com.example.openflight4and.ui.history.HistoryScreen
import com.example.openflight4and.ui.home.HomeScreen
import com.example.openflight4and.ui.inflight.InFlightScreen
import com.example.openflight4and.ui.navigation.Screen
import com.example.openflight4and.ui.newflight.NewFlightScreen
import com.example.openflight4and.ui.sandbox.SandboxScreen
import com.example.openflight4and.ui.seatselection.SeatSelectionScreen
import com.example.openflight4and.ui.settings.SettingsScreen
import com.example.openflight4and.ui.tickets.TicketCenterScreen
import com.example.openflight4and.ui.trend.TrendScreen
import com.example.openflight4and.utils.FlightUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

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
    val ticketBalance by repository.flightTickets.collectAsState(initial = 0)
    val recentSessions by repository.recentSessions.collectAsState(initial = emptyList())

    val allAirports = remember { repository.getAirports() }
    val defaultOrigin = remember(currentLocation, recentSessions, allAirports) {
        if (allAirports.isEmpty()) {
            return@remember Airport(
                iata = "ICN",
                nameKo = "Incheon Intl",
                nameEn = "Incheon Intl",
                cityKo = "Incheon Seoul",
                cityEn = "Incheon Seoul",
                country = "KR",
                latitude = 37.46,
                longitude = 126.44
            )
        }

        currentLocation?.let { return@remember it }

        val lastDestIata = recentSessions.firstOrNull()?.destinationIata
        allAirports.find { it.iata == lastDestIata }
            ?: allAirports.find { it.iata == "ICN" }
            ?: allAirports.first()
    }

    var currentDraft by remember { mutableStateOf(FlightDraft(origin = defaultOrigin)) }
    var showAirplaneModeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentLocation) {
        currentLocation?.let { airport ->
            Log.d("MainScreen", "Current location changed to: ${airport.iata}")
            currentDraft = currentDraft.copy(origin = airport)
        }
    }

    LaunchedEffect(defaultOrigin.iata) {
        if (currentLocation == null && currentDraft.origin.iata != defaultOrigin.iata) {
            currentDraft = currentDraft.copy(origin = defaultOrigin)
        }
    }

    LaunchedEffect(Unit) {
        val granted = repository.grantDailyTicketIfNeeded()
        if (granted > 0) {
            Toast.makeText(context, "일일 비행권이 지급되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    fun startFlight() {
        if (ticketBalance <= 0) {
            Toast.makeText(context, "비행권이 부족합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            val spendResult = repository.canStartFlight(currentDraft.estimatedMinutes)
            if (!spendResult.success) {
                Toast.makeText(context, "티켓이 부족합니다.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val draftToStart = currentDraft.copy(timeScale = sandboxTimeScale)
            currentDraft = draftToStart

            val intent = Intent(context, FlightService::class.java).apply {
                putExtra("origin_iata", draftToStart.origin.iata)
                putExtra("destination_iata", draftToStart.destination?.iata ?: "N/A")
                putExtra("origin_name", draftToStart.origin.nameKo)
                putExtra("destination_name", draftToStart.destination?.nameKo ?: "N/A")
                putExtra("duration_minutes", draftToStart.estimatedMinutes)
                putExtra("time_scale", sandboxTimeScale)
            }
            context.startForegroundService(intent)

            navController.navigate(Screen.InFlight.route) {
                popUpTo(Screen.Home.route) { inclusive = false }
            }
        }
    }

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToNewFlight = {
                        val origin = currentLocation ?: defaultOrigin
                        currentDraft = FlightDraft(origin = origin)
                        navController.navigate(Screen.NewFlight.route)
                    },
                    onNavigateToHistory = { navController.navigate(Screen.History.route) },
                    onNavigateToTrend = { navController.navigate(Screen.Trend.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    currentAirport = currentDraft.origin,
                    ticketBalance = ticketBalance,
                    onNavigateToTickets = { navController.navigate(Screen.Tickets.route) }
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
                        val sandboxEntry = navController.getBackStackEntry(Screen.Sandbox.route)
                        sandboxEntry.savedStateHandle["selected_origin"] = origin.iata
                        sandboxEntry.savedStateHandle["selected_destination"] = destination?.iata
                        navController.popBackStack()
                    },
                    onCurrentLocationSet = { airport ->
                        scope.launch { repository.setCurrentLocation(airport) }
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
                        if (ticketBalance <= 0) {
                            Toast.makeText(context, "비행권이 부족합니다.", Toast.LENGTH_SHORT).show()
                            return@SeatSelectionScreen
                        }

                        if (airplaneModeCheckEnabled && !FlightUtils.isAirplaneModeOn(context)) {
                            showAirplaneModeDialog = true
                        } else {
                            startFlight()
                        }
                    }
                )
            }

            composable(Screen.InFlight.route) {
                InFlightScreen(
                    draft = currentDraft,
                    onFlightEnd = { navController.popBackStack(Screen.Home.route, inclusive = false) }
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

            composable(Screen.Tickets.route) {
                TicketCenterScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.Sandbox.route) {
                val sandboxLocation by repository.currentLocation.collectAsState(initial = null)
                val currentTimeScale by repository.sandboxTimeScale.collectAsState(initial = 1f)

                SandboxScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAirportSelection = { isSettingCurrentLocation ->
                        navController.navigate(
                            "${Screen.NewFlight.route}?sandboxMode=true&isSettingCurrentLocation=$isSettingCurrentLocation"
                        )
                    },
                    currentLocation = sandboxLocation,
                    timeScale = currentTimeScale,
                    onTimeScaleChanged = { newScale ->
                        scope.launch { repository.setSandboxTimeScale(newScale) }
                    },
                    onSaveCompleted = { navController.popBackStack() }
                )
            }
        }
    }

    if (showAirplaneModeDialog) {
        AlertDialog(
            onDismissRequest = { showAirplaneModeDialog = false },
            title = { Text("비행기 모드 확인", color = Color.White) },
            text = {
                Text(
                    "집중 비행에는 비행기 모드 사용을 권장합니다. 그래도 계속할 수 있습니다.",
                    color = Color.Gray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAirplaneModeDialog = false
                        startFlight()
                    }
                ) { Text("그대로 시작") }
            },
            dismissButton = {
                TextButton(onClick = { showAirplaneModeDialog = false }) { Text("취소") }
            },
            containerColor = Color(0xFF0D0000)
        )
    }

}

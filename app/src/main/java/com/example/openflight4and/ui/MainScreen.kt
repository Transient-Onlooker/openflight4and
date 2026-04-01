package com.example.openflight4and.ui

import android.app.Application
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.openflight4and.InFlightLaunchRequest
import com.example.openflight4and.data.AppRepository
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    inflightLaunchRequest: InFlightLaunchRequest? = null,
    onInflightLaunchHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()
    val viewModel: MainScreenViewModel = viewModel(
        factory = MainScreenViewModel.Factory(context.applicationContext as Application)
    )

    val unitSystem by repository.unitSystem.collectAsState(initial = "km")
    val airplaneModeCheckEnabled by repository.airplaneModeCheck.collectAsState(initial = true)
    val sandboxTimeScale by repository.sandboxTimeScale.collectAsState(initial = 1f)
    val ticketBalance by repository.flightTickets.collectAsState(initial = 0)
    val notificationUpdateSeconds by repository.notificationUpdateSeconds.collectAsState(initial = 10)
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is MainScreenEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }

                MainScreenEvent.NavigateToInFlight -> {
                    navController.navigate(Screen.InFlight.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            }
        }
    }

    LaunchedEffect(inflightLaunchRequest) {
        val handled = viewModel.handleInflightLaunchRequest(inflightLaunchRequest)
        if (handled) {
            navController.navigate(Screen.InFlight.route) {
                launchSingleTop = true
                popUpTo(Screen.Home.route) { inclusive = false }
            }
            onInflightLaunchHandled()
        } else if (inflightLaunchRequest != null) {
            onInflightLaunchHandled()
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
                        viewModel.prepareNewFlight()
                        navController.navigate(Screen.NewFlight.route)
                    },
                    onNavigateToHistory = { navController.navigate(Screen.History.route) },
                    onNavigateToTrend = { navController.navigate(Screen.Trend.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    currentAirport = uiState.currentDraft.origin,
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
                    currentDraft = uiState.currentDraft,
                    onAirportSelected = { destination ->
                        viewModel.updateDestination(destination)
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToBoardingPass = {
                        viewModel.prepareBoardingPass()
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
                    draft = uiState.currentDraft,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSeatSelection = { navController.navigate(Screen.SeatSelection.route) },
                    unitSystem = unitSystem
                )
            }

            composable(Screen.SeatSelection.route) {
                SeatSelectionScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSeatSelected = { seat, category ->
                        viewModel.updateSeatAndCategory(seat, category)
                    },
                    hasTickets = ticketBalance > 0,
                    onTicketRequired = {
                        Toast.makeText(context, "\uBE44\uD589\uAD8C\uC774 \uBD80\uC871\uD569\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
                    },
                    onFinish = {
                        if (!viewModel.validateSeatSelection(ticketBalance)) {
                            return@SeatSelectionScreen
                        }

                        viewModel.requestStartFlight(
                            ticketBalance = ticketBalance,
                            airplaneModeCheckEnabled = airplaneModeCheckEnabled,
                            sandboxTimeScale = sandboxTimeScale,
                            notificationUpdateSeconds = notificationUpdateSeconds
                        )
                    }
                )
            }

            composable(Screen.InFlight.route) {
                InFlightScreen(
                    draft = uiState.currentDraft,
                    onNavigateToSettings = { navController.navigate(Screen.InFlightSettings.route) },
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

            composable(Screen.InFlightSettings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    restrictInFlightSettings = true
                )
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

    if (uiState.showAirplaneModeDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAirplaneModeDialog() },
            title = { Text("\uBE44\uD589\uAE30 \uBAA8\uB4DC \uD655\uC778", color = Color.White) },
            text = {
                Text(
                    "\uC9D1\uC911 \uBE44\uD589\uC5D0\uC11C\uB294 \uBE44\uD589\uAE30 \uBAA8\uB4DC \uC0AC\uC6A9\uC744 \uAD8C\uC7A5\uD569\uB2C8\uB2E4. \uADF8\uB798\uB3C4 \uACC4\uC18D\uD558\uC2DC\uACA0\uC2B5\uB2C8\uAE4C?",
                    color = Color.Gray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmAirplaneModeStart(
                            sandboxTimeScale = sandboxTimeScale,
                            notificationUpdateSeconds = notificationUpdateSeconds
                        )
                    }
                ) { Text("\uADF8\uB300\uB85C \uC2DC\uC791") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAirplaneModeDialog() }) { Text("\uCDE8\uC18C") }
            },
            containerColor = Color(0xFF0D0000)
        )
    }
}

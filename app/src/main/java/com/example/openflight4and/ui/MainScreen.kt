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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.openflight4and.InFlightLaunchRequest
import com.example.openflight4and.R
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
    val repository = LocalAppRepository.current
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

                MainScreenEvent.NavigateToBoardingPass -> {
                    navController.navigate(Screen.BoardingPass.route)
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
                        navController.navigate(Screen.SeatSelection.route)
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
                    onNavigateToSeatSelection = {
                        viewModel.startFlightAfterBoardingPass(
                            sandboxTimeScale = sandboxTimeScale,
                            notificationUpdateSeconds = notificationUpdateSeconds
                        )
                    },
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
                        Toast.makeText(context, context.getString(R.string.message_ticket_insufficient), Toast.LENGTH_SHORT).show()
                    },
                    onFinish = {
                        if (!viewModel.validateSeatSelection(ticketBalance)) {
                            return@SeatSelectionScreen
                        }

                        viewModel.requestBoardingPass(
                            ticketBalance = ticketBalance,
                            airplaneModeCheckEnabled = airplaneModeCheckEnabled
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
            title = { Text(stringResource(R.string.main_airplane_mode_dialog_title), color = Color.White) },
            text = {
                Text(
                    stringResource(R.string.main_airplane_mode_dialog_message),
                    color = Color.Gray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmAirplaneModeStart()
                    }
                ) { Text(stringResource(R.string.action_start_anyway)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAirplaneModeDialog() }) { Text(stringResource(R.string.action_cancel)) }
            },
            containerColor = Color(0xFF0D0000)
        )
    }
}

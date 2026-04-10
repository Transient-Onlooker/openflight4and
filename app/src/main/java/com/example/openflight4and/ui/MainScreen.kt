package com.example.openflight4and.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.openflight4and.BuildConfig
import com.example.openflight4and.InFlightLaunchRequest
import com.example.openflight4and.R
import com.example.openflight4and.data.VersionStatus
import com.example.openflight4and.model.Airport
import com.example.openflight4and.ui.inflight.InFlightScreen
import com.example.openflight4and.ui.navigation.Screen
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    inflightLaunchRequest: InFlightLaunchRequest? = null,
    onInflightLaunchHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val repository = com.example.openflight4and.ui.LocalAppRepository.current
    val scope = rememberCoroutineScope()
    val viewModel: MainScreenViewModel = viewModel(
        factory = MainScreenViewModel.Factory(context.applicationContext as Application)
    )

    val unitSystem by repository.unitSystem.collectAsState(initial = "km")
    val sandboxTimeScale by repository.sandboxTimeScale.collectAsState(initial = 1f)
    val ticketBalance by repository.flightTickets.collectAsState(initial = 0)
    val notificationUpdateSeconds by repository.notificationUpdateSeconds.collectAsState(initial = 10)
    val uiState by viewModel.uiState.collectAsState()
    val openReleasePage = {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.GITHUB_RELEASES_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

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

    if (uiState.requiredUpdate != null) {
        BackHandler(enabled = true) {}
    }

    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                com.example.openflight4and.ui.home.HomeScreen(
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

                com.example.openflight4and.ui.newflight.NewFlightScreen(
                    currentDraft = uiState.currentDraft,
                    onAirportSelected = { destination: Airport ->
                        viewModel.updateDestination(destination)
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToBoardingPass = {
                        navController.navigate(Screen.SeatSelection.route)
                    },
                    unitSystem = unitSystem,
                    isSandboxMode = isSandboxMode,
                    isSettingCurrentLocation = isSettingCurrentLocation,
                    onSandboxAirportSelected = { origin: Airport, destination: Airport? ->
                        val sandboxEntry = navController.getBackStackEntry(Screen.Sandbox.route)
                        sandboxEntry.savedStateHandle["selected_origin"] = origin.iata
                        sandboxEntry.savedStateHandle["selected_destination"] = destination?.iata
                        navController.popBackStack()
                    },
                    onCurrentLocationSet = { airport: Airport ->
                        scope.launch { repository.setCurrentLocation(airport) }
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.BoardingPass.route) {
                com.example.openflight4and.ui.boardingpass.BoardingPassScreen(
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
                com.example.openflight4and.ui.seatselection.SeatSelectionScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSeatSelected = { seat: String, category: String? ->
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

                        viewModel.requestBoardingPass(ticketBalance = ticketBalance)
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
                com.example.openflight4and.ui.history.HistoryScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.Trend.route) {
                com.example.openflight4and.ui.trend.TrendScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSandbox = { navController.navigate(Screen.Sandbox.route) }
                )
            }

            composable(Screen.Settings.route) {
                com.example.openflight4and.ui.settings.SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.InFlightSettings.route) {
                com.example.openflight4and.ui.settings.SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    restrictInFlightSettings = true
                )
            }

            composable(Screen.Tickets.route) {
                com.example.openflight4and.ui.tickets.TicketCenterScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.Sandbox.route) {
                val sandboxLocation by repository.currentLocation.collectAsState(initial = null)
                val currentTimeScale by repository.sandboxTimeScale.collectAsState(initial = 1f)

                com.example.openflight4and.ui.sandbox.SandboxScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAirportSelection = { isSettingCurrentLocation: Boolean ->
                        navController.navigate(
                            "${Screen.NewFlight.route}?sandboxMode=true&isSettingCurrentLocation=$isSettingCurrentLocation"
                        )
                    },
                    currentLocation = sandboxLocation,
                    timeScale = currentTimeScale,
                    onTimeScaleChanged = { newScale: Float ->
                        scope.launch { repository.setSandboxTimeScale(newScale) }
                    },
                    onSaveCompleted = { navController.popBackStack() }
                )
            }
        }
    }

    uiState.recommendedUpdate?.let { versionStatus ->
        RecommendedUpdateDialog(
            versionStatus = versionStatus,
            onUpdate = openReleasePage,
            onDismiss = { viewModel.dismissRecommendedUpdate() }
        )
    }

    uiState.requiredUpdate?.let { versionStatus ->
        RequiredUpdateScreen(
            versionStatus = versionStatus,
            onUpdate = openReleasePage
        )
    }
}

@Composable
private fun RecommendedUpdateDialog(
    versionStatus: VersionStatus,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.version_update_recommended_title), color = Color.White) },
        text = {
            Text(
                stringResource(
                    R.string.version_update_recommended_message,
                    versionStatus.currentVersion,
                    versionStatus.recentVersion
                ),
                color = Color.Gray
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(stringResource(R.string.version_update_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_later))
            }
        },
        containerColor = Color(0xFF0D0000)
    )
}

@Composable
private fun RequiredUpdateScreen(
    versionStatus: VersionStatus,
    onUpdate: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505))
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.version_update_required_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    R.string.version_update_required_message,
                    versionStatus.currentVersion,
                    versionStatus.allowedVersion,
                    versionStatus.recentVersion
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFD0D0D0),
                textAlign = TextAlign.Center
            )
            Button(onClick = onUpdate) {
                Text(stringResource(R.string.version_update_action))
            }
        }
    }
}

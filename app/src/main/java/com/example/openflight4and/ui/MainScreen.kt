package com.example.openflight4and.ui

import android.app.Application
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.openflight4and.BuildConfig
import com.example.openflight4and.InFlightLaunchRequest
import com.example.openflight4and.R
import com.example.openflight4and.data.VersionStatus
import com.example.openflight4and.model.Airport
import com.example.openflight4and.service.FlightService
import com.example.openflight4and.ui.inflight.InFlightScreen
import com.example.openflight4and.ui.navigation.Screen
import com.example.openflight4and.utils.FlightUtils
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    inflightLaunchRequest: InFlightLaunchRequest? = null,
    onInflightLaunchHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
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
    val currentLocation by repository.currentLocation.collectAsState(initial = null)
    val initialOriginSetupCompleted by repository.initialOriginSetupCompleted.collectAsState(initial = false)
    val serviceRuntimeState by FlightService.runtimeState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var isResolvingInitialLocation by remember { mutableStateOf(false) }
    var hasHandledServiceResumeNavigation by remember { mutableStateOf(false) }
    val showInitialOriginSetupDialog =
        navBackStackEntry?.destination?.route == Screen.Home.route &&
            !initialOriginSetupCompleted &&
            currentLocation == null
    val openReleasePage = {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.GITHUB_RELEASES_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
    val requestInitialLocationPermissions = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!granted) {
            isResolvingInitialLocation = false
            Toast.makeText(
                context,
                context.getString(R.string.initial_origin_setup_location_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
            return@rememberLauncherForActivityResult
        }

        val nearestAirport = resolveNearestAirportFromDeviceLocation(
            context = context,
            airports = uiState.allAirports
        )
        if (nearestAirport == null) {
            isResolvingInitialLocation = false
            Toast.makeText(
                context,
                context.getString(R.string.initial_origin_setup_location_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            repository.setCurrentLocation(nearestAirport)
            isResolvingInitialLocation = false
            Toast.makeText(
                context,
                context.getString(
                    R.string.initial_origin_setup_location_success,
                    nearestAirport.localizedCity()
                ),
                Toast.LENGTH_SHORT
            ).show()
        }
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

    LaunchedEffect(
        serviceRuntimeState.isRunning,
        serviceRuntimeState.originIata,
        serviceRuntimeState.destinationIata,
        serviceRuntimeState.totalSeconds,
        navBackStackEntry?.destination?.route
    ) {
        val currentRoute = navBackStackEntry?.destination?.route
        if (
            !serviceRuntimeState.isRunning ||
            currentRoute == Screen.InFlight.route ||
            hasHandledServiceResumeNavigation
        ) {
            if (!serviceRuntimeState.isRunning) {
                hasHandledServiceResumeNavigation = false
            }
            return@LaunchedEffect
        }

        val handled = viewModel.handleInflightLaunchRequest(
            InFlightLaunchRequest(
                originIata = serviceRuntimeState.originIata.takeUnless { it == "N/A" },
                destinationIata = serviceRuntimeState.destinationIata.takeUnless { it == "N/A" },
                durationMinutes = (serviceRuntimeState.totalSeconds / 60L).toInt()
            )
        )
        if (handled) {
            hasHandledServiceResumeNavigation = true
            navController.navigate(Screen.InFlight.route) {
                launchSingleTop = true
                popUpTo(Screen.Home.route) { inclusive = false }
            }
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

    if (showInitialOriginSetupDialog) {
        InitialOriginSetupDialog(
            isResolvingLocation = isResolvingInitialLocation,
            onUseCurrentLocation = {
                val hasFineLocation = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarseLocation = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasFineLocation || hasCoarseLocation) {
                    isResolvingInitialLocation = true
                    val nearestAirport = resolveNearestAirportFromDeviceLocation(
                        context = context,
                        airports = uiState.allAirports
                    )
                    if (nearestAirport == null) {
                        isResolvingInitialLocation = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.initial_origin_setup_location_unavailable),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        scope.launch {
                            repository.setCurrentLocation(nearestAirport)
                            isResolvingInitialLocation = false
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.initial_origin_setup_location_success,
                                    nearestAirport.localizedCity()
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    isResolvingInitialLocation = true
                    requestInitialLocationPermissions.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            onChooseAirportManually = {
                navController.navigate(
                    "${Screen.NewFlight.route}?sandboxMode=false&isSettingCurrentLocation=true"
                )
            }
        )
    }
}

private fun resolveNearestAirportFromDeviceLocation(
    context: Context,
    airports: List<Airport>
): Airport? {
    if (airports.isEmpty()) return null
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val providers = runCatching { locationManager.getProviders(true) }.getOrDefault(emptyList())
    val bestLocation = providers
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        .maxByOrNull { location ->
            (location.time.takeIf { it > 0L } ?: 0L) + (1_000_000L - location.accuracy.toLong().coerceAtLeast(0L))
        }
        ?: return null

    return airports.minByOrNull { airport ->
        FlightUtils.calculateDistance(
            bestLocation.latitude,
            bestLocation.longitude,
            airport.latitude,
            airport.longitude
        )
    }
}

@Composable
private fun InitialOriginSetupDialog(
    isResolvingLocation: Boolean,
    onUseCurrentLocation: () -> Unit,
    onChooseAirportManually: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.initial_origin_setup_title), color = Color.White) },
        text = {
            Text(
                stringResource(R.string.initial_origin_setup_message),
                color = Color.Gray
            )
        },
        confirmButton = {
            TextButton(
                onClick = onUseCurrentLocation,
                enabled = !isResolvingLocation
            ) {
                Text(stringResource(R.string.initial_origin_setup_use_current_location))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onChooseAirportManually,
                enabled = !isResolvingLocation
            ) {
                Text(stringResource(R.string.initial_origin_setup_choose_airport))
            }
        },
        containerColor = Color(0xFF0D0000)
    )
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

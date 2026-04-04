package com.example.openflight4and.ui.newflight

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.openflight4and.R
import com.example.openflight4and.model.Airport
import com.example.openflight4and.model.FlightDraft
import com.example.openflight4and.ui.components.RulerPicker
import com.example.openflight4and.ui.theme.*
import com.example.openflight4and.utils.FlightUtils
import com.example.openflight4and.utils.MapBitmapUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class QuickFlightSuggestion(
    val targetMinutes: Int,
    val airport: Airport,
    val durationMinutes: Int,
    val distanceKm: Double
)

private const val NewFlightInitialMapZoom = 5f
private const val NewFlightUnlimitedRadiusKm = 20000
private const val NewFlightSelectionSnapDistanceKm = 300.0
private const val NewFlightSmallRadiusThresholdKm = 300
private const val NewFlightRingFilterMarginKm = 300.0
private const val NewFlightZoomRadius10000 = 10000
private const val NewFlightZoomRadius5000 = 5000
private const val NewFlightZoomRadius2000 = 2000
private const val NewFlightZoomRadius1000 = 1000
private const val NewFlightZoomRadius500 = 500
private const val NewFlightZoomRadius300 = 300
private const val NewFlightRadiusStepKm = 100
private val NewFlightQuickSuggestionMinutes = listOf(60, 120, 180)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewFlightScreen(
    currentDraft: FlightDraft,
    onAirportSelected: (Airport) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToBoardingPass: () -> Unit,
    unitSystem: String = "km",
    isSandboxMode: Boolean = false,
    isSettingCurrentLocation: Boolean = false,
    onSandboxAirportSelected: (origin: Airport, destination: Airport?) -> Unit = { _, _ -> },
    onCurrentLocationSet: (Airport) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val viewModel: NewFlightViewModel = viewModel(
        factory = NewFlightViewModel.Factory(context.applicationContext as android.app.Application)
    )
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    
    // 1. Data Loading
    val allAirports = uiState.allAirports
    val originIata = currentDraft.origin.iata
    val originAirport = currentDraft.origin
    
    LaunchedEffect(originIata, currentDraft.destination?.iata) {
        viewModel.initialize(originIata, currentDraft.destination)
    }

    // 2. State
    val selectedDestination = uiState.selectedDestination
    val searchRadiusKm = uiState.searchRadiusKm
    val showSameAirportDialog = uiState.showSameAirportDialog
    val showQuickFlightDialog = uiState.showQuickFlightDialog
    val searchQuery = uiState.searchQuery
    
    // 카메라 상태 (초기 위치: 출발 공항)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(originAirport.latitude, originAirport.longitude),
            NewFlightInitialMapZoom
        )
    }

    // 3. Derived Data (정렬된 리스트)
    val mapCenter = cameraPositionState.position.target
    val sortedAirports = remember(mapCenter, searchRadiusKm, allAirports, searchQuery) {
        val query = searchQuery.trim().lowercase()
        val targetFlightMinutes = query.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?.let { it * 60.0 }
        allAirports
            .map { airport ->
                val distFromCenter = FlightUtils.calculateDistance(
                    mapCenter.latitude, mapCenter.longitude,
                    airport.latitude, airport.longitude
                )
                val distFromOrigin = FlightUtils.calculateDistance(
                    originAirport.latitude, originAirport.longitude,
                    airport.latitude, airport.longitude
                )
                Triple(airport, distFromCenter, distFromOrigin)
            }
            .filter { (airport, distFromCenter, distFromOrigin) ->
                // 1. 검색 필터
                if (query.isNotEmpty()) {
                    if (targetFlightMinutes != null) {
                        val durationMinutes = FlightUtils.estimateDurationMinutes(distFromOrigin)
                        val toleranceMinutes = targetFlightMinutes * 0.1
                        val lowerBound = targetFlightMinutes - toleranceMinutes
                        val upperBound = targetFlightMinutes + toleranceMinutes
                        if (airport.iata != originIata && durationMinutes.toDouble() !in lowerBound..upperBound) {
                            return@filter false
                        }
                    } else {
                        val matchesSearch =
                            airport.iata.lowercase().contains(query) ||
                            airport.cityKo.lowercase().contains(query) ||
                            airport.cityEn.lowercase().contains(query) ||
                            airport.nameKo.lowercase().contains(query) ||
                            airport.nameEn.lowercase().contains(query) ||
                            airport.country.lowercase().contains(query)

                        if (!matchesSearch) return@filter false
                    }
                }
                
                // 2. 거리 필터
                if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) {
                    // 무제한: 모든 공항 표시
                    return@filter true
                }
                
                // 출발지는 항상 표시
                if (airport.iata == originIata) return@filter true
                
                // 300km 이하에서는 테두리 필터링 끄고 일반 필터링
                if (searchRadiusKm <= NewFlightSmallRadiusThresholdKm) {
                    distFromOrigin <= searchRadiusKm.toDouble()
                } else {
                    // 테두리 안쪽만 표시 (테두리부터 -300km 구간)
                    val margin = NewFlightRingFilterMarginKm
                    val lowerBound = (searchRadiusKm.toDouble() - margin).coerceAtLeast(0.0)
                    val upperBound = searchRadiusKm.toDouble()
                    
                    distFromOrigin >= lowerBound && distFromOrigin <= upperBound
                }
            }
            .sortedBy { (airport, distFromCenter, _) ->
                if (airport.iata == originIata) -1.0 else distFromCenter
            }
            .map { it.first }
    }
    val quickFlightSuggestions = remember(allAirports, originAirport.iata) {
        buildQuickFlightSuggestions(originAirport, allAirports)
    }

    fun selectDestination(airport: Airport) {
        viewModel.selectDestination(airport)
        onAirportSelected(airport)
        scope.launch {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(LatLng(airport.latitude, airport.longitude))
            )
        }
    }

    fun quickSelectDestination(airport: Airport) {
        viewModel.selectDestination(airport)
        onAirportSelected(airport)
        when {
            isSandboxMode -> onSandboxAirportSelected(originAirport, airport)
            isSettingCurrentLocation -> onCurrentLocationSet(originAirport)
            else -> onNavigateToBoardingPass()
        }
    }

    // Map Snapping
    LaunchedEffect(cameraPositionState.isMoving, sortedAirports, originIata) {
        if (!cameraPositionState.isMoving) {
            val center = cameraPositionState.position.target
            val visibleSelectableAirports = sortedAirports.filter { it.iata != originIata }
            val closest = visibleSelectableAirports
                .minByOrNull { 
                    FlightUtils.calculateDistance(center.latitude, center.longitude, it.latitude, it.longitude)
                }
            
            closest?.let {
                val dist = FlightUtils.calculateDistance(center.latitude, center.longitude, it.latitude, it.longitude)
                if (dist < NewFlightSelectionSnapDistanceKm) { 
                     viewModel.selectDestination(it)
                     onAirportSelected(it)
                }
            }
        }
    }

    // Zoom Sync
    LaunchedEffect(searchRadiusKm) {
        val zoom = when {
            searchRadiusKm >= NewFlightUnlimitedRadiusKm -> 2f
            searchRadiusKm >= NewFlightZoomRadius10000 -> 3f
            searchRadiusKm >= NewFlightZoomRadius5000 -> 4f
            searchRadiusKm >= NewFlightZoomRadius2000 -> 5f
            searchRadiusKm >= NewFlightZoomRadius1000 -> 6f
            searchRadiusKm >= NewFlightZoomRadius500 -> 7f
            searchRadiusKm < NewFlightZoomRadius500 -> 8f
            else -> 5f
        }
        cameraPositionState.animate(CameraUpdateFactory.zoomTo(zoom))
    }

    // Bottom Sheet Scaffold
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = if (isLandscape) 0.dp else 220.dp,
        sheetContainerColor = FlightDarkGray,
        sheetContentColor = Color.White,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                // Handle & Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.DragHandle, contentDescription = null, tint = Color.Gray)
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // Destination Info
                    Text(
                        text = selectedDestination?.let {
                            stringResource(R.string.newflight_selected_destination_format, it.cityKo, it.iata)
                        } ?: stringResource(R.string.newflight_select_airport_on_map),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    if (selectedDestination != null) {
                        val distKm = FlightUtils.calculateDistance(originAirport, selectedDestination!!)
                        Text(
                            text = stringResource(
                                R.string.newflight_selected_destination_distance_format,
                                FlightUtils.formatDistance(distKm, unitSystem),
                                FlightUtils.formatDuration(FlightUtils.estimateDurationMinutes(distKm))
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            color = FlightPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.newflight_move_map_or_select_from_list),
                            style = MaterialTheme.typography.bodyLarge,
                            color = FlightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Controls
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    // 검색창
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.newflight_search_placeholder), color = FlightGray) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = FlightGray)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.newflight_clear_search), tint = FlightGray)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FlightPrimary,
                            unfocusedBorderColor = FlightGray.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = FlightGray,
                            unfocusedPlaceholderColor = FlightGray,
                            cursorColor = FlightPrimary
                        )
                    )

                    val radiusText = if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) stringResource(R.string.newflight_unlimited) else "${searchRadiusKm}km"
                    val durationText = if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) "" else " • ${FlightUtils.formatDuration(FlightUtils.estimateDurationMinutes(searchRadiusKm.toDouble()))}"
                    Text(
                        text = stringResource(R.string.newflight_search_radius_format, radiusText, durationText),
                        color = FlightPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    RulerPicker(
                        initialValue = searchRadiusKm,
                        minRequest = 100,
                        maxRequest = NewFlightUnlimitedRadiusKm,
                        step = NewFlightRadiusStepKm,
                        onValueChange = { viewModel.updateSearchRadius(it) }
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sortedAirports.size, key = { index -> index }) { index ->
                        val airport = sortedAirports[index]
                        val isSelected = airport == selectedDestination
                        val isOrigin = airport.iata == originIata

                        AirportListItem(
                            airport = airport,
                            origin = originAirport,
                            isSelected = isSelected,
                            isOrigin = isOrigin,
                            unitSystem = unitSystem,
                             onClick = {
                                 if (!isOrigin) {
                                     selectDestination(airport)
                                 }
                             }
                         )
                    }
                }

                // CTA Button
                Button(
                    onClick = {
                        // 출발지와 도착지가 같은지 확인
                        if (selectedDestination?.iata == originIata) {
                            // 같은 공항 선택 시 다이얼로그 표시
                            viewModel.showSameAirportDialog()
                        } else {
                            if (isSandboxMode) {
                                // 샌드박스 모드: 선택된 공항을 SandboxScreen 에 전달
                                onSandboxAirportSelected(originAirport, selectedDestination)
                            } else if (isSettingCurrentLocation) {
                                // 현재 위치 설정 모드: 선택된 공항을 현재 위치로 저장
                                onCurrentLocationSet(originAirport)
                            } else {
                                onNavigateToBoardingPass()
                            }
                        }
                    },
                    enabled = selectedDestination != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FlightPrimary,
                        disabledContainerColor = FlightGray.copy(alpha = 0.3f),
                        contentColor = FlightBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp)
                ) {
                    Text(
                        text = when {
                            isSettingCurrentLocation -> stringResource(R.string.newflight_set_current_location)
                            isSandboxMode -> stringResource(R.string.newflight_airport_selection_done)
                            else -> stringResource(R.string.newflight_confirm_destination)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(FlightBlack)) {
            // Map
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                    compassEnabled = false
                ),
                properties = MapProperties(
                    mapStyleOptions = try {
                        MapStyleOptions.loadRawResourceStyle(context, com.example.openflight4and.R.raw.map_style_dark)
                    } catch (e: Exception) { null }
                )
            ) {
                // Circle
                // Circle (무제한일 때는 표시 안 함)
                if (searchRadiusKm < NewFlightUnlimitedRadiusKm) {
                    Circle(
                        center = LatLng(originAirport.latitude, originAirport.longitude),
                        radius = searchRadiusKm * 1000.0,
                        strokeColor = FlightPrimary.copy(alpha = 0.5f),
                        strokeWidth = 2f,
                        fillColor = FlightPrimary.copy(alpha = 0.05f)
                    )
                }

                // Polyline
                selectedDestination?.let { dest ->
                    Polyline(
                        points = listOf(
                            LatLng(originAirport.latitude, originAirport.longitude),
                            LatLng(dest.latitude, dest.longitude)
                        ),
                        color = FlightPrimary,
                        width = 5f,
                        geodesic = true,
                        pattern = listOf(Dash(30f), Gap(20f))
                    )
                }

                // Markers
                allAirports.forEach { airport ->
                    // 출발지는 항상 표시
                    val isOrigin = airport.iata == originIata
                    if (isOrigin) {
                        // 출발지 마커 표시
                        val isSelected = airport == selectedDestination
                        val markerIcon = remember(airport.iata, isSelected, isOrigin) {
                            val label = context.getString(R.string.newflight_origin_short)
                            MapBitmapUtils.createCustomMarkerBitmap(context, airport.iata, label, isSelected || isOrigin)
                        }
                        Marker(
                            state = MarkerState(position = LatLng(airport.latitude, airport.longitude)),
                            icon = markerIcon,
                            zIndex = 2f,
                            onClick = { true }
                        )
                        return@forEach
                    }
                    
                    // 무제한일 때만 모든 공항 표시
                    if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) {
                        val distFromOrigin = FlightUtils.calculateDistance(originAirport, airport)
                        val isSelected = airport == selectedDestination
                        val markerIcon = remember(airport.iata, isSelected, isOrigin) {
                            val label = if (isSelected) context.getString(R.string.newflight_arrival_short) else airport.cityKo.split("/")[0]
                            MapBitmapUtils.createCustomMarkerBitmap(context, airport.iata, label, isSelected || isOrigin)
                        }
                        Marker(
                            state = MarkerState(position = LatLng(airport.latitude, airport.longitude)),
                            icon = markerIcon,
                            zIndex = if (isSelected) 2f else 1f,
                                 onClick = {
                                     selectDestination(airport)
                                     true
                                 }
                             )
                        return@forEach
                    }
                    
                    // 300km 이하에서는 테두리 필터링 끄고 일반 필터링
                    if (searchRadiusKm <= NewFlightSmallRadiusThresholdKm) {
                        val distFromOrigin = FlightUtils.calculateDistance(originAirport, airport)
                        if (distFromOrigin <= searchRadiusKm.toDouble()) {
                            // 마커 표시
                            val isSelected = airport == selectedDestination
                            val markerIcon = remember(airport.iata, isSelected, isOrigin) {
                                val label = if (isSelected) context.getString(R.string.newflight_arrival_short) else airport.cityKo.split("/")[0]
                                MapBitmapUtils.createCustomMarkerBitmap(context, airport.iata, label, isSelected || isOrigin)
                            }
                            Marker(
                                state = MarkerState(position = LatLng(airport.latitude, airport.longitude)),
                                icon = markerIcon,
                                zIndex = if (isSelected) 2f else 1f,
                                 onClick = {
                                     selectDestination(airport)
                                     true
                                 }
                             )
                        }
                    } else {
                        // 테두리 안쪽만 표시 (테두리부터 -300km 구간)
                        val distFromOrigin = FlightUtils.calculateDistance(originAirport, airport)
                        val margin = NewFlightRingFilterMarginKm
                        val lowerBound = (searchRadiusKm - margin).coerceAtLeast(0.0)
                        val upperBound = searchRadiusKm.toDouble()

                        if (distFromOrigin >= lowerBound && distFromOrigin <= upperBound) {
                            // 마커 표시
                            val isSelected = airport == selectedDestination
                            val markerIcon = remember(airport.iata, isSelected, isOrigin) {
                                val label = if (isSelected) context.getString(R.string.newflight_arrival_short) else airport.cityKo.split("/")[0]
                                MapBitmapUtils.createCustomMarkerBitmap(context, airport.iata, label, isSelected || isOrigin)
                            }
                            Marker(
                                state = MarkerState(position = LatLng(airport.latitude, airport.longitude)),
                                icon = markerIcon,
                                zIndex = if (isSelected) 2f else 1f,
                                 onClick = {
                                     selectDestination(airport)
                                     true
                                 }
                             )
                        }
                    }
                }
            }

            // Crosshair
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FlightTakeoff,
                    contentDescription = null,
                    tint = FlightPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Top Bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = Color.White)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                TextButton(
                    onClick = { viewModel.showQuickFlightDialog() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                ) {
                    Text(stringResource(R.string.newflight_quick_flight_title), fontWeight = FontWeight.Bold)
                }
            }

            if (isLandscape) {
                NewFlightLandscapePanel(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 72.dp, end = 16.dp, bottom = 16.dp)
                        .width(340.dp)
                        .fillMaxHeight(),
                    selectedDestination = selectedDestination,
                    originAirport = originAirport,
                    originIata = originIata,
                    unitSystem = unitSystem,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    searchRadiusKm = searchRadiusKm,
                    onSearchRadiusChange = { viewModel.updateSearchRadius(it) },
                    sortedAirports = sortedAirports,
                    onAirportClick = { airport ->
                        if (airport.iata != originIata) {
                            selectDestination(airport)
                        }
                    },
                    isSandboxMode = isSandboxMode,
                    isSettingCurrentLocation = isSettingCurrentLocation,
                    onConfirm = {
                        if (selectedDestination?.iata == originIata) {
                            viewModel.showSameAirportDialog()
                        } else {
                            if (isSandboxMode) {
                                onSandboxAirportSelected(originAirport, selectedDestination)
                            } else if (isSettingCurrentLocation) {
                                onCurrentLocationSet(originAirport)
                            } else {
                                onNavigateToBoardingPass()
                            }
                        }
                    }
                )
            }

            // Same Airport Dialog
            if (showSameAirportDialog) {
                SameAirportDialog(onDismiss = { viewModel.hideSameAirportDialog() })
            }

            if (showQuickFlightDialog) {
                QuickFlightDialogCards(
                    suggestions = quickFlightSuggestions,
                    unitSystem = unitSystem,
                    onDismiss = { viewModel.hideQuickFlightDialog() },
                    onSelect = { airport ->
                        viewModel.hideQuickFlightDialog()
                        quickSelectDestination(airport)
                    }
                )
            }
        }
    }
}

@Composable
fun AirportListItem(
    airport: Airport,
    origin: Airport,
    isSelected: Boolean,
    isOrigin: Boolean,
    unitSystem: String,
    onClick: () -> Unit
) {
    val distanceKm = FlightUtils.calculateDistance(origin, airport)
    val distStr = FlightUtils.formatDistance(distanceKm, unitSystem)
    val durationMinutes = FlightUtils.estimateDurationMinutes(distanceKm)
    val durationStr = FlightUtils.formatDuration(durationMinutes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) FlightPrimary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(enabled = !isOrigin) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (isOrigin) FlightGray else if (isSelected) FlightPrimary else Color(0xFF3E3C3A),
            contentColor = if (isSelected) FlightBlack else Color.White,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.size(width = 60.dp, height = 44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = airport.iata, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = airport.nameKo,
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                if (isOrigin) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = FlightPrimary, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = stringResource(R.string.newflight_origin_badge),
                            color = FlightBlack,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = "${airport.cityKo}, ${airport.country}",
                color = FlightGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = distStr,
                color = if (isSelected) FlightPrimary else FlightGray,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = durationStr,
                color = if (isSelected) FlightPrimary.copy(alpha = 0.8f) else FlightGray,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SameAirportDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.newflight_same_airport_title), color = Color.White) },
        text = { Text(stringResource(R.string.newflight_same_airport_message), color = FlightGray) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_confirm)) }
        },
        containerColor = Color(0xFF0D0000)
    )
}

@Composable
private fun QuickFlightDialogCards(
    suggestions: List<QuickFlightSuggestion>,
    unitSystem: String,
    onDismiss: () -> Unit,
    onSelect: (Airport) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.newflight_quick_flight_title), color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.newflight_quick_flight_description),
                    color = FlightGray
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    suggestions.forEach { suggestion ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelect(suggestion.airport) },
                            shape = RoundedCornerShape(14.dp),
                            color = FlightPrimary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                FlightPrimary.copy(alpha = 0.28f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    stringResource(R.string.newflight_quick_flight_recommendation_format, suggestion.targetMinutes / 60),
                                    color = FlightPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${suggestion.airport.nameKo} (${suggestion.airport.iata})",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${suggestion.airport.cityKo}, ${suggestion.airport.country}",
                                    color = FlightGray,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "${FlightUtils.formatDistance(suggestion.distanceKm, unitSystem)}  |  ${FlightUtils.formatDuration(suggestion.durationMinutes)}",
                                    color = FlightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        containerColor = Color(0xFF0D0000)
    )
}

@Composable
private fun NewFlightLandscapePanel(
    modifier: Modifier,
    selectedDestination: Airport?,
    originAirport: Airport,
    originIata: String,
    unitSystem: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchRadiusKm: Int,
    onSearchRadiusChange: (Int) -> Unit,
    sortedAirports: List<Airport>,
    onAirportClick: (Airport) -> Unit,
    isSandboxMode: Boolean,
    isSettingCurrentLocation: Boolean,
    onConfirm: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = FlightDarkGray.copy(alpha = 0.94f),
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = selectedDestination?.let {
                        stringResource(R.string.newflight_selected_destination_format, it.cityKo, it.iata)
                    } ?: stringResource(R.string.newflight_select_airport_on_map),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (selectedDestination != null) {
                    val distKm = FlightUtils.calculateDistance(originAirport, selectedDestination)
                    Text(
                        text = stringResource(
                            R.string.newflight_distance_duration_format,
                            FlightUtils.formatDistance(distKm, unitSystem),
                            FlightUtils.formatDuration(FlightUtils.estimateDurationMinutes(distKm))
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = FlightPrimary,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.newflight_move_map_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = {
                    Text(
                        stringResource(R.string.newflight_search_placeholder),
                        color = FlightGray
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = FlightGray)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.newflight_clear_search), tint = FlightGray)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = FlightPrimary,
                    unfocusedBorderColor = FlightGray.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedPlaceholderColor = FlightGray,
                    unfocusedPlaceholderColor = FlightGray,
                    cursorColor = FlightPrimary
                )
            )

            val radiusText = if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) stringResource(R.string.newflight_unlimited) else "${searchRadiusKm}km"
            val durationText = if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) "" else "  |  ${FlightUtils.formatDuration(FlightUtils.estimateDurationMinutes(searchRadiusKm.toDouble()))}"
            Text(
                text = stringResource(R.string.newflight_search_radius_format, radiusText, durationText),
                color = FlightPrimary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            RulerPicker(
                initialValue = searchRadiusKm,
                minRequest = 100,
                maxRequest = NewFlightUnlimitedRadiusKm,
                step = NewFlightRadiusStepKm,
                onValueChange = onSearchRadiusChange
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.padding(top = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sortedAirports) { airport ->
                    AirportListItem(
                        airport = airport,
                        origin = originAirport,
                        isSelected = airport == selectedDestination,
                        isOrigin = airport.iata == originIata,
                        unitSystem = unitSystem,
                        onClick = { onAirportClick(airport) }
                    )
                }
            }

            Button(
                onClick = onConfirm,
                enabled = selectedDestination != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FlightPrimary,
                    disabledContainerColor = FlightGray.copy(alpha = 0.3f),
                    contentColor = FlightBlack
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp)
            ) {
                Text(
                    text = when {
                        isSettingCurrentLocation -> stringResource(R.string.newflight_set_current_location)
                        isSandboxMode -> stringResource(R.string.newflight_airport_selection_done)
                        else -> stringResource(R.string.newflight_confirm_destination)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        }
    }
}

private fun buildQuickFlightSuggestions(
    origin: Airport,
    airports: List<Airport>
): List<QuickFlightSuggestion> {
    val remaining = airports
        .filter { it.iata != origin.iata }
        .toMutableList()
    val suggestions = mutableListOf<QuickFlightSuggestion>()

    for (targetMinutes in NewFlightQuickSuggestionMinutes) {
        val bestAirport = remaining.minByOrNull { airport ->
            val distanceKm = FlightUtils.calculateDistance(origin, airport)
            val durationMinutes = FlightUtils.estimateDurationMinutes(distanceKm)
            kotlin.math.abs(durationMinutes - targetMinutes)
        } ?: continue

        val distanceKm = FlightUtils.calculateDistance(origin, bestAirport)
        val durationMinutes = FlightUtils.estimateDurationMinutes(distanceKm)
        suggestions += QuickFlightSuggestion(
            targetMinutes = targetMinutes,
            airport = bestAirport,
            durationMinutes = durationMinutes,
            distanceKm = distanceKm
        )
        remaining.remove(bestAirport)
    }

    return suggestions
}

@Composable
private fun QuickFlightDialog(
    suggestions: List<QuickFlightSuggestion>,
    unitSystem: String,
    onDismiss: () -> Unit,
    onSelect: (Airport) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.newflight_quick_flight_title), color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "1시간, 2시간, 3시간에 가장 가까운 비행 시간을 가진 공항입니다.",
                    color = FlightGray
                )
                suggestions.forEach { suggestion ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(suggestion.airport) },
                        shape = RoundedCornerShape(14.dp),
                        color = FlightPrimary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            FlightPrimary.copy(alpha = 0.28f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "${suggestion.targetMinutes / 60}시간 추천",
                                color = FlightPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${suggestion.airport.nameKo} (${suggestion.airport.iata})",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${suggestion.airport.cityKo}, ${suggestion.airport.country}",
                                color = FlightGray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "${FlightUtils.formatDistance(suggestion.distanceKm, unitSystem)} • 약 ${FlightUtils.formatDuration(suggestion.durationMinutes)}",
                                color = FlightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        containerColor = Color(0xFF0D0000)
    )
}

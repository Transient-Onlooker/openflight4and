package com.example.openflight4and.ui.newflight

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
import androidx.compose.ui.text.style.TextOverflow
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
import java.util.Locale
import kotlin.math.pow
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
private const val NewFlightManualSelectionMarkerWidthPx = 56.0
private const val NewFlightManualSelectionMarkerHeightPx = 32.0
private const val NewFlightTileSizePx = 256.0
private const val NewFlightMaxMarkersZoomFar = 40
private const val NewFlightMaxMarkersZoomMid = 80
private const val NewFlightMaxMarkersZoomNear = 140
private const val NewFlightZoomRadius10000 = 10000
private const val NewFlightZoomRadius5000 = 5000
private const val NewFlightZoomRadius2000 = 2000
private const val NewFlightZoomRadius1000 = 1000
private const val NewFlightZoomRadius500 = 500
private const val NewFlightZoomRadius300 = 300
private const val NewFlightRadiusStepKm = 100
private val NewFlightQuickSuggestionMinutes = listOf(30, 60, 120)

@Composable
private fun quickFlightRecommendationLabel(targetMinutes: Int): String {
    return if (targetMinutes < 60) {
        stringResource(R.string.newflight_quick_flight_recommendation_minutes_format, targetMinutes)
    } else {
        stringResource(R.string.newflight_quick_flight_recommendation_hours_format, targetMinutes / 60)
    }
}

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
    val languageTag = configuration.locales[0]?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
    val bottomSheetMaxHeight = maxOf(360.dp, configuration.screenHeightDp.dp * 0.6f)
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
    var settledMapCenter by remember(originAirport.iata) {
        mutableStateOf(LatLng(originAirport.latitude, originAirport.longitude))
    }
    val sortedAirports = remember(settledMapCenter, searchRadiusKm, allAirports, searchQuery) {
        val query = searchQuery.trim().lowercase()
        val targetFlightMinutes = query.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?.let { it * 60.0 }
        allAirports
            .map { airport ->
                val distFromCenter = FlightUtils.calculateDistance(
                    settledMapCenter.latitude, settledMapCenter.longitude,
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

                    return@filter true
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
    val manualSelectionAirports = remember(allAirports, searchQuery, languageTag) {
        val query = searchQuery.trim().lowercase()
        allAirports
            .filter { airport ->
                query.isBlank() ||
                    airport.iata.lowercase().contains(query) ||
                    airport.cityKo.lowercase().contains(query) ||
                    airport.cityEn.lowercase().contains(query) ||
                    airport.nameKo.lowercase().contains(query) ||
                    airport.nameEn.lowercase().contains(query) ||
                    airport.country.lowercase().contains(query)
            }
            .sortedWith { left, right ->





                val cityCompare = String.CASE_INSENSITIVE_ORDER.compare(
                    left.localizedCity(languageTag),
                    right.localizedCity(languageTag)
                )
                if (cityCompare != 0) {
                    cityCompare
                } else {
                    String.CASE_INSENSITIVE_ORDER.compare(left.iata, right.iata)
                }
            }
    }
    val manualSelectionZoomBucket by remember(cameraPositionState) {
        derivedStateOf { cameraPositionState.position.zoom.roundToInt().coerceAtLeast(0) }
    }
    val manualSelectionMapAirports = remember(
        manualSelectionAirports,
        selectedDestination?.iata,
        manualSelectionZoomBucket
    ) {
        visibleManualSelectionAirports(
            airports = manualSelectionAirports,
            selectedAirport = selectedDestination,
            zoomBucket = manualSelectionZoomBucket
        )
    }
    val selectionModeMapAirports = remember(
        sortedAirports,
        selectedDestination?.iata,
        manualSelectionZoomBucket
    ) {
        visibleManualSelectionAirports(
            airports = sortedAirports,
            selectedAirport = selectedDestination,
            zoomBucket = manualSelectionZoomBucket
        )
    }
    val displayedAirports = if (isSettingCurrentLocation) manualSelectionAirports else sortedAirports
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
            isSettingCurrentLocation -> onCurrentLocationSet(airport)
            else -> onNavigateToBoardingPass()
        }
    }

    // Map Snapping
    LaunchedEffect(cameraPositionState.isMoving, displayedAirports, originIata, isSettingCurrentLocation) {
        if (isSettingCurrentLocation) return@LaunchedEffect
        if (!cameraPositionState.isMoving) {
            val center = cameraPositionState.position.target
            settledMapCenter = center
            val visibleSelectableAirports = displayedAirports.filter { it.iata != originIata }
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
    LaunchedEffect(searchRadiusKm, isSettingCurrentLocation) {
        if (isSettingCurrentLocation) return@LaunchedEffect
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

    val showBottomSheet = !isLandscape

    // Bottom Sheet Scaffold
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    )

    if (showBottomSheet) {
        BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 260.dp,
        sheetContainerColor = FlightDarkGray,
        sheetContentColor = Color.White,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContent = {
            if (showBottomSheet) {
                Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = bottomSheetMaxHeight)
                ) {
                // Handle & Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Destination Info
                    Text(
                        text = selectedDestination?.let {
                            stringResource(R.string.newflight_selected_destination_format, it.localizedCity(languageTag), it.iata)
                        } ?: if (isSettingCurrentLocation) {
                            stringResource(R.string.newflight_select_current_location_airport)
                        } else {
                            stringResource(R.string.newflight_select_airport_on_map)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    if (selectedDestination != null && !isSettingCurrentLocation) {
                        val distKm = FlightUtils.calculateDistance(originAirport, selectedDestination!!)
                        Text(
                            text = stringResource(
                                R.string.newflight_selected_destination_distance_format,
                                FlightUtils.formatDistance(distKm, unitSystem),
                                FlightUtils.formatDuration(context, FlightUtils.estimateDurationMinutes(distKm))
                            ),
                            style = MaterialTheme.typography.titleLarge,
                            color = FlightPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = if (isSettingCurrentLocation) {
                                stringResource(R.string.newflight_search_and_select_airport)
                            } else {
                                stringResource(R.string.newflight_move_map_or_select_from_list)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = FlightGray
                        )
                    }
                }

                    Spacer(modifier = Modifier.height(16.dp))


                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Controls
                Column(
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                ) {
                    if (!isSettingCurrentLocation) {
                        val radiusText = if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) {
                            stringResource(R.string.newflight_unlimited)
                        } else {
                            "${searchRadiusKm}km"
                        }
                        val durationText = if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) {
                            ""
                        } else {
                            " • ${FlightUtils.formatDuration(context, FlightUtils.estimateDurationMinutes(searchRadiusKm.toDouble()))}"
                        }
                        Text(
                            text = stringResource(R.string.newflight_search_radius_format, radiusText, durationText),
                            color = FlightPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 2.dp)
                        )
                        RulerPicker(
                            initialValue = searchRadiusKm,
                            minRequest = 100,
                            maxRequest = NewFlightUnlimitedRadiusKm,
                            step = NewFlightRadiusStepKm,
                            onValueChange = { viewModel.updateSearchRadius(it) }
                        )
                    }
                    // 검색창
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it, NewFlightUnlimitedRadiusKm) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.newflight_search_placeholder), color = FlightGray) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = FlightGray)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("", NewFlightUnlimitedRadiusKm) }) {
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

                    if (false && !isSettingCurrentLocation) {
                        val radiusText = if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) stringResource(R.string.newflight_unlimited) else "${searchRadiusKm}km"
                    val durationText = if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) "" else " • ${FlightUtils.formatDuration(context, FlightUtils.estimateDurationMinutes(searchRadiusKm.toDouble()))}"
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
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = bottomSheetMaxHeight * 0.4f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedAirports.size, key = { index -> displayedAirports[index].iata }) { index ->
                        val airport = displayedAirports[index]
                        val isSelected = airport == selectedDestination
                        val isOrigin = airport.iata == originIata

                        AirportListItem(
                            context = context,
                            airport = airport,
                            origin = originAirport,
                            isSelected = isSelected,
                            isOrigin = isOrigin,
                            unitSystem = unitSystem,
                            languageTag = languageTag,
                            showMetrics = !isSettingCurrentLocation,
                             onClick = {
                                 if (isSettingCurrentLocation || !isOrigin) {
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
                        if (!isSettingCurrentLocation && selectedDestination?.iata == originIata) {
                            // 같은 공항 선택 시 다이얼로그 표시
                            viewModel.showSameAirportDialog()
                        } else {
                            if (isSandboxMode) {
                                // 샌드박스 모드: 선택된 공항을 SandboxScreen 에 전달
                                onSandboxAirportSelected(originAirport, selectedDestination)
                            } else if (isSettingCurrentLocation) {
                                // 현재 위치 설정 모드: 선택된 공항을 현재 위치로 저장
                                selectedDestination?.let(onCurrentLocationSet)
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
        ) {
        Box(modifier = Modifier.fillMaxSize().background(FlightBlack)) {
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
                if (!isSettingCurrentLocation && searchRadiusKm < NewFlightUnlimitedRadiusKm) {
                    Circle(
                        center = LatLng(originAirport.latitude, originAirport.longitude),
                        radius = searchRadiusKm * 1000.0,
                        strokeColor = FlightPrimary.copy(alpha = 0.5f),
                        strokeWidth = 2f,
                        fillColor = FlightPrimary.copy(alpha = 0.05f)
                    )
                }

                // Polyline
                selectedDestination?.takeUnless { isSettingCurrentLocation }?.let { dest ->
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
                val mapAirports = if (isSettingCurrentLocation) {
                    manualSelectionMapAirports
                } else {
                    selectionModeMapAirports
                }
                mapAirports.forEach { airport ->
                    // 출발지는 항상 표시
                    val isOrigin = airport.iata == originIata
                    if (!isSettingCurrentLocation && isOrigin) {
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

                    if (isSettingCurrentLocation) {
                        val isSelected = airport == selectedDestination
                        val markerIcon = remember(airport.iata, isSelected) {
                            val label = if (isSelected) context.getString(R.string.newflight_arrival_short) else airport.localizedCity(languageTag).split("/")[0]
                            MapBitmapUtils.createCustomMarkerBitmap(context, airport.iata, label, isSelected)
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
                    
                    // 무제한일 때만 모든 공항 표시
                    if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) {
                        val distFromOrigin = FlightUtils.calculateDistance(originAirport, airport)
                        val isSelected = airport == selectedDestination
                        val markerIcon = remember(airport.iata, isSelected, isOrigin) {
                            val label = if (isSelected) context.getString(R.string.newflight_arrival_short) else airport.localizedCity(languageTag).split("/")[0]
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
                                val label = if (isSelected) context.getString(R.string.newflight_arrival_short) else airport.localizedCity(languageTag).split("/")[0]
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
                                val label = if (isSelected) context.getString(R.string.newflight_arrival_short) else airport.localizedCity(languageTag).split("/")[0]
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
            if (!isSettingCurrentLocation) {
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

            if (!isSettingCurrentLocation) {
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
            }

            if (isLandscape) {
                NewFlightLandscapePanel(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 72.dp, end = 16.dp, bottom = 16.dp)
                        .width(340.dp)
                        .fillMaxHeight(),
                    context = context,
                    selectedDestination = selectedDestination,
                    originAirport = originAirport,
                    originIata = originIata,
                    unitSystem = unitSystem,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it, NewFlightUnlimitedRadiusKm) },
                    searchRadiusKm = searchRadiusKm,
                    onSearchRadiusChange = { viewModel.updateSearchRadius(it) },
                    displayedAirports = displayedAirports,
                    languageTag = languageTag,
                    onAirportClick = { airport ->
                        if (isSettingCurrentLocation || airport.iata != originIata) {
                            selectDestination(airport)
                        }
                    },
                    isSandboxMode = isSandboxMode,
                    isSettingCurrentLocation = isSettingCurrentLocation,
                    onConfirm = {
                        if (!isSettingCurrentLocation && selectedDestination?.iata == originIata) {
                            viewModel.showSameAirportDialog()
                        } else {
                            if (isSandboxMode) {
                                onSandboxAirportSelected(originAirport, selectedDestination)
                            } else if (isSettingCurrentLocation) {
                                selectedDestination?.let(onCurrentLocationSet)
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
                    context = context,
                    suggestions = quickFlightSuggestions,
                    unitSystem = unitSystem,
                    languageTag = languageTag,
                    onDismiss = { viewModel.hideQuickFlightDialog() },
                    onSelect = { airport ->
                        viewModel.hideQuickFlightDialog()
                        quickSelectDestination(airport)
                    }
                )
            }
        }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().background(FlightBlack)) {
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
                if (!isSettingCurrentLocation && searchRadiusKm < NewFlightUnlimitedRadiusKm) {
                    Circle(
                        center = LatLng(originAirport.latitude, originAirport.longitude),
                        radius = searchRadiusKm * 1000.0,
                        strokeColor = FlightPrimary.copy(alpha = 0.5f),
                        strokeWidth = 2f,
                        fillColor = FlightPrimary.copy(alpha = 0.05f)
                    )
                }

                selectedDestination?.takeUnless { isSettingCurrentLocation }?.let { dest ->
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

                val mapAirports = if (isSettingCurrentLocation) {
                    manualSelectionMapAirports
                } else {
                    selectionModeMapAirports
                }
                mapAirports.forEach { airport ->
                    val isOrigin = airport.iata == originIata
                    if (!isSettingCurrentLocation && isOrigin) {
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

                    if (isSettingCurrentLocation) {
                        val isSelected = airport == selectedDestination
                        val markerIcon = remember(airport.iata, isSelected) {
                            val label = if (isSelected) context.getString(R.string.newflight_arrival_short) else airport.localizedCity(languageTag).split("/")[0]
                            MapBitmapUtils.createCustomMarkerBitmap(context, airport.iata, label, isSelected)
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

                    if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) {
                        val isSelected = airport == selectedDestination
                        val markerIcon = remember(airport.iata, isSelected, isOrigin) {
                            val label = if (isSelected) context.getString(R.string.newflight_arrival_short) else airport.localizedCity(languageTag).split("/")[0]
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

                    if (searchRadiusKm <= NewFlightSmallRadiusThresholdKm) {
                        val distFromOrigin = FlightUtils.calculateDistance(originAirport, airport)
                        if (distFromOrigin <= searchRadiusKm.toDouble()) {
                            val isSelected = airport == selectedDestination
                            val markerIcon = remember(airport.iata, isSelected, isOrigin) {
                                val label = if (isSelected) context.getString(R.string.newflight_arrival_short) else airport.localizedCity(languageTag).split("/")[0]
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
                        val distFromOrigin = FlightUtils.calculateDistance(originAirport, airport)
                        val margin = NewFlightRingFilterMarginKm
                        val lowerBound = (searchRadiusKm - margin).coerceAtLeast(0.0)
                        val upperBound = searchRadiusKm.toDouble()

                        if (distFromOrigin >= lowerBound && distFromOrigin <= upperBound) {
                            val isSelected = airport == selectedDestination
                            val markerIcon = remember(airport.iata, isSelected, isOrigin) {
                                val label = if (isSelected) context.getString(R.string.newflight_arrival_short) else airport.localizedCity(languageTag).split("/")[0]
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

            if (!isSettingCurrentLocation) {
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
            }

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

            if (!isSettingCurrentLocation) {
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
            }

            NewFlightLandscapePanel(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 72.dp, end = 16.dp, bottom = 16.dp)
                    .width(340.dp)
                    .fillMaxHeight(),
                context = context,
                selectedDestination = selectedDestination,
                originAirport = originAirport,
                originIata = originIata,
                unitSystem = unitSystem,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.updateSearchQuery(it, NewFlightUnlimitedRadiusKm) },
                searchRadiusKm = searchRadiusKm,
                onSearchRadiusChange = { viewModel.updateSearchRadius(it) },
                displayedAirports = displayedAirports,
                languageTag = languageTag,
                onAirportClick = { airport ->
                    if (isSettingCurrentLocation || airport.iata != originIata) {
                        selectDestination(airport)
                    }
                },
                isSandboxMode = isSandboxMode,
                isSettingCurrentLocation = isSettingCurrentLocation,
                onConfirm = {
                    if (!isSettingCurrentLocation && selectedDestination?.iata == originIata) {
                        viewModel.showSameAirportDialog()
                    } else {
                        if (isSandboxMode) {
                            onSandboxAirportSelected(originAirport, selectedDestination)
                        } else if (isSettingCurrentLocation) {
                            selectedDestination?.let(onCurrentLocationSet)
                        } else {
                            onNavigateToBoardingPass()
                        }
                    }
                }
            )

            if (showSameAirportDialog) {
                SameAirportDialog(onDismiss = { viewModel.hideSameAirportDialog() })
            }

            if (showQuickFlightDialog) {
                QuickFlightDialogCards(
                    context = context,
                    suggestions = quickFlightSuggestions,
                    unitSystem = unitSystem,
                    languageTag = languageTag,
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
    context: android.content.Context,
    airport: Airport,
    origin: Airport,
    isSelected: Boolean,
    isOrigin: Boolean,
    unitSystem: String,
    languageTag: String,
    showMetrics: Boolean = true,
    onClick: () -> Unit
) {
    val distanceKm = FlightUtils.calculateDistance(origin, airport)
    val distStr = FlightUtils.formatDistance(distanceKm, unitSystem)
    val durationMinutes = FlightUtils.estimateDurationMinutes(distanceKm)
    val durationStr = FlightUtils.formatDuration(context, durationMinutes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) FlightPrimary.copy(alpha = 0.15f) else Color.Transparent)
            .clickable(enabled = isSettingCurrentLocationClickable(showMetrics, isOrigin)) { onClick() }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = airport.localizedName(languageTag),
                    modifier = Modifier.weight(1f, fill = false),
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
                if (isOrigin && showMetrics) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = FlightPrimary, shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = stringResource(R.string.newflight_origin_badge),
                            color = FlightBlack,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = "${airport.localizedCity(languageTag)}, ${airport.country}",
                color = FlightGray,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (showMetrics) {
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
}

private fun isSettingCurrentLocationClickable(showMetrics: Boolean, isOrigin: Boolean): Boolean {
    return !showMetrics || !isOrigin
}

private fun visibleManualSelectionAirports(
    airports: List<Airport>,
    selectedAirport: Airport?,
    zoomBucket: Int
): List<Airport> {
    val maxMarkers = when {
        zoomBucket <= 4 -> NewFlightMaxMarkersZoomFar
        zoomBucket <= 6 -> NewFlightMaxMarkersZoomMid
        else -> NewFlightMaxMarkersZoomNear
    }
    val zoomScale = 2.0.pow(zoomBucket.toDouble().coerceAtLeast(0.0))
    val degreesPerPixel = 360.0 / (NewFlightTileSizePx * zoomScale)
    val horizontalThreshold = (degreesPerPixel * NewFlightManualSelectionMarkerWidthPx).coerceAtLeast(0.008)
    val verticalThreshold = (degreesPerPixel * NewFlightManualSelectionMarkerHeightPx).coerceAtLeast(0.006)
    val kept = mutableListOf<Airport>()

    airports
        .sortedBy { it.longitude }
        .forEach { airport ->
            val overlappingIndex = kept.indexOfFirst { keptAirport ->
                kotlin.math.abs(airport.longitude - keptAirport.longitude) <= horizontalThreshold &&
                    kotlin.math.abs(airport.latitude - keptAirport.latitude) <= verticalThreshold
            }
            when {
                overlappingIndex == -1 -> {
                    kept += airport
                }
                airport.iata == selectedAirport?.iata -> {
                    kept[overlappingIndex] = airport
                }
            }
        }

    if (kept.size <= maxMarkers) {
        return kept
    }

    val selectedIata = selectedAirport?.iata
    val limited = kept.take(maxMarkers).toMutableList()
    if (selectedIata != null && limited.none { it.iata == selectedIata }) {
        kept.firstOrNull { it.iata == selectedIata }?.let { selected ->
            limited[limited.lastIndex] = selected
        }
    }
    return limited
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
    context: android.content.Context,
    suggestions: List<QuickFlightSuggestion>,
    unitSystem: String,
    languageTag: String,
    onDismiss: () -> Unit,
    onSelect: (Airport) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.newflight_quick_flight_title), color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    quickFlightRecommendationLabel(suggestion.targetMinutes),
                                    color = FlightPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${suggestion.airport.localizedName(languageTag)} (${suggestion.airport.iata})",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${suggestion.airport.localizedCity(languageTag)}, ${suggestion.airport.country}",
                                    color = FlightGray,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "${FlightUtils.formatDistance(suggestion.distanceKm, unitSystem)}  |  ${FlightUtils.formatDuration(context, suggestion.durationMinutes)}",
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
    context: android.content.Context,
    selectedDestination: Airport?,
    originAirport: Airport,
    originIata: String,
    unitSystem: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchRadiusKm: Int,
    onSearchRadiusChange: (Int) -> Unit,
    displayedAirports: List<Airport>,
    languageTag: String,
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
                        stringResource(R.string.newflight_selected_destination_format, it.localizedCity(languageTag), it.iata)
                    } ?: if (isSettingCurrentLocation) {
                        stringResource(R.string.newflight_select_current_location_airport)
                    } else {
                        stringResource(R.string.newflight_select_airport_on_map)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (selectedDestination != null && !isSettingCurrentLocation) {
                    val distKm = FlightUtils.calculateDistance(originAirport, selectedDestination)
                    Text(
                        text = stringResource(
                            R.string.newflight_distance_duration_format,
                            FlightUtils.formatDistance(distKm, unitSystem),
                            FlightUtils.formatDuration(context, FlightUtils.estimateDurationMinutes(distKm))
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = FlightPrimary,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = if (isSettingCurrentLocation) {
                            stringResource(R.string.newflight_search_and_select_airport)
                        } else {
                            stringResource(R.string.newflight_move_map_hint)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!isSettingCurrentLocation) {
                val radiusText = if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) stringResource(R.string.newflight_unlimited) else "${searchRadiusKm}km"
                val durationText = if (searchRadiusKm >= NewFlightUnlimitedRadiusKm) "" else "  |  ${FlightUtils.formatDuration(context, FlightUtils.estimateDurationMinutes(searchRadiusKm.toDouble()))}"
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
            }

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

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier.padding(top = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayedAirports) { airport ->
                    AirportListItem(
                        context = context,
                        airport = airport,
                        origin = originAirport,
                        isSelected = airport == selectedDestination,
                        isOrigin = airport.iata == originIata,
                        unitSystem = unitSystem,
                        languageTag = languageTag,
                        showMetrics = !isSettingCurrentLocation,
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
    context: android.content.Context,
    suggestions: List<QuickFlightSuggestion>,
    unitSystem: String,
    languageTag: String,
    onDismiss: () -> Unit,
    onSelect: (Airport) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.newflight_quick_flight_title), color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                quickFlightRecommendationLabel(suggestion.targetMinutes),
                                color = FlightPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${suggestion.airport.localizedName(languageTag)} (${suggestion.airport.iata})",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${suggestion.airport.localizedCity(languageTag)}, ${suggestion.airport.country}",
                                color = FlightGray,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "${FlightUtils.formatDistance(suggestion.distanceKm, unitSystem)} • ${FlightUtils.formatDuration(context, suggestion.durationMinutes)}",
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

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.openflight4and.data.AppRepository
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
    val scope = rememberCoroutineScope()
    val repository = remember { AppRepository(context) }
    
    // 1. Data Loading
    val allAirports = remember { repository.getAirports() }
    val originIata = currentDraft.origin.iata
    val originAirport = currentDraft.origin
    
    // 2. State
    var selectedDestination by remember { mutableStateOf(currentDraft.destination) }
    var searchRadiusKm by remember { mutableIntStateOf(1000) }
    var showSameAirportDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
    // 카메라 상태 (초기 위치: 출발 공항)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(originAirport.latitude, originAirport.longitude), 5f)
    }

    // 3. Derived Data (정렬된 리스트)
    val mapCenter = cameraPositionState.position.target
    val sortedAirports = remember(mapCenter, searchRadiusKm, allAirports, searchQuery) {
        val query = searchQuery.trim().lowercase()
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
                    val matchesSearch = 
                        airport.iata.lowercase().contains(query) ||
                        airport.cityKo.lowercase().contains(query) ||
                        airport.cityEn.lowercase().contains(query) ||
                        airport.nameKo.lowercase().contains(query) ||
                        airport.nameEn.lowercase().contains(query) ||
                        airport.country.lowercase().contains(query)
                    
                    if (!matchesSearch) return@filter false
                }
                
                // 2. 거리 필터
                if (searchRadiusKm >= 20000) {
                    // 무제한: 모든 공항 표시
                    return@filter true
                }
                
                // 출발지는 항상 표시
                if (airport.iata == originIata) return@filter true
                
                // 300km 이하에서는 테두리 필터링 끄고 일반 필터링
                if (searchRadiusKm <= 300) {
                    distFromOrigin <= searchRadiusKm.toDouble()
                } else {
                    // 테두리 안쪽만 표시 (테두리부터 -300km 구간)
                    val margin = 300.0 // 테두리 마진 (km)
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

    // Map Snapping
    LaunchedEffect(cameraPositionState.isMoving) {
        if (!cameraPositionState.isMoving) {
            val center = cameraPositionState.position.target
            val closest = allAirports
                .filter { it.iata != originIata }
                .minByOrNull { 
                    FlightUtils.calculateDistance(center.latitude, center.longitude, it.latitude, it.longitude)
                }
            
            closest?.let {
                val dist = FlightUtils.calculateDistance(center.latitude, center.longitude, it.latitude, it.longitude)
                if (dist < 300) { 
                     selectedDestination = it
                     onAirportSelected(it)
                }
            }
        }
    }

    // Zoom Sync
    LaunchedEffect(searchRadiusKm) {
        val zoom = when {
            searchRadiusKm >= 20000 -> 2f
            searchRadiusKm >= 10000 -> 3f
            searchRadiusKm >= 5000 -> 4f
            searchRadiusKm >= 2000 -> 5f
            searchRadiusKm >= 1000 -> 6f
            searchRadiusKm >= 500 -> 7f
            searchRadiusKm < 500 -> 8f
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
        sheetPeekHeight = 220.dp,
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
                        text = selectedDestination?.let { "도착: ${it.cityKo} (${it.iata})" } ?: "지도에서 공항 선택",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    if (selectedDestination != null) {
                        val distKm = FlightUtils.calculateDistance(originAirport, selectedDestination!!)
                        Text(
                            text = "${FlightUtils.formatDistance(distKm, unitSystem)} • 약 ${FlightUtils.formatDuration(FlightUtils.estimateDurationMinutes(distKm))}",
                            style = MaterialTheme.typography.titleLarge,
                            color = FlightPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = "지도를 움직이거나 아래 목록에서 선택하세요",
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
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        placeholder = { Text("공항 검색 (IATA, 도시, 공항명)", color = FlightGray) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = FlightGray)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "검색 초기화", tint = FlightGray)
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

                    val radiusText = if (searchRadiusKm >= 20000) "무제한" else "${searchRadiusKm}km"
                    val durationText = if (searchRadiusKm >= 20000) "" else " • 약 ${FlightUtils.formatDuration(FlightUtils.estimateDurationMinutes(searchRadiusKm.toDouble()))}"
                    Text(
                        text = "검색 반경: $radiusText$durationText",
                        color = FlightPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                    RulerPicker(
                        initialValue = searchRadiusKm,
                        minRequest = 100,
                        maxRequest = 20000,
                        step = 100,
                        onValueChange = { searchRadiusKm = it }
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
                                    selectedDestination = airport
                                    onAirportSelected(airport)
                                    scope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLng(LatLng(airport.latitude, airport.longitude))
                                        )
                                    }
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
                            showSameAirportDialog = true
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
                            isSettingCurrentLocation -> "현재 위치로 설정"
                            isSandboxMode -> "공항 선택 완료"
                            else -> "목적지 확정"
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
                if (searchRadiusKm < 20000) {
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
                            val label = "출발"
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
                    if (searchRadiusKm >= 20000) {
                        val distFromOrigin = FlightUtils.calculateDistance(originAirport, airport)
                        val isSelected = airport == selectedDestination
                        val markerIcon = remember(airport.iata, isSelected, isOrigin) {
                            val label = if (isSelected) "도착" else airport.cityKo.split("/")[0]
                            MapBitmapUtils.createCustomMarkerBitmap(context, airport.iata, label, isSelected || isOrigin)
                        }
                        Marker(
                            state = MarkerState(position = LatLng(airport.latitude, airport.longitude)),
                            icon = markerIcon,
                            zIndex = if (isSelected) 2f else 1f,
                            onClick = {
                                selectedDestination = airport
                                onAirportSelected(airport)
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLng(LatLng(airport.latitude, airport.longitude))
                                    )
                                }
                                true
                            }
                        )
                        return@forEach
                    }
                    
                    // 300km 이하에서는 테두리 필터링 끄고 일반 필터링
                    if (searchRadiusKm <= 300) {
                        val distFromOrigin = FlightUtils.calculateDistance(originAirport, airport)
                        if (distFromOrigin <= searchRadiusKm.toDouble()) {
                            // 마커 표시
                            val isSelected = airport == selectedDestination
                            val markerIcon = remember(airport.iata, isSelected, isOrigin) {
                                val label = if (isSelected) "도착" else airport.cityKo.split("/")[0]
                                MapBitmapUtils.createCustomMarkerBitmap(context, airport.iata, label, isSelected || isOrigin)
                            }
                            Marker(
                                state = MarkerState(position = LatLng(airport.latitude, airport.longitude)),
                                icon = markerIcon,
                                zIndex = if (isSelected) 2f else 1f,
                                onClick = {
                                    selectedDestination = airport
                                    onAirportSelected(airport)
                                    scope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLng(LatLng(airport.latitude, airport.longitude))
                                        )
                                    }
                                    true
                                }
                            )
                        }
                    } else {
                        // 테두리 안쪽만 표시 (테두리부터 -300km 구간)
                        val distFromOrigin = FlightUtils.calculateDistance(originAirport, airport)
                        val margin = 300.0
                        val lowerBound = (searchRadiusKm - margin).coerceAtLeast(0.0)
                        val upperBound = searchRadiusKm.toDouble()

                        if (distFromOrigin >= lowerBound && distFromOrigin <= upperBound) {
                            // 마커 표시
                            val isSelected = airport == selectedDestination
                            val markerIcon = remember(airport.iata, isSelected, isOrigin) {
                                val label = if (isSelected) "도착" else airport.cityKo.split("/")[0]
                                MapBitmapUtils.createCustomMarkerBitmap(context, airport.iata, label, isSelected || isOrigin)
                            }
                            Marker(
                                state = MarkerState(position = LatLng(airport.latitude, airport.longitude)),
                                icon = markerIcon,
                                zIndex = if (isSelected) 2f else 1f,
                                onClick = {
                                    selectedDestination = airport
                                    onAirportSelected(airport)
                                    scope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLng(LatLng(airport.latitude, airport.longitude))
                                        )
                                    }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            // Same Airport Dialog
            if (showSameAirportDialog) {
                SameAirportDialog(onDismiss = { showSameAirportDialog = false })
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
                        Text(text = "출발", color = FlightBlack, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
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
        title = { Text("목적지 오류", color = Color.White) },
        text = { Text("출발지와 목적지가 같습니다.\n다른 공항를 선택해주세요.", color = FlightGray) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인") }
        },
        containerColor = Color(0xFF0D0000)
    )
}

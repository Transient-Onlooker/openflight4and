package com.example.openflight4and.ui.inflight

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.FlightDraft
import com.example.openflight4and.model.FlightSession
import com.example.openflight4and.service.FlightService
import com.example.openflight4and.service.FlightStatusManager
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.components.RealFlightMap
import com.example.openflight4and.ui.components.rememberMapOverlayPalette
import com.example.openflight4and.ui.theme.FlightBlack
import com.example.openflight4and.ui.theme.FlightGray
import com.example.openflight4and.ui.theme.FlightPrimary
import com.example.openflight4and.utils.FlightUtils
import com.example.openflight4and.utils.MapBitmapUtils
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

private const val TrackingTiltDegrees = 60f
private const val TrackingZoom = 16f
private const val MinTickDelayMillis = 100L

/**
 * 대권 보간 (Great Circle Interpolation)
 * 지구 곡면을 따른 두 지점 사이의 보간
 */
fun interpolateGreatCircle(start: LatLng, end: LatLng, fraction: Double): LatLng {
    if (fraction == 0.0) return start
    if (fraction == 1.0) return end
    
    // 위도, 경도를 라디안으로 변환
    val lat1 = Math.toRadians(start.latitude)
    val lon1 = Math.toRadians(start.longitude)
    val lat2 = Math.toRadians(end.latitude)
    val lon2 = Math.toRadians(end.longitude)
    
    // 두 지점 사이의 각거리 (angular distance)
    val dLat = lat2 - lat1
    val dLon = lon2 - lon1
    
    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    
    // fraction 이 0 이거나 1 이면 시작/도착 지점 반환
    if (c == 0.0) return start
    
    // 보간 계수 계산
    val A = sin((1 - fraction) * c) / sin(c)
    val B = sin(fraction * c) / sin(c)
    
    // 직교 좌표계로 변환
    val x = A * cos(lat1) * cos(lon1) + B * cos(lat2) * cos(lon2)
    val y = A * cos(lat1) * sin(lon1) + B * cos(lat2) * sin(lon2)
    val z = A * sin(lat1) + B * sin(lat2)
    
    // 위도, 경도로 다시 변환
    val latN = atan2(z, sqrt(x.pow(2) + y.pow(2)))
    val lonN = atan2(y, x)
    
    return LatLng(Math.toDegrees(latN), Math.toDegrees(lonN))
}

@Composable
fun InFlightScreen(
    draft: FlightDraft,
    onFlightEnd: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()
    val mapStyle by repository.mapStyle.collectAsState(initial = "standard")
    val mapOverlayStyle by repository.mapOverlayStyle.collectAsState(initial = "dark")
    val debugFlightMode by repository.debugFlightMode.collectAsState(initial = false)
    val overlayPalette = rememberMapOverlayPalette(mapOverlayStyle)

    // 총 비행 시간 (초)
    val totalSeconds = (draft.estimatedMinutes * 60).toLong()

    // UI 상태: 경과 시간 (초)
    var secondsElapsed by remember { mutableStateOf(0L) }
    var isServiceSynced by remember { mutableStateOf(false) }
    var ticketCharged by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var debugSliderSeconds by remember { mutableFloatStateOf(0f) }
    var isDebugSliderDirty by remember { mutableStateOf(false) }
    var lastDebugSliderInteractionAt by remember { mutableStateOf(0L) }

    // 계산된 상태
    val progress = if (totalSeconds > 0) (secondsElapsed.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f
    val remainingSeconds = (totalSeconds - secondsElapsed).coerceAtLeast(0)
    val isFlying = secondsElapsed < totalSeconds
    val localTickDelayMillis = remember(draft.timeScale) {
        (1000f / draft.timeScale.coerceIn(0.001f, 1000f)).toLong().coerceAtLeast(MinTickDelayMillis)
    }

    // 뒤로가기 다이얼로그 상태
    var showGiveUpDialog by remember { mutableStateOf(false) }

    // 뒤로가기 버튼 핸들러
    BackHandler {
        showGiveUpDialog = true
    }

    fun pauseFlight() {
        context.startService(
            Intent(context, FlightService::class.java).apply {
                action = FlightService.ACTION_PAUSE
            }
        )
        isPaused = true
        showGiveUpDialog = false
    }

    fun resumeFlight() {
        context.startService(
            Intent(context, FlightService::class.java).apply {
                action = FlightService.ACTION_RESUME
            }
        )
        isPaused = false
    }

    fun stopFlight() {
        context.startService(
            Intent(context, FlightService::class.java).apply {
                action = FlightService.ACTION_STOP
            }
        )
        FlightStatusManager.stopFlight()
        isPaused = false
        showGiveUpDialog = false
        onFlightEnd()
    }

    // 앱 복귀 시 서비스 상태가 있으면 복구, 없으면 새로 시작
    LaunchedEffect(Unit) {
        if (FlightService.isServiceRunning()) {
            val synced = FlightStatusManager.syncFromService()
            if (synced) {
                secondsElapsed = FlightService.getSecondsElapsed()
                isServiceSynced = true
                ticketCharged = FlightService.isTicketCharged()
                isPaused = FlightService.isPaused()
                if (!isDebugSliderDirty) {
                    debugSliderSeconds = secondsElapsed.toFloat()
                }
            }
        } else {
            FlightStatusManager.startFlight(
                originIata = draft.origin.iata,
                destinationIata = draft.destination?.iata ?: "N/A",
                originName = draft.origin.nameKo,
                destinationName = draft.destination?.nameKo ?: "N/A",
                totalSeconds = totalSeconds
            )
            if (!isDebugSliderDirty) {
                debugSliderSeconds = 0f
            }
        }
    }

    // 서비스와 연결되지 않았을 때만 로컬 타이머를 증가시킴
    LaunchedEffect(isFlying, isServiceSynced) {
        if (isFlying && !isServiceSynced) {
            while (secondsElapsed < totalSeconds) {
                delay(localTickDelayMillis)
                if (FlightService.isServiceRunning()) {
                    val synced = FlightStatusManager.syncFromService()
                    if (synced) {
                        secondsElapsed = FlightService.getSecondsElapsed()
                        ticketCharged = FlightService.isTicketCharged()
                        isPaused = FlightService.isPaused()
                        isServiceSynced = true
                        if (!isDebugSliderDirty) {
                            debugSliderSeconds = secondsElapsed.toFloat()
                        }
                        break
                    }
                }
                secondsElapsed++
                // FlightStatusManager 와 동기화
                FlightStatusManager.updateProgress(secondsElapsed)
                if (!isDebugSliderDirty) {
                    debugSliderSeconds = secondsElapsed.toFloat()
                }
            }
        } else if (isFlying && isServiceSynced) {
            while (secondsElapsed < totalSeconds) {
                delay(localTickDelayMillis)
                isPaused = FlightService.isPaused()
                if (isPaused) {
                    continue
                }
                val serviceElapsed = FlightService.getSecondsElapsed()
                if (serviceElapsed > secondsElapsed) {
                    secondsElapsed = serviceElapsed
                }
                FlightStatusManager.updateProgress(secondsElapsed)
                if (!isDebugSliderDirty) {
                    debugSliderSeconds = secondsElapsed.toFloat()
                }
            }
        }
    }

    LaunchedEffect(isDebugSliderDirty, lastDebugSliderInteractionAt) {
        if (!isDebugSliderDirty) {
            return@LaunchedEffect
        }

        val interactionAt = lastDebugSliderInteractionAt
        delay(10_000)
        if (isDebugSliderDirty && lastDebugSliderInteractionAt == interactionAt) {
            debugSliderSeconds = secondsElapsed.toFloat()
            isDebugSliderDirty = false
        }
    }

    LaunchedEffect(secondsElapsed, totalSeconds, ticketCharged) {
        if (totalSeconds < 600 || ticketCharged || secondsElapsed < 600) {
            return@LaunchedEffect
        }

        val spendResult = repository.consumeTicketForLongFlight()
        if (spendResult.success) {
            ticketCharged = true
            FlightService.markTicketCharged()
            Toast.makeText(context, "이용권이 차감되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 비행기 위치 및 회전 계산 (대권 보간 - Great Circle Interpolation)
    fun applyDebugElapsed(targetSeconds: Long) {
        val clampedSeconds = targetSeconds.coerceIn(0L, totalSeconds)
        secondsElapsed = clampedSeconds
        debugSliderSeconds = clampedSeconds.toFloat()
        isDebugSliderDirty = false
        FlightStatusManager.updateProgress(clampedSeconds)
        if (FlightService.isServiceRunning()) {
            FlightService.jumpToElapsedSeconds(clampedSeconds)
        }
    }

    fun formatTimeScale(scale: Float): String {
        return when {
            scale >= 100f -> "${scale.toInt()}x"
            scale >= 10f -> "${"%.1f".format(scale)}x"
            scale >= 1f -> "${"%.2f".format(scale)}x"
            else -> "${"%.3f".format(scale)}x"
        }
    }

    val origin = draft.origin.location
    val dest = draft.destination?.location ?: origin
    val currentPos = remember(progress) {
        // 대권 보간: 지구 곡면을 따른 최단 거리 경로
        interpolateGreatCircle(origin, dest, progress.toDouble())
    }
    val bearing = remember { FlightUtils.calculateBearing(draft.origin, draft.destination ?: draft.origin) }
    val planeMarker = remember { MapBitmapUtils.createPlaneMarkerBitmap(context) }

    // 카메라 추적 상태
    var isCameraTracking by remember { mutableStateOf(true) }
    val cameraPositionState = rememberCameraPositionState()

    // 초기 카메라 위치 설정 (출발지 и 도착지를 모두 포함하는 줌 레벨)
    LaunchedEffect(Unit) {
        // LatLngBounds 계산하여 경로가 화면에 꽉 차도록 설정
        val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
        boundsBuilder.include(origin)
        boundsBuilder.include(dest)
        val bounds = boundsBuilder.build()
        
        // 화면 패딩을 고려한 줌 레벨 계산
        val padding = 200 // 픽셀 단위 패딩
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngBounds(bounds, padding)
        )
    }

    // 카메라 추적 로직 (사용자가 직접 조작했을 때는 추적 중지)
    LaunchedEffect(currentPos, isCameraTracking) {
        if (isCameraTracking && !cameraPositionState.isMoving) {
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(currentPos)
                        .zoom(max(cameraPositionState.position.zoom, TrackingZoom))
                        .tilt(TrackingTiltDegrees)
                        .bearing(bearing)
                        .build()
                )
            )
        }
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            isCameraTracking = false
        }
    }

    // 비행 완료 감지 및 저장
    LaunchedEffect(secondsElapsed, totalSeconds) {
        if (secondsElapsed >= totalSeconds && totalSeconds > 0) {
            saveAndExit(repository, draft, 1f, true, System.currentTimeMillis() - (draft.estimatedMinutes * 60 * 1000), onFlightEnd, context)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        RealFlightMap(
            cameraPositionState = cameraPositionState,
            mapStyle = mapStyle,
            isInteractive = true,
            useDarkOverlay = false,
            mapContent = {
                // 지도 콘텐츠 (GoogleMap 내부에서 렌더링)
                Polyline(
                    points = listOf(origin, dest),
                    color = Color.White.copy(alpha = 0.6f),
                    width = 5f,
                    geodesic = true // 대원 곡선 (현실적 비행 경로)
                )
                Marker(
                    state = MarkerState(position = currentPos),
                    icon = planeMarker,
                    rotation = bearing,
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                )
            },
            overlayContent = {
                // UI Overlay (Canvas 오버레이 위에 렌더링)
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp).systemBarsPadding(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    GlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = overlayPalette.panelBackground,
                        borderColor = overlayPalette.panelBorder
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("비행 중", color = overlayPalette.accentText, fontSize = 12.sp)
                                    Text(draft.flightNumber, color = overlayPalette.primaryText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("남은 시간", color = overlayPalette.secondaryText, fontSize = 12.sp)
                                    Text(FlightUtils.formatTimer(remainingSeconds), color = overlayPalette.primaryText, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = overlayPalette.divider)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(draft.origin.iata, color = overlayPalette.secondaryText, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.Flight, contentDescription = null, tint = overlayPalette.iconTint, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(draft.destination?.iata ?: "---", color = overlayPalette.primaryText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SmallFloatingActionButton(
                            onClick = {
                                isCameraTracking = true
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder()
                                                .target(currentPos)
                                                .zoom(max(cameraPositionState.position.zoom, TrackingZoom))
                                                .tilt(TrackingTiltDegrees)
                                                .bearing(bearing)
                                                .build()
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.align(Alignment.End),
                            containerColor = if (isCameraTracking) MaterialTheme.colorScheme.primary else overlayPalette.floatingButtonContainer,
                            contentColor = if (isCameraTracking) MaterialTheme.colorScheme.onPrimary else overlayPalette.floatingButtonContent
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null)
                        }

                        if (debugFlightMode) {
                            GlassPanel(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = overlayPalette.panelBackground,
                                borderColor = overlayPalette.panelBorder
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Flight Debug", color = overlayPalette.primaryText, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Elapsed: ${FlightUtils.formatTimer(debugSliderSeconds.toLong())}",
                                        color = overlayPalette.secondaryText,
                                        fontSize = 12.sp
                                    )
                                    Slider(
                                        value = debugSliderSeconds,
                                        onValueChange = {
                                            debugSliderSeconds = it
                                            isDebugSliderDirty = true
                                            lastDebugSliderInteractionAt = System.currentTimeMillis()
                                        },
                                        valueRange = 0f..totalSeconds.toFloat()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { applyDebugElapsed(590L) },
                                            modifier = Modifier.weight(1f),
                                            enabled = totalSeconds >= 600,
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = overlayPalette.primaryText)
                                        ) {
                                            Text("9:50")
                                        }
                                        OutlinedButton(
                                            onClick = { applyDebugElapsed(totalSeconds / 2) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = overlayPalette.primaryText)
                                        ) {
                                            Text("50%")
                                        }
                                        Button(
                                            onClick = { applyDebugElapsed(debugSliderSeconds.toLong()) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Apply")
                                        }
                                    }
                                }
                            }
                        }

                        GlassPanel(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = overlayPalette.panelBackground,
                            borderColor = overlayPalette.panelBorder
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = overlayPalette.trackColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("${(progress * 100).toInt()}% 비행 완료", color = overlayPalette.secondaryText, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                                
                                // 자유 모드일 때 시간 배율 표시
                                if (draft.timeScale != 1f) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("시간 배율: ${formatTimeScale(draft.timeScale)}", color = overlayPalette.accentText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { pauseFlight() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = overlayPalette.primaryText)
                        ) {
                            Text("일시정지")
                        }

                        PrimaryFlightButton(
                            text = "여정 중단",
                            onClick = { showGiveUpDialog = true },
                            isDestructive = true
                        )
                    }
                }
            }
        )

        if (isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1F1F1F))
            ) {
                GlassPanel(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    backgroundColor = overlayPalette.panelBackground,
                    borderColor = overlayPalette.panelBorder
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("일시중지되었습니다.", color = overlayPalette.primaryText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(
                            "비행중에 잠깐의 휴식을 가지는 것도 좋죠.",
                            color = overlayPalette.secondaryText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = { resumeFlight() }) {
                            Text("비행 재개")
                        }
                    }
                }
            }
        }
    }

    // 비행 포기 다이얼로그
    if (showGiveUpDialog) {
        AlertDialog(
            onDismissRequest = { showGiveUpDialog = false },
            title = { Text("비행 포기", color = Color.White) },
            text = { Text("현재 비행을 포기하시겠습니까?\n기록이 저장되지 않습니다.", color = FlightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        stopFlight()
                    }
                ) { Text("포기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showGiveUpDialog = false }) { Text("계속 비행") }
            },
            containerColor = Color(0xFF0D0000)
        )
    }
}

private suspend fun saveAndExit(
    repository: AppRepository,
    draft: FlightDraft,
    progress: Float,
    isCompleted: Boolean,
    startTime: Long,
    onExit: () -> Unit,
    context: android.content.Context
) {
    val session = FlightSession(
        flightNumber = draft.flightNumber,
        originIata = draft.origin.iata,
        originName = draft.origin.nameKo,
        destinationIata = draft.destination?.iata ?: "Unknown",
        destinationName = draft.destination?.nameKo ?: "Unknown",
        seatNumber = draft.seatNumber,
        focusCategory = draft.focusCategory,
        distanceKm = (draft.distanceKm * progress).toInt(),
        durationMinutes = ((System.currentTimeMillis() - startTime) / 1000 / 60).toInt(),
        startTime = startTime,
        endTime = System.currentTimeMillis(),
        isCompleted = isCompleted
    )
    repository.saveSession(session)
    onExit()
}

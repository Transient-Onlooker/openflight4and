package com.example.openflight4and.ui.inflight

import android.content.Intent
import android.location.Location
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
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
private const val MinTickDelayMillis = 100L
private const val Perspective2D = "2d"
private const val Perspective2_5D = "2_5d"
private const val GreatCircleBearingStep = 0.001
private const val GreatCircleRouteSegments = 256

/**
 * ??????????⑤벡瑜?????(Great Circle Interpolation)
 * ????븐뼐???????????????????????곕츥??????????????녳븢????????븐뼐???????????????????⑤벡瑜?????
 */
fun interpolateGreatCircle(start: LatLng, end: LatLng, fraction: Double): LatLng {
    if (fraction == 0.0) return start
    if (fraction == 1.0) return end
    
    // ??????袁⑸즴筌?씛彛??? ??癲됱빖???嶺???????椰꾧퀡逾???????ｋ즲??????????????????롮쾸?椰?嚥▲굧???븍툖?????????⑤벡瑜????
    val lat1 = Math.toRadians(start.latitude)
    val lon1 = Math.toRadians(start.longitude)
    val lat2 = Math.toRadians(end.latitude)
    val lon2 = Math.toRadians(end.longitude)
    
    // ??????븐뼐????????????????????ル??????????꿔꺂?????(angular distance)
    val dLat = lat2 - lat1
    val dLon = lon2 - lon1
    
    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    
    // fraction ??0 ?????????1 ????????耀붾굝?????傭?끆????椰???????袁⑸즴筌?씛彛???돗????⑸뻿??????븐뼐?????????????獄쏅챶留덌┼???????
    if (c == 0.0) return start
    
    // ??????⑤벡瑜?????????????????????????????
    val A = sin((1 - fraction) * c) / sin(c)
    val B = sin(fraction * c) / sin(c)
    
    // ????븐뼐?????????????亦껋꼦維??????????????????????몃┛???????⑤벡瑜????
    val x = A * cos(lat1) * cos(lon1) + B * cos(lat2) * cos(lon2)
    val y = A * cos(lat1) * sin(lon1) + B * cos(lat2) * sin(lon2)
    val z = A * sin(lat1) + B * sin(lat2)
    
    // ??????袁⑸즴筌?씛彛??? ??癲됱빖???嶺???????椰꾧퀡逾??┑?λ쳹???怨멸텛?????????????袁④뎬????????⑤벡瑜????
    val latN = atan2(z, sqrt(x.pow(2) + y.pow(2)))
    val lonN = atan2(y, x)
    
    return LatLng(Math.toDegrees(latN), Math.toDegrees(lonN))
}

fun calculateGreatCircleBearing(start: LatLng, end: LatLng, fraction: Double): Float {
    val clampedFraction = fraction.coerceIn(0.0, 1.0)
    val fromFraction = (clampedFraction - GreatCircleBearingStep).coerceAtLeast(0.0)
    val toFraction = (clampedFraction + GreatCircleBearingStep).coerceAtMost(1.0)
    val fromPoint = interpolateGreatCircle(start, end, fromFraction)
    val toPoint = interpolateGreatCircle(start, end, toFraction)

    val fromLocation = Location("great_circle_from").apply {
        latitude = fromPoint.latitude
        longitude = fromPoint.longitude
    }
    val toLocation = Location("great_circle_to").apply {
        latitude = toPoint.latitude
        longitude = toPoint.longitude
    }

    return fromLocation.bearingTo(toLocation)
}

fun planeMarkerRotationForBearing(bearing: Float): Float {
    // ic_flight_marker is drawn facing north already, which matches Google Maps marker
    // rotation semantics where 0 degrees points north.
    return ((bearing % 360f) + 360f) % 360f
}

fun buildGreatCircleRoutePoints(start: LatLng, end: LatLng, segments: Int = GreatCircleRouteSegments): List<LatLng> {
    if (segments <= 1) return listOf(start, end)
    return List(segments + 1) { index ->
        interpolateGreatCircle(start, end, index.toDouble() / segments.toDouble())
    }
}

@Composable
fun InFlightScreen(
    draft: FlightDraft,
    onFlightEnd: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val repository = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()
    val mapStyle by repository.mapStyle.collectAsState(initial = "standard")
    val mapOverlayStyle by repository.mapOverlayStyle.collectAsState(initial = "dark")
    val mapPerspective by repository.mapPerspective.collectAsState(initial = Perspective2_5D)
    val debugFlightMode by repository.debugFlightMode.collectAsState(initial = false)
    val overlayPalette = rememberMapOverlayPalette(mapOverlayStyle)
    val inflightPanelBackground = Color.White.copy(alpha = 0.5f)
    val inflightPanelBorder = Color.Black.copy(alpha = 0.18f)
    val inflightPrimaryText = Color.Black
    val inflightSecondaryText = Color.Black.copy(alpha = 0.72f)
    val inflightAccentText = Color.Black
    val inflightDivider = Color.Black.copy(alpha = 0.12f)
    val inflightTrackColor = Color.Black.copy(alpha = 0.16f)

    // ???????轅붽틓???????????(??
    val totalSeconds = (draft.estimatedMinutes * 60).toLong()

    // UI ??????椰???? ??癲됱빖???嶺??????????(??
    var secondsElapsed by remember { mutableStateOf(0L) }
    var isServiceSynced by remember { mutableStateOf(false) }
    var ticketCharged by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var showAdRewardDialog by remember { mutableStateOf(false) }
    var isAdRewardRunning by remember { mutableStateOf(false) }
    var adRewardSecondsRemaining by remember { mutableIntStateOf(30) }
    var debugSliderSeconds by remember { mutableFloatStateOf(0f) }
    var isDebugSliderDirty by remember { mutableStateOf(false) }
    var lastDebugSliderInteractionAt by remember { mutableStateOf(0L) }

    // ???????????????????椰????
    val localTickDelayMillis = remember(draft.timeScale) {
        (1000f / draft.timeScale.coerceIn(0.001f, 1000f)).toLong().coerceAtLeast(MinTickDelayMillis)
    }
    val progress = if (totalSeconds > 0) (secondsElapsed.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f
    val smoothedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(
            durationMillis = localTickDelayMillis.coerceAtMost(1000L).toInt(),
            easing = LinearEasing
        ),
        label = "inflight_progress"
    )
    val remainingSeconds = (totalSeconds - secondsElapsed).coerceAtLeast(0)
    val isFlying = secondsElapsed < totalSeconds

    // ????겾??좊읈??????源낇꼧???⑥??????ㅺ컼??
    var showGiveUpDialog by remember { mutableStateOf(false) }

    // ??살쨮揶쎛疫?甕곌쑵???紐껊굶??
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

    DisposableEffect(Unit) {
        FlightService.setInFlightScreenVisible(true)
        onDispose {
            FlightService.setInFlightScreenVisible(false)
        }
    }

    // ????????⑤벡瑜??꿔꺂??????????썹땟??? ?????耀붾굝??????????嶺????????椰?????癲?濾곌풝源?????????쎛 ???????롮쾸?椰?嚥▲굧???븍툖??????????⑤벡瑜??꿔꺂??????????썹땟???? ?????????대첉??轅붽틓?????獒뺣폍???????????耀붾굝?????傭?끆????椰?
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

    // ???耀붾굝??????????嶺??? ????????산뭐????? ??????μ떜媛?걫?????????????????????????????傭?끆????ш끽維??????븐뼐????傭?끆?????Β?ｊ콞???癲??????
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
                // FlightStatusManager ?? ??????????                FlightStatusManager.updateProgress(secondsElapsed)
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

    LaunchedEffect(isAdRewardRunning) {
        if (!isAdRewardRunning) {
            return@LaunchedEffect
        }

        adRewardSecondsRemaining = 30
        while (adRewardSecondsRemaining > 0) {
            delay(1_000)
            adRewardSecondsRemaining--
        }

        repository.rewardSingleTicketFromInFlightAd()
        isAdRewardRunning = false
        Toast.makeText(context, "\uBE44\uD589\uAD8C 1\uAC1C\uAC00 \uC9C0\uAE09\uB418\uC5C8\uC2B5\uB2C8\uB2E4.", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(secondsElapsed, totalSeconds, ticketCharged) {
        if (totalSeconds < 600 || ticketCharged || secondsElapsed < 600) {
            return@LaunchedEffect
        }

        val spendResult = repository.consumeTicketForLongFlight()
        if (spendResult.success) {
            ticketCharged = true
            FlightService.markTicketCharged()
            Toast.makeText(context, "????????살몖?亦껋꼦維뽬뜎?????????遺얘턁????怨뚮옩?????椰????誘⑸쿋????????????", Toast.LENGTH_SHORT).show()
        }
    }

    // ?????轅붽틓??????饔낅떽???????????袁⑸즴筌?씛彛?????????????????????(??????????⑤벡瑜?????- Great Circle Interpolation)
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
    val currentPos = remember(smoothedProgress) {
        // ??????????⑤벡瑜????? ????븐뼐???????????????????????곕츥??????????????녳븢??????븐뼐??????????????븐뼐?곭춯?竊????????癲됱빖???嶺?????????
        interpolateGreatCircle(origin, dest, smoothedProgress.toDouble())
    }
    val bearing = remember(smoothedProgress, origin, dest) {
        calculateGreatCircleBearing(origin, dest, smoothedProgress.toDouble())
    }
    val routePoints = remember(origin, dest) {
        buildGreatCircleRoutePoints(origin, dest)
    }
    val trackingTilt = if (mapPerspective == Perspective2D) 0f else TrackingTiltDegrees
    val trackingBearing = if (mapPerspective == Perspective2D) 0f else bearing
    val planeRotation = remember(bearing) { planeMarkerRotationForBearing(bearing) }
    val planeMarker = remember { MapBitmapUtils.createPlaneMarkerBitmap(context) }

    // ?????筌뤾퍓愿???????????熬곣몿??????????椰????
    var isCameraTracking by rememberSaveable { mutableStateOf(true) }
    val cameraPositionState = rememberCameraPositionState()
    val zoomLabel by remember {
        derivedStateOf { "\uD654\uBA74 \uBC30\uC728: ${cameraPositionState.position.zoom.roundToInt()}x" }
    }
    suspend fun updateCameraPerspective(
        perspective: String,
        keepTrackingTarget: Boolean
    ) {
        val target = if (keepTrackingTarget) currentPos else cameraPositionState.position.target
        val updatedTilt = if (perspective == Perspective2D) 0f else TrackingTiltDegrees
        val updatedBearing = if (perspective == Perspective2D) 0f else bearing
        cameraPositionState.move(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(cameraPositionState.position)
                    .target(target)
                    .tilt(updatedTilt)
                    .bearing(updatedBearing)
                    .build()
            )
        )
    }

    // ??????嶺뚮∥?????????筌뤾퍓愿???????????袁⑸즴筌?씛彛?????????롮쾸?椰???(???????????????곗뵰??? ????????袁⑸즴筌?씛彛???돗????⑸뻿????????嚥싲갭큔?????????븐뼐???????????븐뼔???????????뀀맩鍮??????룸챶猷??????????????
    LaunchedEffect(Unit) {
        cameraPositionState.animate(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(currentPos)
                    .zoom(16f)
                    .tilt(trackingTilt)
                    .bearing(trackingBearing)
                    .build()
            )
        )
    }

    // ?????筌뤾퍓愿???????????熬곣몿???????????雅?퍔瑗?땟???(?????? ????븐뼐???????????????????????????롮쾸?椰??????????????熬곣몿??????????ш끽踰椰?????袁ㅻ쇀??)
    LaunchedEffect(currentPos, isCameraTracking, mapPerspective) {
        if (isCameraTracking && cameraPositionState.cameraMoveStartedReason != CameraMoveStartedReason.GESTURE) {
            val currentZoom = cameraPositionState.position.zoom.takeIf { it > 1f } ?: 16f
            cameraPositionState.move(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(currentPos)
                        .zoom(currentZoom)
                        .tilt(trackingTilt)
                        .bearing(trackingBearing)
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

    // ?????轅붽틓???????????袁⑸즴筌?씛彛?????????ル???? ??????
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
                // ????븐뼐??????????????꾩룆梨띰쭕??????(GoogleMap ????????????????
                Polyline(
                    points = routePoints,
                    color = Color.White.copy(alpha = 0.6f),
                    width = 5f,
                    geodesic = false
                )
                Marker(
                    state = MarkerState(position = currentPos),
                    icon = planeMarker,
                    rotation = planeRotation,
                    flat = true,
                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                )
            },
            overlayContent = {
                // UI Overlay (Canvas ????????源낆┸????????롮쾸?椰???⑤챷寃?┼???????袁⑸즴筌?씛彛?????????
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isLandscape) 16.dp else 24.dp)
                        .systemBarsPadding(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    GlassPanel(
                        modifier = if (isLandscape) {
                            Modifier.fillMaxWidth(0.52f)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        backgroundColor = inflightPanelBackground,
                        borderColor = inflightPanelBorder
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("\uBE44\uD589 \uC911", color = inflightAccentText, fontSize = 12.sp)
                                    Text(draft.flightNumber, color = inflightPrimaryText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("\uB0A8\uC740 \uC2DC\uAC04", color = inflightSecondaryText, fontSize = 12.sp)
                                    Text(FlightUtils.formatTimer(remainingSeconds), color = inflightPrimaryText, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                    if (draft.timeScale != 1f) {
                                        Text(
                                            "\uC2DC\uAC04 \uBC30\uC728: ${formatTimeScale(draft.timeScale)}",
                                            color = inflightSecondaryText,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = inflightDivider)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(draft.origin.iata, color = inflightSecondaryText, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.Flight, contentDescription = null, tint = inflightPrimaryText, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(draft.destination?.iata ?: "---", color = inflightPrimaryText, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    zoomLabel,
                                    color = inflightSecondaryText,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Column(
                        modifier = if (isLandscape) Modifier.fillMaxWidth(0.34f) else Modifier,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = if (isLandscape) Alignment.Start else Alignment.End
                    ) {
                        SmallFloatingActionButton(
                            onClick = { showAdRewardDialog = true },
                            modifier = Modifier.align(if (isLandscape) Alignment.Start else Alignment.End),
                            containerColor = overlayPalette.floatingButtonContainer,
                            contentColor = overlayPalette.floatingButtonContent
                        ) {
                            if (isAdRewardRunning) {
                                Text(
                                    text = "${adRewardSecondsRemaining}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else {
                                Icon(Icons.Default.ConfirmationNumber, contentDescription = null)
                            }
                        }

                        SmallFloatingActionButton(
                            onClick = {
                                scope.launch {
                                    val nextPerspective = if (mapPerspective == Perspective2D) {
                                        Perspective2_5D
                                    } else {
                                        Perspective2D
                                    }
                                    updateCameraPerspective(
                                        perspective = nextPerspective,
                                        keepTrackingTarget = isCameraTracking
                                    )
                                    repository.setMapPerspective(nextPerspective)
                                }
                            },
                            modifier = Modifier.align(if (isLandscape) Alignment.Start else Alignment.End),
                            containerColor = overlayPalette.floatingButtonContainer,
                            contentColor = overlayPalette.floatingButtonContent
                        ) {
                            Text(
                                text = if (mapPerspective == Perspective2D) "2D" else "2.5D",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        SmallFloatingActionButton(
                            onClick = {
                                isCameraTracking = true
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newCameraPosition(
                                            CameraPosition.Builder()
                                                .target(currentPos)
                                                .zoom(cameraPositionState.position.zoom)
                                                .tilt(trackingTilt)
                                                .bearing(trackingBearing)
                                                .build()
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.align(if (isLandscape) Alignment.Start else Alignment.End),
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
                            backgroundColor = inflightPanelBackground,
                            borderColor = inflightPanelBorder
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = inflightPrimaryText,
                                    trackColor = inflightTrackColor
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("${(progress * 100).toInt()}% \uBE44\uD589 \uC644\uB8CC", color = inflightSecondaryText, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

                                if (draft.timeScale != 1f) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("\uC2DC\uAC04 \uBC30\uC728: ${formatTimeScale(draft.timeScale)}", color = inflightPrimaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { pauseFlight() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.5f),
                                    contentColor = inflightPrimaryText
                                )
                            ) {
                                Text("\uC77C\uC2DC\uC815\uC9C0")
                            }

                            PrimaryFlightButton(
                                text = "\uC5EC\uC815 \uC911\uB2E8",
                                onClick = { showGiveUpDialog = true },
                                modifier = Modifier.weight(1f),
                                isDestructive = true
                            )
                        }
                    }
                }
            }
        )

        if (isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x331F1F1F))
            ) {
                GlassPanel(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    backgroundColor = Color.White,
                    borderColor = inflightPanelBorder
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("\uC77C\uC2DC\uC911\uC9C0\uB418\uC5C8\uC2B5\uB2C8\uB2E4.", color = inflightPrimaryText, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(
                            "\uBE44\uD589\uC911\uC5D0 \uC7A0\uAE50\uC758 \uD734\uC2DD\uC744 \uAC00\uC9C0\uB294 \uAC83\uB3C4 \uC88B\uC8E0.",
                            color = inflightSecondaryText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = { resumeFlight() }) {
                            Text("\uBE44\uD589 \uC7AC\uAC1C")
                        }
                    }
                }
            }
        }

        if (showAdRewardDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x331F1F1F))
            ) {
                GlassPanel(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    backgroundColor = Color.White,
                    borderColor = inflightPanelBorder
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "\uBE44\uD589\uAD8C \uD68D\uB4DD",
                            color = inflightPrimaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Text(
                            "30\uCD08 \uAD11\uACE0\uB97C \uBCF4\uACE0 \uD2F0\uCF13\uC744 1\uAC1C \uC5BB\uC73C\uC2DC\uACA0\uC2B5\uB2C8\uAE4C?\n(\uC9C4\uD589\uC911\uC774\uB358 \uBE44\uD589\uC740 \uAD11\uACE0 \uC7AC\uC0DD\uC911\uC5D0\uB3C4 \uC9C4\uD589\uB429\uB2C8\uB2E4)",
                            color = inflightSecondaryText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    showAdRewardDialog = false
                                    isAdRewardRunning = true
                                }
                            ) {
                                Text("\uAD11\uACE0 \uBCF4\uAE30")
                            }
                            OutlinedButton(
                                onClick = { showAdRewardDialog = false },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = inflightPrimaryText)
                            ) {
                                Text("\uC544\uB2C8\uC624")
                            }
                        }
                    }
                }
            }
        }

        if (isAdRewardRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x331F1F1F))
            ) {
                GlassPanel(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    backgroundColor = Color.White,
                    borderColor = inflightPanelBorder
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "\uAD11\uACE0 \uC7AC\uC0DD \uC911",
                            color = inflightPrimaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                        Text(
                            "${adRewardSecondsRemaining}\uCD08 \uB0A8\uC74C",
                            color = inflightSecondaryText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "\uBE44\uD589\uC740 \uACC4\uC18D \uC9C4\uD589\uB429\uB2C8\uB2E4.",
                            color = inflightSecondaryText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(
                            onClick = { isAdRewardRunning = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = inflightPrimaryText)
                        ) {
                            Text("\uCDE8\uC18C")
                        }
                    }
                }
            }
        }
    }

    if (showGiveUpDialog) {
        AlertDialog(
            onDismissRequest = { showGiveUpDialog = false },
            title = { Text("\uBE44\uD589 \uD3EC\uAE30", color = Color.White) },
            text = { Text("\uD604\uC7AC \uBE44\uD589\uC744 \uD3EC\uAE30\uD558\uC2DC\uACA0\uC2B5\uB2C8\uAE4C?\n\uAE30\uB85D\uC774 \uC800\uC7A5\uB418\uC9C0 \uC54A\uC2B5\uB2C8\uB2E4.", color = FlightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        stopFlight()
                    }
                ) { Text("\uD3EC\uAE30", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showGiveUpDialog = false }) { Text("\uACC4\uC18D \uBE44\uD589") }
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


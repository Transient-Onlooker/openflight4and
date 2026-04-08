package com.example.openflight4and.ui.inflight

import android.app.Application
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.openflight4and.BuildConfig
import com.example.openflight4and.R
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.FlightDraft
import com.example.openflight4and.model.FlightSession
import com.example.openflight4and.service.FlightService
import com.example.openflight4and.utils.FlightUtils
import com.example.openflight4and.utils.MapBitmapUtils
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps3d.GoogleMap3D
import com.google.android.gms.maps3d.Map3DOptions
import com.google.android.gms.maps3d.Map3DView
import com.google.android.gms.maps3d.OnMap3DViewReadyCallback
import com.google.android.gms.maps3d.model.Camera
import com.google.android.gms.maps3d.model.camera
import com.google.android.gms.maps3d.model.latLngAltitude
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.*

private const val TrackingTiltDegrees = 60f
private const val MinTickDelayMillis = 100L
private const val InFlightInitialZoom = 14f
private const val InFlightMinTrackedZoom = 2f
private const val InFlightAnimationDurationCapMillis = 1000L
private const val InFlightSingleSpeedFrameDelayMillis = 16L
private const val InFlightLowSpeedFrameDelayMillis = 24L
private const val InFlightDefaultFrameDelayMillis = 33L
private const val InFlightSingleSpeedCameraUpdateMillis = 48L
private const val InFlightLowSpeedCameraUpdateMillis = 90L
private const val InFlightMediumSpeedCameraUpdateMillis = 140L
private const val InFlightHighSpeedCameraUpdateMillis = 180L
private const val InFlightLowSpeedThreshold = 1f
private const val InFlightMediumSpeedThreshold = 3f
private const val InFlightHighSpeedThreshold = 10f
private const val InFlightPanelAlpha = 0.5f
private const val InFlightPanelBorderAlpha = 0.18f
private const val InFlightSecondaryTextAlpha = 0.72f
private const val InFlightDividerAlpha = 0.12f
private const val InFlightTrackAlpha = 0.16f
private const val Perspective2D = "2d"
private const val Perspective2_5D = "2_5d"
private const val Perspective3D = "3d"
private const val GreatCircleBearingStep = 0.001
private const val GreatCircleRouteSegments = 256
private const val Maps3DTag = "Map3DVRView"
private const val Maps3DTiltDegrees = 60.0
private const val Maps3DRangeMeters = 4500.0
private const val Maps3DAltitudeMeters = 1200.0
private const val Maps3DManualHeadingPerPixel = 0.18
private const val Maps3DManualTiltPerPixel = 0.12
private const val Maps3DMinTiltDegrees = 5.0
private const val Maps3DMaxTiltDegrees = 88.0
private const val Maps3DDragThresholdPixels = 0.5f

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

fun nextStandardMapPerspective(current: String): String {
    return if (current == Perspective2D) Perspective2_5D else Perspective2D
}

private fun tiltForPerspective(perspective: String): Float {
    return if (perspective == Perspective2D) 0f else TrackingTiltDegrees
}

private fun bearingForPerspective(perspective: String, flightBearing: Float): Float {
    return if (perspective == Perspective2D) 0f else flightBearing
}

@Composable
fun InFlightScreen(
    draft: FlightDraft,
    onNavigateToSettings: () -> Unit,
    onFlightEnd: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val inflightViewModel: InFlightViewModel = viewModel(
        factory = InFlightViewModel.Factory(context.applicationContext as Application)
    )
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val repository = remember(context) { AppRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val serviceRuntimeState by FlightService.runtimeState.collectAsState()
    val mapStyle by repository.mapStyle.collectAsState(initial = "standard")
    val mapOverlayStyle by repository.mapOverlayStyle.collectAsState(initial = "dark")
    var mapPerspective by remember { mutableStateOf(Perspective2_5D) }
    val debugFlightMode by repository.debugFlightMode.collectAsState(initial = false)
    val overlayPalette = rememberInFlightOverlayPalette(mapOverlayStyle)
    val inflightPanelBackground = Color.White.copy(alpha = InFlightPanelAlpha)
    val inflightPanelBorder = Color.Black.copy(alpha = InFlightPanelBorderAlpha)
    val inflightPrimaryText = Color.Black
    val inflightSecondaryText = Color.Black.copy(alpha = InFlightSecondaryTextAlpha)
    val inflightAccentText = Color.Black
    val inflightDivider = Color.Black.copy(alpha = InFlightDividerAlpha)
    val inflightTrackColor = Color.Black.copy(alpha = InFlightTrackAlpha)
    val panelColors = remember(
        inflightPanelBackground,
        inflightPanelBorder,
        inflightPrimaryText,
        inflightSecondaryText,
        inflightAccentText,
        inflightDivider,
        inflightTrackColor
    ) {
        InFlightPanelColors(
            panelBackground = inflightPanelBackground,
            panelBorder = inflightPanelBorder,
            primaryText = inflightPrimaryText,
            secondaryText = inflightSecondaryText,
            accentText = inflightAccentText,
            divider = inflightDivider,
            trackColor = inflightTrackColor
        )
    }

    var fallbackSecondsElapsed by remember { mutableStateOf(0L) }
    var fallbackTicketCharged by remember { mutableStateOf(false) }
    var fallbackIsPaused by remember { mutableStateOf(false) }
    val inflightUiState by inflightViewModel.uiState.collectAsState()
    var debugSliderSeconds by remember { mutableFloatStateOf(0f) }
    var isDebugSliderDirty by remember { mutableStateOf(false) }
    var lastDebugSliderInteractionAt by remember { mutableStateOf(0L) }
    var renderedElapsedSeconds by remember { mutableFloatStateOf(0f) }
    var animationStartElapsed by remember { mutableFloatStateOf(0f) }
    var animationTargetElapsed by remember { mutableFloatStateOf(0f) }
    var animationStartedAtMillis by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    val hasServiceRuntimeState = serviceRuntimeState.isRunning
    val totalSeconds = serviceRuntimeState.totalSeconds.takeIf { hasServiceRuntimeState && it > 0L }
        ?: (draft.estimatedMinutes * 60).toLong()
    val secondsElapsed = if (hasServiceRuntimeState) serviceRuntimeState.secondsElapsed else fallbackSecondsElapsed
    val ticketCharged = if (hasServiceRuntimeState) serviceRuntimeState.ticketCharged else fallbackTicketCharged
    val isPaused = if (hasServiceRuntimeState) serviceRuntimeState.isPaused else fallbackIsPaused

    // ???????????????????椰????
    val localTickDelayMillis = remember(draft.timeScale) {
        (1000f / draft.timeScale.coerceIn(0.001f, 1000f)).toLong().coerceAtLeast(MinTickDelayMillis)
    }
    val renderFrameDelayMillis = remember(draft.timeScale) {
        when {
            draft.timeScale <= InFlightLowSpeedThreshold -> InFlightSingleSpeedFrameDelayMillis
            draft.timeScale <= InFlightMediumSpeedThreshold -> InFlightLowSpeedFrameDelayMillis
            else -> InFlightDefaultFrameDelayMillis
        }
    }
    val cameraTrackingUpdateIntervalMillis = remember(draft.timeScale) {
        when {
            draft.timeScale <= InFlightLowSpeedThreshold -> InFlightSingleSpeedCameraUpdateMillis
            draft.timeScale <= InFlightMediumSpeedThreshold -> InFlightLowSpeedCameraUpdateMillis
            draft.timeScale <= InFlightHighSpeedThreshold -> InFlightMediumSpeedCameraUpdateMillis
            else -> InFlightHighSpeedCameraUpdateMillis
        }
    }
    val progress = if (totalSeconds > 0) (secondsElapsed.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f
    val remainingSeconds = (totalSeconds - secondsElapsed).coerceAtLeast(0)
    val isFlying = secondsElapsed < totalSeconds

    // ????겾??좊읈??????源낇꼧???⑥??????ㅺ컼??
    // ??살쨮揶쎛疫?甕곌쑵???紐껊굶??
    BackHandler {
        inflightViewModel.showGiveUpDialog()
    }

    fun pauseFlight() {
        context.startService(
            Intent(context, FlightService::class.java).apply {
                action = FlightService.ACTION_PAUSE
            }
        )
        if (!hasServiceRuntimeState) {
            fallbackIsPaused = true
        }
        inflightViewModel.hideGiveUpDialog()
    }

    fun resumeFlight() {
        context.startService(
            Intent(context, FlightService::class.java).apply {
                action = FlightService.ACTION_RESUME
            }
        )
        if (!hasServiceRuntimeState) {
            fallbackIsPaused = false
        }
    }

    fun stopFlight() {
        context.startService(
            Intent(context, FlightService::class.java).apply {
                action = FlightService.ACTION_STOP
            }
        )
        fallbackIsPaused = false
        inflightViewModel.hideGiveUpDialog()
        onFlightEnd()
    }

    LaunchedEffect(Unit) {
        inflightViewModel.events.collect { event ->
            when (event) {
                is InFlightEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                InFlightEvent.LaunchRewardedAd -> {
                    if (activity == null) {
                        inflightViewModel.handleAdLoadFailed(context.getString(R.string.tickets_ad_reward_unavailable))
                        return@collect
                    }

                    val adRequest = AdRequest.Builder().build()
                    RewardedAd.load(
                        context,
                        BuildConfig.ADMOB_REWARDED_AD_UNIT_ID,
                        adRequest,
                        object : RewardedAdLoadCallback() {
                            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                                inflightViewModel.handleAdLoadFailed(loadAdError.message)
                            }

                            override fun onAdLoaded(rewardedAd: RewardedAd) {
                                var rewardEarned = false
                                rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                                    override fun onAdDismissedFullScreenContent() {
                                        if (!rewardEarned) {
                                            inflightViewModel.handleAdClosedWithoutReward()
                                        }
                                    }

                                    override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                                        inflightViewModel.handleAdLoadFailed(adError.message)
                                    }
                                }
                                rewardedAd.show(activity) {
                                    rewardEarned = true
                                    inflightViewModel.completeAdReward()
                                }
                            }
                        }
                    )
                }
            }
        }
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
            if (!isDebugSliderDirty) {
                debugSliderSeconds = secondsElapsed.toFloat()
            }
            renderedElapsedSeconds = secondsElapsed.toFloat()
            animationStartElapsed = renderedElapsedSeconds
            animationTargetElapsed = renderedElapsedSeconds
            animationStartedAtMillis = SystemClock.elapsedRealtime()
        } else {
            fallbackSecondsElapsed = 0L
            fallbackTicketCharged = false
            fallbackIsPaused = false
            if (!isDebugSliderDirty) {
                debugSliderSeconds = 0f
            }
            renderedElapsedSeconds = 0f
            animationStartElapsed = 0f
            animationTargetElapsed = 0f
            animationStartedAtMillis = SystemClock.elapsedRealtime()
        }
    }

    LaunchedEffect(serviceRuntimeState.secondsElapsed, serviceRuntimeState.ticketCharged, serviceRuntimeState.isPaused, serviceRuntimeState.isRunning) {
        if (!serviceRuntimeState.isRunning) {
            return@LaunchedEffect
        }

        if (!isDebugSliderDirty) {
            debugSliderSeconds = secondsElapsed.toFloat()
        }
    }

    LaunchedEffect(secondsElapsed) {
        animationStartElapsed = renderedElapsedSeconds
        animationTargetElapsed = secondsElapsed.toFloat()
        animationStartedAtMillis = SystemClock.elapsedRealtime()
        if (isPaused) {
            renderedElapsedSeconds = animationTargetElapsed
        }
    }

    LaunchedEffect(isPaused) {
        if (isPaused) {
            renderedElapsedSeconds = secondsElapsed.toFloat()
            animationStartElapsed = renderedElapsedSeconds
            animationTargetElapsed = renderedElapsedSeconds
            animationStartedAtMillis = SystemClock.elapsedRealtime()
        }
    }

    val origin = draft.origin.location
    val dest = draft.destination?.location ?: origin
    val routePoints = remember(origin, dest) {
        buildGreatCircleRoutePoints(origin, dest)
    }
    val planeMarker = remember { MapBitmapUtils.createPlaneMarkerBitmap(context) }
    val planeMarkerState = remember { MarkerState(position = origin) }
    val liveCurrentPos = remember(origin, dest) { mutableStateOf(origin) }
    val liveBearing = remember(origin, dest) {
        mutableFloatStateOf(calculateGreatCircleBearing(origin, dest, 0.0))
    }
    val planeRotation = planeMarkerRotationForBearing(liveBearing.floatValue)
    var lastCameraTrackingUpdateAt by remember { mutableStateOf(0L) }
    var hasUserAdjustedMapZoom by remember { mutableStateOf(false) }
    val isCameraTracking = inflightUiState.isCameraTracking
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(origin, InFlightInitialZoom)
    }
    val zoomLevel by remember {
        derivedStateOf { cameraPositionState.position.zoom.roundToInt() }
    }
    val zoomLabel = if (mapPerspective == Perspective3D) {
        stringResource(R.string.inflight_zoom_label_3d)
    } else {
        stringResource(R.string.inflight_zoom_label, zoomLevel)
    }

    LaunchedEffect(Unit) {
        hasUserAdjustedMapZoom = false
        mapPerspective = Perspective2_5D
        repository.setMapPerspective(Perspective2_5D)
    }

    fun trackedZoom(): Float {
        val currentZoom = cameraPositionState.position.zoom
        return if (hasUserAdjustedMapZoom) {
            currentZoom.takeIf { it >= InFlightMinTrackedZoom } ?: InFlightInitialZoom
        } else {
            currentZoom.coerceAtLeast(InFlightInitialZoom)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (isPaused) {
                renderedElapsedSeconds = secondsElapsed.toFloat()
            } else {
                val now = SystemClock.elapsedRealtime()
                val durationMillis = localTickDelayMillis.coerceAtMost(InFlightAnimationDurationCapMillis).toFloat().coerceAtLeast(1f)
                val fraction = ((now - animationStartedAtMillis).toFloat() / durationMillis).coerceIn(0f, 1f)
                renderedElapsedSeconds = animationStartElapsed + (animationTargetElapsed - animationStartElapsed) * fraction
            }
            val smoothedProgress = if (totalSeconds > 0) {
                (renderedElapsedSeconds / totalSeconds.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val updatedPos = interpolateGreatCircle(origin, dest, smoothedProgress.toDouble())
            val updatedBearing = calculateGreatCircleBearing(origin, dest, smoothedProgress.toDouble())
            liveCurrentPos.value = updatedPos
            liveBearing.floatValue = updatedBearing
            planeMarkerState.position = updatedPos

            if (mapPerspective != Perspective3D &&
                inflightUiState.isCameraTracking &&
                !cameraPositionState.isMoving
            ) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastCameraTrackingUpdateAt >= cameraTrackingUpdateIntervalMillis) {
                    val currentTilt = tiltForPerspective(mapPerspective)
                    lastCameraTrackingUpdateAt = now
                    cameraPositionState.move(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(updatedPos)
                                .zoom(trackedZoom())
                                .tilt(currentTilt)
                                .bearing(bearingForPerspective(mapPerspective, updatedBearing))
                                .build()
                        )
                    )
                }
            }
            delay(renderFrameDelayMillis)
        }
    }

    // ???耀붾굝??????????嶺??? ????????산뭐????? ??????μ떜媛?걫?????????????????????????????傭?끆????ш끽維??????븐뼐????傭?끆?????Β?ｊ콞???癲??????
    LaunchedEffect(isFlying, serviceRuntimeState.isRunning) {
        if (isFlying && !serviceRuntimeState.isRunning) {
            while (fallbackSecondsElapsed < totalSeconds) {
                delay(localTickDelayMillis)
                if (FlightService.isServiceRunning()) {
                    break
                }
                fallbackSecondsElapsed++
                if (!isDebugSliderDirty) {
                    debugSliderSeconds = fallbackSecondsElapsed.toFloat()
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
            if (hasServiceRuntimeState) {
                FlightService.markTicketCharged()
            } else {
                fallbackTicketCharged = true
            }
            Toast.makeText(context, context.getString(R.string.ticket_deducted_after_ten_minutes), Toast.LENGTH_SHORT).show()
        }
    }

    // ?????轅붽틓??????饔낅떽???????????袁⑸즴筌?씛彛?????????????????????(??????????⑤벡瑜?????- Great Circle Interpolation)
    fun applyDebugElapsed(targetSeconds: Long) {
        val clampedSeconds = targetSeconds.coerceIn(0L, totalSeconds)
        if (hasServiceRuntimeState) {
            FlightService.jumpToElapsedSeconds(clampedSeconds)
        } else {
            fallbackSecondsElapsed = clampedSeconds
        }
        renderedElapsedSeconds = clampedSeconds.toFloat()
        debugSliderSeconds = clampedSeconds.toFloat()
        isDebugSliderDirty = false
    }

    fun formatTimeScale(scale: Float): String {
        return when {
            scale >= 100f -> "${scale.toInt()}x"
            scale >= 10f -> "${"%.1f".format(scale)}x"
            scale >= 1f -> "${"%.2f".format(scale)}x"
            else -> "${"%.3f".format(scale)}x"
        }
    }

    suspend fun updateCameraPerspective(
        perspective: String,
        keepTrackingTarget: Boolean
    ) {
        val target = if (keepTrackingTarget || isCameraTracking) {
            liveCurrentPos.value
        } else {
            cameraPositionState.position.target
        }
        val updatedTilt = tiltForPerspective(perspective)
        val updatedBearing = bearingForPerspective(perspective, liveBearing.floatValue)
        cameraPositionState.move(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(cameraPositionState.position)
                    .target(target)
                    .zoom(trackedZoom())
                    .tilt(updatedTilt)
                    .bearing(updatedBearing)
                    .build()
            )
        )
    }

    // ??????嶺뚮∥?????????筌뤾퍓愿???????????袁⑸즴筌?씛彛?????????롮쾸?椰???(???????????????곗뵰??? ????????袁⑸즴筌?씛彛???돗????⑸뻿????????嚥싲갭큔?????????븐뼐???????????븐뼔???????????뀀맩鍮??????룸챶猷??????????????
    LaunchedEffect(mapPerspective) {
        val target = if (isCameraTracking) {
            liveCurrentPos.value
        } else {
            cameraPositionState.position.target
        }
        cameraPositionState.animate(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(trackedZoom())
                    .tilt(tiltForPerspective(mapPerspective))
                    .bearing(bearingForPerspective(mapPerspective, liveBearing.floatValue))
                    .build()
            )
        )
    }

    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving to cameraPositionState.cameraMoveStartedReason }
            .collect { (isMoving, moveStartedReason) ->
                if (mapPerspective != Perspective3D &&
                    isMoving &&
                    moveStartedReason == CameraMoveStartedReason.GESTURE
                ) {
                    hasUserAdjustedMapZoom = true
                    inflightViewModel.disableCameraTracking()
                }
            }
    }

    // ?????轅붽틓???????????袁⑸즴筌?씛彛?????????ル???? ??????
    LaunchedEffect(secondsElapsed, totalSeconds) {
        if (secondsElapsed >= totalSeconds && totalSeconds > 0) {
            if (hasServiceRuntimeState) {
                onFlightEnd()
            } else {
                saveAndExit(
                    repository = repository,
                    draft = draft,
                    progress = 1f,
                    isCompleted = true,
                    startTime = System.currentTimeMillis() - (draft.estimatedMinutes * 60 * 1000),
                    onExit = onFlightEnd,
                    context = context
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (mapPerspective == Perspective3D) {
            EmbeddedMap3DVRView(
                currentPosition = liveCurrentPos.value,
                bearing = liveBearing.floatValue,
                isCameraTracking = isCameraTracking,
                modifier = Modifier.fillMaxSize(),
                onUserInteraction = { inflightViewModel.disableCameraTracking() },
                    onMapError = {
                        scope.launch {
                            mapPerspective = Perspective2_5D
                            repository.setMapPerspective(Perspective2_5D)
                        }
                    Toast.makeText(
                        context,
                        context.getString(R.string.inflight_3d_fallback_message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }

        EmbeddedRealFlightMap(
            cameraPositionState = cameraPositionState,
            mapStyle = mapStyle,
            renderMap = mapPerspective != Perspective3D,
            isInteractive = true,
            allowRotationGestures = true,
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
                    state = planeMarkerState,
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
                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            InFlightStatusPanel(
                                draft = draft,
                                remainingSeconds = remainingSeconds,
                                zoomLabel = zoomLabel,
                                timeScaleLabel = draft.timeScale.takeIf { it != 1f }?.let(::formatTimeScale),
                                destinationIataFallback = "---",
                                colors = panelColors,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            InFlightFloatingControls(
                                isAdRewardRunning = inflightUiState.isAdRewardRunning,
                                mapPerspective = mapPerspective,
                                isCameraTracking = isCameraTracking,
                                overlayPalette = overlayPalette,
                                modifier = Modifier.weight(1f),
                                onShowReward = { inflightViewModel.showAdRewardDialog() },
                                onToggleStandardPerspective = {
                                    scope.launch {
                                        val nextPerspective = nextStandardMapPerspective(mapPerspective)
                                        updateCameraPerspective(nextPerspective, isCameraTracking)
                                        mapPerspective = nextPerspective
                                        repository.setMapPerspective(nextPerspective)
                                    }
                                },
                                onToggle3dPerspective = {
                                    scope.launch {
                                        val nextPerspective =
                                            if (mapPerspective == Perspective3D) Perspective2_5D else Perspective3D
                                        updateCameraPerspective(nextPerspective, isCameraTracking)
                                        mapPerspective = nextPerspective
                                        repository.setMapPerspective(nextPerspective)
                                    }
                                },
                                onCenterOnPlane = {
                                    inflightViewModel.enableCameraTracking()
                                    if (mapPerspective != Perspective3D) {
                                        scope.launch {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newCameraPosition(
                                                    CameraPosition.Builder()
                                                        .target(liveCurrentPos.value)
                                                        .zoom(cameraPositionState.position.zoom)
                                                        .tilt(tiltForPerspective(mapPerspective))
                                                        .bearing(bearingForPerspective(mapPerspective, liveBearing.floatValue))
                                                        .build()
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    } else {
                        InFlightStatusPanel(
                            draft = draft,
                            remainingSeconds = remainingSeconds,
                            zoomLabel = zoomLabel,
                            timeScaleLabel = draft.timeScale.takeIf { it != 1f }?.let(::formatTimeScale),
                            destinationIataFallback = stringResource(R.string.boardingpass_destination_tbd),
                            colors = panelColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isLandscape) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true)
                        ) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth(0.33f),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (debugFlightMode) {
                                    InFlightDebugPanel(
                                        modifier = Modifier.fillMaxWidth(),
                                        debugSliderSeconds = debugSliderSeconds,
                                        totalSeconds = totalSeconds,
                                        overlayPalette = overlayPalette,
                                        onSliderChange = {
                                            debugSliderSeconds = it
                                            isDebugSliderDirty = true
                                            lastDebugSliderInteractionAt = System.currentTimeMillis()
                                        },
                                        onJumpTo950 = { applyDebugElapsed(590L) },
                                        onJumpToHalf = { applyDebugElapsed(totalSeconds / 2) },
                                        onApply = { applyDebugElapsed(debugSliderSeconds.toLong()) }
                                    )
                                }

                                InFlightProgressPanel(
                                    modifier = Modifier.fillMaxWidth(),
                                    progress = progress,
                                    timeScaleLabel = draft.timeScale.takeIf { it != 1f }?.let(::formatTimeScale),
                                    colors = panelColors
                                )
                            }

                            InFlightActionRow(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .fillMaxWidth(0.33f),
                                onPause = { pauseFlight() },
                                onGiveUp = { inflightViewModel.showGiveUpDialog() },
                                colors = panelColors
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            InFlightFloatingControls(
                                isAdRewardRunning = inflightUiState.isAdRewardRunning,
                                mapPerspective = mapPerspective,
                                isCameraTracking = isCameraTracking,
                                overlayPalette = overlayPalette,
                                modifier = Modifier.align(Alignment.End),
                                onShowReward = { inflightViewModel.showAdRewardDialog() },
                                onToggleStandardPerspective = {
                                    scope.launch {
                                        val nextPerspective = nextStandardMapPerspective(mapPerspective)
                                        updateCameraPerspective(nextPerspective, isCameraTracking)
                                        mapPerspective = nextPerspective
                                        repository.setMapPerspective(nextPerspective)
                                    }
                                },
                                onToggle3dPerspective = {
                                    scope.launch {
                                        val nextPerspective =
                                            if (mapPerspective == Perspective3D) Perspective2_5D else Perspective3D
                                        updateCameraPerspective(nextPerspective, isCameraTracking)
                                        mapPerspective = nextPerspective
                                        repository.setMapPerspective(nextPerspective)
                                    }
                                },
                                onCenterOnPlane = {
                                    inflightViewModel.enableCameraTracking()
                                    if (mapPerspective != Perspective3D) {
                                        scope.launch {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newCameraPosition(
                                                    CameraPosition.Builder()
                                                        .target(liveCurrentPos.value)
                                                        .zoom(cameraPositionState.position.zoom)
                                                        .tilt(tiltForPerspective(mapPerspective))
                                                        .bearing(bearingForPerspective(mapPerspective, liveBearing.floatValue))
                                                        .build()
                                                )
                                            )
                                        }
                                    }
                                }
                            )

                            if (debugFlightMode) {
                                InFlightDebugPanel(
                                    modifier = Modifier.fillMaxWidth(),
                                    debugSliderSeconds = debugSliderSeconds,
                                    totalSeconds = totalSeconds,
                                    overlayPalette = overlayPalette,
                                    onSliderChange = {
                                        debugSliderSeconds = it
                                        isDebugSliderDirty = true
                                        lastDebugSliderInteractionAt = System.currentTimeMillis()
                                    },
                                    onJumpTo950 = { applyDebugElapsed(590L) },
                                    onJumpToHalf = { applyDebugElapsed(totalSeconds / 2) },
                                    onApply = { applyDebugElapsed(debugSliderSeconds.toLong()) }
                                )
                            }

                            InFlightProgressPanel(
                                modifier = Modifier.fillMaxWidth(),
                                progress = progress,
                                timeScaleLabel = draft.timeScale.takeIf { it != 1f }?.let(::formatTimeScale),
                                colors = panelColors
                            )

                            InFlightActionRow(
                                modifier = Modifier.fillMaxWidth(),
                                onPause = { pauseFlight() },
                                onGiveUp = { inflightViewModel.showGiveUpDialog() },
                                colors = panelColors
                            )
                        }
                    }
                }
            }
        )

        if (isPaused) {
            InFlightPausedOverlay(
                colors = panelColors,
                onNavigateToSettings = onNavigateToSettings,
                onResume = { resumeFlight() }
            )
        }

        if (inflightUiState.showAdRewardDialog) {
            InFlightRewardPromptOverlay(
                colors = panelColors,
                onWatchAd = { inflightViewModel.startAdReward() },
                onDismiss = { inflightViewModel.hideAdRewardDialog() }
            )
        }

        if (inflightUiState.isAdRewardRunning) {
            InFlightAdRunningOverlay(
                colors = panelColors,
                onCancel = { inflightViewModel.cancelAdReward() }
            )
        }
    }

    if (inflightUiState.showGiveUpDialog) {
        InFlightGiveUpDialog(
            onDismiss = { inflightViewModel.hideGiveUpDialog() },
            onConfirmGiveUp = { stopFlight() }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun rememberInFlightOverlayPalette(style: String): com.example.openflight4and.ui.components.MapOverlayPalette {
    return when (style) {
        "light" -> com.example.openflight4and.ui.components.MapOverlayPalette(
            panelBackground = Color.White.copy(alpha = 0.9f),
            panelBorder = Color.Black.copy(alpha = 0.12f),
            primaryText = Color(0xFF0D0000),
            secondaryText = Color(0xFF262523).copy(alpha = 0.8f),
            accentText = Color(0xFF0D0000),
            iconTint = Color(0xFF0D0000),
            divider = Color.Black.copy(alpha = 0.08f),
            trackColor = Color.Black.copy(alpha = 0.08f),
            floatingButtonContainer = Color.White.copy(alpha = 0.96f),
            floatingButtonContent = Color(0xFF0D0000)
        )

        else -> com.example.openflight4and.ui.components.MapOverlayPalette(
            panelBackground = Color.White.copy(alpha = 0.24f),
            panelBorder = Color.White.copy(alpha = 0.28f),
            primaryText = Color.White,
            secondaryText = Color(0xFF8C8A80),
            accentText = Color(0xFFD9D7CC),
            iconTint = Color(0xFF8C8A80),
            divider = Color.White.copy(alpha = 0.16f),
            trackColor = Color.White.copy(alpha = 0.16f),
            floatingButtonContainer = Color(0xFF0D0000).copy(alpha = 0.88f),
            floatingButtonContent = Color(0xFFD9D7CC)
        )
    }
}

@Composable
private fun EmbeddedRealFlightMap(
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState,
    mapStyle: String = "standard",
    renderMap: Boolean = true,
    isInteractive: Boolean = true,
    allowRotationGestures: Boolean = false,
    mapContent: @Composable (() -> Unit)? = null,
    overlayContent: @Composable (BoxScope.() -> Unit)? = null,
    useDarkOverlay: Boolean = true
) {
    val mapType = when (mapStyle) {
        "satellite" -> MapType.SATELLITE
        "hybrid" -> MapType.HYBRID
        else -> MapType.NORMAL
    }
    val overlayAlpha = if (mapType == MapType.NORMAL) 0.6f else 0.3f

    Box(modifier = modifier.fillMaxSize()) {
        if (renderMap) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = mapType,
                    isMyLocationEnabled = false,
                    isTrafficEnabled = false,
                    maxZoomPreference = 20f,
                    minZoomPreference = 2f
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false,
                    scrollGesturesEnabled = isInteractive,
                    zoomGesturesEnabled = isInteractive,
                    tiltGesturesEnabled = false,
                    rotationGesturesEnabled = isInteractive && allowRotationGestures
                )
            ) {
                mapContent?.invoke()
            }
        }

        if (renderMap && useDarkOverlay) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color(0xFF0D0000).copy(alpha = overlayAlpha))
            }
        }

        overlayContent?.invoke(this)
    }
}

private fun normalizeMaps3DHeading(bearing: Float): Double {
    return ((bearing % 360f) + 360f).toDouble() % 360.0
}

private fun normalizedHeadingDouble(heading: Double): Double {
    return ((heading % 360.0) + 360.0) % 360.0
}

private fun adjustedCameraForDrag(
    camera: Camera,
    deltaX: Float,
    deltaY: Float
): Camera {
    val currentHeading = camera.heading ?: 0.0
    val currentTilt = camera.tilt ?: Maps3DTiltDegrees
    val updatedHeading = normalizedHeadingDouble(currentHeading - deltaX * Maps3DManualHeadingPerPixel)
    val updatedTilt = (currentTilt + deltaY * Maps3DManualTiltPerPixel)
        .coerceIn(Maps3DMinTiltDegrees, Maps3DMaxTiltDegrees)
    return camera {
        center = camera.center
        heading = updatedHeading
        tilt = updatedTilt
        roll = camera.roll ?: 0.0
        range = camera.range ?: Maps3DRangeMeters
    }
}

@Composable
private fun EmbeddedMap3DVRView(
    currentPosition: LatLng,
    bearing: Float,
    isCameraTracking: Boolean,
    modifier: Modifier = Modifier,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null,
    onUserInteraction: () -> Unit = {},
    onMapError: (Exception) -> Unit = {}
) {
    var map3D by remember { mutableStateOf<GoogleMap3D?>(null) }
    var map3DView by remember { mutableStateOf<Map3DView?>(null) }
    var isUserCameraOverride by remember { mutableStateOf(false) }
    var lastTouchX by remember { mutableStateOf(0f) }
    var lastTouchY by remember { mutableStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val normalizedHeading = remember(bearing) { normalizeMaps3DHeading(bearing) }

    DisposableEffect(lifecycleOwner, map3DView) {
        val view = map3DView
        if (view == null) {
            return@DisposableEffect onDispose {}
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        val currentState = lifecycleOwner.lifecycle.currentState
        if (currentState.isAtLeast(Lifecycle.State.STARTED)) {
            view.onStart()
        }
        if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            view.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                val options = Map3DOptions(
                    centerLat = currentPosition.latitude,
                    centerLng = currentPosition.longitude,
                    centerAlt = Maps3DAltitudeMeters,
                    heading = normalizedHeading,
                    tilt = Maps3DTiltDegrees,
                    roll = 0.0,
                    range = Maps3DRangeMeters
                )
                Map3DView(viewContext, options).apply {
                    map3DView = this
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                isUserCameraOverride = true
                                onUserInteraction()
                                lastTouchX = event.x
                                lastTouchY = event.y
                                true
                            }

                            MotionEvent.ACTION_MOVE -> {
                                if (event.pointerCount == 1) {
                                    val deltaX = event.x - lastTouchX
                                    val deltaY = event.y - lastTouchY
                                    if (abs(deltaX) > Maps3DDragThresholdPixels || abs(deltaY) > Maps3DDragThresholdPixels) {
                                        map3D?.getCamera()?.let { currentCamera ->
                                            map3D?.setCamera(
                                                adjustedCameraForDrag(
                                                    camera = currentCamera,
                                                    deltaX = deltaX,
                                                    deltaY = deltaY
                                                )
                                            )
                                        }
                                        lastTouchX = event.x
                                        lastTouchY = event.y
                                    }
                                    true
                                } else {
                                    false
                                }
                            }

                            MotionEvent.ACTION_POINTER_DOWN -> {
                                isUserCameraOverride = true
                                onUserInteraction()
                                false
                            }

                            else -> false
                        }
                    }
                    onCreate(null)
                    getMap3DViewAsync(
                        object : OnMap3DViewReadyCallback {
                            override fun onMap3DViewReady(googleMap3D: GoogleMap3D) {
                                map3D = googleMap3D
                            }

                            override fun onError(error: Exception) {
                                Log.e(Maps3DTag, "Map3D initialization failed", error)
                                onMapError(error)
                            }
                        }
                    )
                }
            },
            update = { view ->
                if (map3D == null) {
                    view.getMap3DViewAsync(
                        object : OnMap3DViewReadyCallback {
                            override fun onMap3DViewReady(googleMap3D: GoogleMap3D) {
                                map3D = googleMap3D
                            }

                            override fun onError(error: Exception) {
                                Log.e(Maps3DTag, "Map3D update failed", error)
                                onMapError(error)
                            }
                        }
                    )
                }
            },
            onRelease = { view ->
                map3D = null
                map3DView = null
                view.onDestroy()
            }
        )

        overlayContent?.invoke(this)
    }

    LaunchedEffect(isCameraTracking) {
        if (isCameraTracking) {
            isUserCameraOverride = false
        }
    }

    LaunchedEffect(map3D, currentPosition, bearing, isCameraTracking, isUserCameraOverride) {
        if (!isCameraTracking || isUserCameraOverride) {
            return@LaunchedEffect
        }

        map3D?.setCamera(
            camera {
                center = latLngAltitude {
                    latitude = currentPosition.latitude
                    longitude = currentPosition.longitude
                    altitude = Maps3DAltitudeMeters
                }
                heading = normalizedHeading
                tilt = Maps3DTiltDegrees
                roll = 0.0
                range = Maps3DRangeMeters
            }
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
    val languageTag = context.resources.configuration.locales[0]?.toLanguageTag()
        ?: java.util.Locale.getDefault().toLanguageTag()
    val session = FlightSession(
        flightNumber = draft.flightNumber,
        originIata = draft.origin.iata,
        originName = draft.origin.localizedName(languageTag),
        destinationIata = draft.destination?.iata ?: "Unknown",
        destinationName = draft.destination?.localizedName(languageTag) ?: "Unknown",
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

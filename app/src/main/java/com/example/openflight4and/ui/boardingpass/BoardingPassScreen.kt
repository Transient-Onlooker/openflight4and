package com.example.openflight4and.ui.boardingpass

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.openflight4and.R
import com.example.openflight4and.model.FlightDraft
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.theme.FlightBlack
import com.example.openflight4and.ui.theme.FlightGray
import com.example.openflight4and.utils.FlightUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardingPassScreen(
    draft: FlightDraft,
    onNavigateBack: () -> Unit,
    onNavigateToSeatSelection: () -> Unit,
    unitSystem: String
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val density = LocalDensity.current
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    val tearLinePath = remember {
        Path().apply {
            val width = 1000f
            val height = 8f
            val zigzagCount = 80
            moveTo(0f, height / 2)
            for (i in 0..zigzagCount) {
                val x = (i.toFloat() / zigzagCount) * width
                val y = if (i % 2 == 0) 0f else height
                lineTo(x, y)
            }
            lineTo(width, height / 2)
        }
    }

    val tearDistancePx = with(density) { 150.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val wobbleX = remember { Animatable(0f) }
    val wobbleRotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    var isTorn by remember { mutableStateOf(false) }
    var showGreeting by remember { mutableStateOf(false) }
    var tearProgress by remember { mutableStateOf(0f) }

    fun triggerHapticTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    val draggableState = rememberDraggableState { delta ->
        if (!isTorn) {
            scope.launch {
                val newX = (offsetX.value + delta).coerceAtLeast(0f)
                offsetX.snapTo(newX)

                val progress = (newX / tearDistancePx).coerceIn(0f, 1f)
                tearProgress = progress

                rotation.snapTo(progress * 30f)
                offsetY.snapTo(progress * 50f)

                val currentHapticIndex = (progress * 8).toInt()
                val prevHapticIndex = ((progress - 0.01f).coerceAtLeast(0f) * 8).toInt()
                if (currentHapticIndex > prevHapticIndex && currentHapticIndex < 8) {
                    triggerHapticTick()
                }

                if (newX > tearDistancePx) {
                    isTorn = true
                    showGreeting = true

                    launch { offsetX.animateTo(1000f, animationSpec = tween(400, easing = LinearEasing)) }
                    launch { offsetY.animateTo(2000f, animationSpec = tween(800, easing = LinearEasing)) }
                    launch { rotation.animateTo(-120f, animationSpec = tween(800, easing = LinearEasing)) }
                    launch {
                        val shakeDuration = 800
                        val shakeSteps = 20
                        for (i in 0 until shakeSteps) {
                            val t = i.toFloat() / shakeSteps
                            val shake = (sin(t * PI * 4) * 30.0 * (1.0 - t.toDouble())).toFloat()
                            wobbleX.snapTo(shake)
                            val wobbleRot = (sin(t * PI * 6) * 15.0 * (1.0 - t.toDouble())).toFloat()
                            wobbleRotation.snapTo(wobbleRot)
                            delay((shakeDuration / shakeSteps).toLong())
                        }
                        wobbleX.snapTo(0f)
                        wobbleRotation.snapTo(0f)
                    }

                    delay(1500)
                    onNavigateToSeatSelection()
                }
            }
        }
    }

    FlightMapBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.boardingpass_title),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        if (isLandscape) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Column(
                            modifier = if (isLandscape) {
                                Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxWidth()
                                    .widthIn(max = 280.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 280.dp)
                            }
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(1f),
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(24.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(draft.origin.cityKo, color = FlightGray, style = MaterialTheme.typography.labelSmall)
                                            Text(draft.origin.iata, color = FlightBlack, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Icon(
                                            imageVector = Icons.Default.FlightTakeoff,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .align(Alignment.CenterVertically),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                draft.destination?.cityKo ?: stringResource(R.string.boardingpass_destination_tbd),
                                                color = FlightGray,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                            Text(draft.destination?.iata ?: "---", color = FlightBlack, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(32.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        TicketInfoItem(stringResource(R.string.boardingpass_info_flight), draft.flightNumber)
                                        TicketInfoItem(stringResource(R.string.boardingpass_info_distance), FlightUtils.formatDistance(draft.distanceKm, unitSystem))
                                        TicketInfoItem(
                                            stringResource(R.string.boardingpass_info_boarding_time),
                                            draft.boardingTime.ifBlank { stringResource(R.string.label_now) }
                                        )
                                    }
                                }
                            }

                            Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                                drawPath(
                                    path = tearLinePath,
                                    color = FlightGray.copy(alpha = 0.5f),
                                    style = Stroke(
                                        width = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                                    )
                                )
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        translationX = offsetX.value + wobbleX.value
                                        translationY = offsetY.value
                                        rotationZ = rotation.value + wobbleRotation.value
                                        shadowElevation = if (offsetX.value > 0) 16.dp.toPx() else 0f
                                        alpha = if (offsetY.value > 500) {
                                            1f - ((offsetY.value - 500) / 1500).coerceIn(0f, 1f)
                                        } else {
                                            1f
                                        }
                                    }
                                    .draggable(state = draggableState, orientation = Orientation.Horizontal),
                                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp)
                                            .background(FlightGray.copy(alpha = 0.1f))
                                    ) {
                                        Text(
                                            "|| ||| | || ||| || || |",
                                            modifier = Modifier.align(Alignment.Center),
                                            style = MaterialTheme.typography.headlineLarge,
                                            color = FlightBlack,
                                            letterSpacing = 4.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        if (isTorn) {
                                            stringResource(R.string.boardingpass_confirmed)
                                        } else {
                                            stringResource(R.string.boardingpass_swipe_to_confirm)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isTorn) Color.Gray else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (isLandscape) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showGreeting,
                    enter = fadeIn(animationSpec = tween(500)),
                    exit = fadeOut(animationSpec = tween(500)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Surface(
                        color = FlightBlack.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            stringResource(R.string.boardingpass_greeting),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(vertical = 32.dp, horizontal = 48.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TicketInfoItem(label: String, value: String) {
    Column {
        Text(label, color = FlightGray, style = MaterialTheme.typography.labelSmall)
        Text(value, color = FlightBlack, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

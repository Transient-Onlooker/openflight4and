package com.example.openflight4and.ui.boardingpass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.FlightDraft
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.theme.FlightBlack
import com.example.openflight4and.ui.theme.FlightGray
import com.example.openflight4and.utils.FlightUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardingPassScreen(
    draft: FlightDraft,
    onNavigateBack: () -> Unit,
    onNavigateToSeatSelection: () -> Unit,
    unitSystem: String // 파라미터 추가
) {
    val context = LocalContext.current
    // repository.unitSystem 호출 제거 (파라미터 사용)
    
    val density = LocalDensity.current
    val tearDistancePx = with(density) { 150.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    var isTorn by remember { mutableStateOf(false) }
    var showGreeting by remember { mutableStateOf(false) }

    val draggableState = rememberDraggableState { delta ->
        if (!isTorn) {
            scope.launch {
                val newX = (offsetX.value + delta).coerceAtLeast(0f)
                offsetX.snapTo(newX)
                
                val progress = (newX / tearDistancePx).coerceIn(0f, 1f)
                
                rotation.snapTo(progress * 45f)
                offsetY.snapTo(progress * 300f)
                
                if (newX > tearDistancePx) {
                    isTorn = true
                    showGreeting = true
                    offsetX.animateTo(1000f, animationSpec = tween(300))
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
                    title = { Text("탑승권", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = Color.White)
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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 1. Main Ticket (Upper)
                        Card(
                            modifier = Modifier.fillMaxWidth().zIndex(1f),
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
                                        modifier = Modifier.size(40.dp).align(Alignment.CenterVertically),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(draft.destination?.cityKo ?: "미정", color = FlightGray, style = MaterialTheme.typography.labelSmall)
                                        Text(draft.destination?.iata ?: "---", color = FlightBlack, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TicketInfoItem("항공편", draft.flightNumber)
                                    TicketInfoItem("거리", FlightUtils.formatDistance(draft.distanceKm, unitSystem))
                                    TicketInfoItem("탑승시간", draft.boardingTime)
                                }
                            }
                        }

                        // 2. Tear Line
                        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp).background(Color.White)) {
                            drawLine(
                                color = FlightGray.copy(alpha = 0.5f),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f),
                                strokeWidth = 2.dp.toPx()
                            )
                        }

                        // 3. Stub (Lower)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationX = offsetX.value
                                    translationY = offsetY.value
                                    rotationZ = rotation.value
                                    shadowElevation = if (offsetX.value > 0) 16.dp.toPx() else 0f
                                }
                                .draggable(state = draggableState, orientation = Orientation.Horizontal),
                            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.fillMaxWidth().height(60.dp).background(FlightGray.copy(alpha = 0.1f))) {
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
                                    if (isTorn) "탑승 확인 완료" else "오른쪽으로 밀어서 탑승 확인 ->",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isTorn) Color.Gray else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 인사말 오버레이
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
                            "좋은 비행 되세요",
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
fun TicketInfoItem(label: String, value: String) {
    Column {
        Text(label, color = FlightGray, style = MaterialTheme.typography.labelSmall)
        Text(value, color = FlightBlack, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

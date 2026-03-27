package com.example.openflight4and.ui.sandbox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.Airport
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.theme.FlightGray
import com.example.openflight4and.ui.theme.FlightPrimary
import kotlinx.coroutines.launch
import kotlin.math.ln

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAirportSelection: (Boolean) -> Unit, // isSettingCurrentLocation
    currentLocation: Airport?,
    timeScale: Float,
    onTimeScaleChanged: (Float) -> Unit,
    onSaveCompleted: () -> Unit
) {
    val minTimeScale = 0.01f
    val maxTimeScale = 100f
    val minLog = ln(minTimeScale)
    val maxLog = ln(maxTimeScale)
    val oneXSliderPosition = ((ln(1f) - minLog) / (maxLog - minLog)).toFloat()
    val oneXSnapWindow = 0.035f
    val oneXMagnetWindow = 0.09f

    fun snapSliderPosition(position: Float): Float {
        val clamped = position.coerceIn(0f, 1f)
        val distanceFromOneX = kotlin.math.abs(clamped - oneXSliderPosition)

        return when {
            distanceFromOneX <= oneXSnapWindow -> oneXSliderPosition
            distanceFromOneX <= oneXMagnetWindow -> {
                val ratio = (distanceFromOneX - oneXSnapWindow) / (oneXMagnetWindow - oneXSnapWindow)
                val eased = ratio * ratio
                oneXSliderPosition + (clamped - oneXSliderPosition) * eased
            }
            else -> clamped
        }
    }

    val sliderPosition = remember(timeScale) {
        snapSliderPosition(
            ((ln(timeScale.coerceIn(minTimeScale, maxTimeScale)) - minLog) / (maxLog - minLog)).toFloat()
        )
    }
    fun sliderToTimeScale(position: Float): Float {
        val snappedPosition = snapSliderPosition(position)
        val scale = kotlin.math.exp(minLog + (maxLog - minLog) * snappedPosition).toFloat()
        return if (kotlin.math.abs(scale - 1f) < 0.12f) 1f else scale
    }
    fun formatTimeScale(scale: Float): String {
        return when {
            scale >= 100f -> "${scale.toInt()}x"
            scale >= 10f -> "${"%.1f".format(scale)}x"
            scale >= 1f -> "${"%.2f".format(scale)}x"
            else -> "${"%.2f".format(scale)}x"
        }
    }

    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val allAirports = remember { repository.getAirports() }
    
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    FlightMapBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("?�유 모드", color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "집중모드 ?�정",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "집중모드 ?�작 ???�용???�정??구성?�세??",
                    color = FlightGray,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ?�간 배율 ?�션
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("?�간 배율", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatTimeScale(timeScale),
                                color = FlightPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineMedium
                            )
                            Slider(
                                value = sliderPosition,
                                onValueChange = { onTimeScaleChanged(sliderToTimeScale(it)) },
                                valueRange = 0f..1f,
                                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                            )
                            Text("100x", color = FlightGray, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when {
                            timeScale < 10f -> Text("?�시간에 가깝게 진행?�니??", color = FlightGray, style = MaterialTheme.typography.bodySmall)
                            timeScale < 50f -> Text("빠르�?진행?�니??", color = FlightGray, style = MaterialTheme.typography.bodySmall)
                            else -> Text("매우 빠르�?진행?�니??", color = FlightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
                
                Text(
                    text = "?�정???�료?????�에??집중모드�??�작?�세??",
                    color = FlightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))
                
                // ?�정 ?�료 버튼
                PrimaryFlightButton(
                    text = "?�정 ?�료",
                    onClick = onSaveCompleted,
                    isDestructive = false
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

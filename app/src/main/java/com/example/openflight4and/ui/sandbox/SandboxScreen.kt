package com.example.openflight4and.ui.sandbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.openflight4and.model.Airport
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.theme.FlightGray
import com.example.openflight4and.ui.theme.FlightPrimary
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

private const val TITLE_SANDBOX = "\uC790\uC720 \uBAA8\uB4DC"
private const val TITLE_FOCUS_MODE = "\uC9D1\uC911\uBAA8\uB4DC \uC124\uC815"
private const val BODY_FOCUS_MODE =
    "\uC9D1\uC911\uBAA8\uB4DC \uC2DC\uC791 \uC804 \uC801\uC6A9\uB420 \uC124\uC815\uC744 \uAD6C\uC131\uD558\uC138\uC694."
private const val TITLE_TIME_SCALE = "\uC2DC\uAC04 \uBC30\uC728"
private const val BODY_NEAR_REALTIME = "\uC2E4\uC2DC\uAC04\uC5D0 \uAC00\uAE4C\uAC8C \uC9C4\uD589\uB429\uB2C8\uB2E4."
private const val BODY_FAST = "\uBE60\uB974\uAC8C \uC9C4\uD589\uB429\uB2C8\uB2E4."
private const val BODY_VERY_FAST = "\uB9E4\uC6B0 \uBE60\uB974\uAC8C \uC9C4\uD589\uB429\uB2C8\uB2E4."
private const val BODY_FINISH_GUIDE =
    "\uC124\uC815\uC744 \uC644\uB8CC\uD55C \uB4A4 \uD648\uC5D0\uC11C \uC9D1\uC911\uBAA8\uB4DC\uB97C \uC2DC\uC791\uD558\uC138\uC694."
private const val CTA_DONE = "\uC124\uC815 \uC644\uB8CC"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SandboxScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAirportSelection: (Boolean) -> Unit,
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
        val distanceFromOneX = abs(clamped - oneXSliderPosition)

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
        val scale = exp(minLog + (maxLog - minLog) * snappedPosition).toFloat()
        return if (abs(scale - 1f) < 0.12f) 1f else scale
    }

    fun formatTimeScale(scale: Float): String {
        return when {
            scale >= 100f -> "${scale.toInt()}x"
            scale >= 10f -> "${"%.1f".format(scale)}x"
            scale >= 1f -> "${"%.2f".format(scale)}x"
            else -> "${"%.2f".format(scale)}x"
        }
    }

    FlightMapBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(TITLE_SANDBOX, color = Color.White, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
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
                    text = TITLE_FOCUS_MODE,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = BODY_FOCUS_MODE,
                    color = FlightGray,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(32.dp))

                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            TITLE_TIME_SCALE,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
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
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                            )
                            Text("100x", color = FlightGray, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = when {
                                timeScale < 10f -> BODY_NEAR_REALTIME
                                timeScale < 50f -> BODY_FAST
                                else -> BODY_VERY_FAST
                            },
                            color = FlightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = BODY_FINISH_GUIDE,
                    color = FlightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                PrimaryFlightButton(
                    text = CTA_DONE,
                    onClick = onSaveCompleted,
                    isDestructive = false
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

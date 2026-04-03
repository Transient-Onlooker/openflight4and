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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.openflight4and.R
import com.example.openflight4and.model.Airport
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.theme.FlightGray
import com.example.openflight4and.ui.theme.FlightPrimary
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

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
                    title = {
                        Text(
                            stringResource(R.string.sandbox_title),
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.sandbox_focus_mode_title),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.sandbox_focus_mode_description),
                    color = FlightGray,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(32.dp))

                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            stringResource(R.string.sandbox_time_scale_title),
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
                            Text(
                                stringResource(R.string.sandbox_time_scale_max),
                                color = FlightGray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = when {
                                timeScale < 10f -> stringResource(R.string.sandbox_time_scale_near_realtime)
                                timeScale < 50f -> stringResource(R.string.sandbox_time_scale_fast)
                                else -> stringResource(R.string.sandbox_time_scale_very_fast)
                            },
                            color = FlightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = stringResource(R.string.sandbox_finish_guide),
                    color = FlightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                PrimaryFlightButton(
                    text = stringResource(R.string.sandbox_done),
                    onClick = onSaveCompleted,
                    isDestructive = false
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

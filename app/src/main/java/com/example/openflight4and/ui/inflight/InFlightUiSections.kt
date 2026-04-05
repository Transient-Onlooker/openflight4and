package com.example.openflight4and.ui.inflight

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.openflight4and.R
import com.example.openflight4and.model.FlightDraft
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.MapOverlayPalette
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.theme.FlightGray

data class InFlightPanelColors(
    val panelBackground: Color,
    val panelBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val accentText: Color,
    val divider: Color,
    val trackColor: Color
)

@Composable
fun Window3DIcon(
    modifier: Modifier = Modifier,
    tint: Color
) {
    Canvas(modifier = modifier) {
        val frameInset = size.minDimension * 0.12f
        val frameSize = Size(size.width - frameInset * 2, size.height - frameInset * 2)
        drawRoundRect(
            color = tint,
            topLeft = Offset(frameInset, frameInset),
            size = frameSize,
            cornerRadius = CornerRadius(size.minDimension * 0.18f, size.minDimension * 0.18f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.1f)
        )
        drawLine(
            color = tint,
            start = Offset(size.width / 2f, frameInset * 1.6f),
            end = Offset(size.width / 2f, size.height - frameInset * 1.6f),
            strokeWidth = size.minDimension * 0.08f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(frameInset * 1.7f, size.height / 2f),
            end = Offset(size.width - frameInset * 1.7f, size.height / 2f),
            strokeWidth = size.minDimension * 0.08f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun InFlightStatusPanel(
    draft: FlightDraft,
    remainingSeconds: Long,
    zoomLabel: String,
    timeScaleLabel: String?,
    destinationIataFallback: String,
    colors: InFlightPanelColors,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier,
        backgroundColor = colors.panelBackground,
        borderColor = colors.panelBorder
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.inflight_title),
                        color = colors.accentText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = draft.flightNumber,
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.inflight_remaining_time),
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = com.example.openflight4and.utils.FlightUtils.formatTimer(remainingSeconds),
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                    if (timeScaleLabel != null) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(
                                R.string.inflight_time_scale_format,
                                timeScaleLabel
                            ),
                            color = colors.secondaryText,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.divider)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(draft.origin.iata, color = colors.secondaryText, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Flight,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        draft.destination?.iata ?: destinationIataFallback,
                        color = colors.primaryText,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(zoomLabel, color = colors.secondaryText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun InFlightFloatingControls(
    isAdRewardRunning: Boolean,
    adRewardSecondsRemaining: Int,
    mapPerspective: String,
    isCameraTracking: Boolean,
    overlayPalette: MapOverlayPalette,
    modifier: Modifier = Modifier,
    onShowReward: () -> Unit,
    onToggleStandardPerspective: () -> Unit,
    onToggle3dPerspective: () -> Unit,
    onCenterOnPlane: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.End
    ) {
        SmallFloatingActionButton(
            onClick = onShowReward,
            containerColor = overlayPalette.floatingButtonContainer,
            contentColor = overlayPalette.floatingButtonContent
        ) {
            if (isAdRewardRunning) {
                Text(
                    text = "$adRewardSecondsRemaining",
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Icon(Icons.Default.ConfirmationNumber, contentDescription = null)
            }
        }

        SmallFloatingActionButton(
            onClick = onToggleStandardPerspective,
            containerColor = overlayPalette.floatingButtonContainer,
            contentColor = overlayPalette.floatingButtonContent
        ) {
            Text(
                text = if (mapPerspective == "2d") "2.5D" else "2D",
                style = MaterialTheme.typography.labelSmall
            )
        }

        SmallFloatingActionButton(
            onClick = onToggle3dPerspective,
            containerColor = if (mapPerspective == "3d") {
                MaterialTheme.colorScheme.primary
            } else {
                overlayPalette.floatingButtonContainer
            },
            contentColor = if (mapPerspective == "3d") {
                MaterialTheme.colorScheme.onPrimary
            } else {
                overlayPalette.floatingButtonContent
            }
        ) {
            Window3DIcon(
                modifier = Modifier.size(18.dp),
                tint = if (mapPerspective == "3d") {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    overlayPalette.floatingButtonContent
                }
            )
        }

        SmallFloatingActionButton(
            onClick = onCenterOnPlane,
            containerColor = if (isCameraTracking) {
                MaterialTheme.colorScheme.primary
            } else {
                overlayPalette.floatingButtonContainer
            },
            contentColor = if (isCameraTracking) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                overlayPalette.floatingButtonContent
            }
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = null)
        }
    }
}

@Composable
fun InFlightDebugPanel(
    debugSliderSeconds: Float,
    totalSeconds: Long,
    overlayPalette: MapOverlayPalette,
    onSliderChange: (Float) -> Unit,
    onJumpTo950: () -> Unit,
    onJumpToHalf: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier,
        backgroundColor = overlayPalette.panelBackground,
        borderColor = overlayPalette.panelBorder
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                androidx.compose.ui.res.stringResource(R.string.inflight_debug_title),
                color = overlayPalette.primaryText,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                androidx.compose.ui.res.stringResource(
                    R.string.inflight_debug_elapsed_format,
                    com.example.openflight4and.utils.FlightUtils.formatTimer(debugSliderSeconds.toLong())
                ),
                color = overlayPalette.secondaryText,
                fontSize = 12.sp
            )
            Slider(
                value = debugSliderSeconds,
                onValueChange = onSliderChange,
                valueRange = 0f..totalSeconds.toFloat()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onJumpTo950,
                    modifier = Modifier.weight(1f),
                    enabled = totalSeconds >= 600,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = overlayPalette.primaryText)
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.inflight_debug_shortcut_950))
                }
                OutlinedButton(
                    onClick = onJumpToHalf,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = overlayPalette.primaryText)
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.inflight_debug_shortcut_half))
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.inflight_debug_apply))
                }
            }
        }
    }
}

@Composable
fun InFlightProgressPanel(
    progress: Float,
    timeScaleLabel: String?,
    colors: InFlightPanelColors,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier,
        backgroundColor = colors.panelBackground,
        borderColor = colors.panelBorder
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = colors.primaryText,
                trackColor = colors.trackColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                androidx.compose.ui.res.stringResource(
                    R.string.inflight_progress_complete_format,
                    (progress * 100).toInt()
                ),
                color = colors.secondaryText,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            if (timeScaleLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    androidx.compose.ui.res.stringResource(
                        R.string.inflight_time_scale_format,
                        timeScaleLabel
                    ),
                    color = colors.primaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
fun InFlightActionRow(
    onPause: () -> Unit,
    onGiveUp: () -> Unit,
    colors: InFlightPanelColors,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onPause,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White.copy(alpha = 0.5f),
                contentColor = colors.primaryText
            )
        ) {
            Text(androidx.compose.ui.res.stringResource(R.string.inflight_pause))
        }

        PrimaryFlightButton(
            text = androidx.compose.ui.res.stringResource(R.string.inflight_end_journey),
            onClick = onGiveUp,
            modifier = Modifier.weight(1f),
            isDestructive = true
        )
    }
}

@Composable
fun InFlightPausedOverlay(
    colors: InFlightPanelColors,
    onNavigateToSettings: () -> Unit,
    onResume: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x331F1F1F))
    ) {
        GlassPanel(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            backgroundColor = Color.White,
            borderColor = colors.panelBorder
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.inflight_paused_title),
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.inflight_paused_message),
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primaryText)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.action_settings))
                    }
                    Button(onClick = onResume) {
                        Text(androidx.compose.ui.res.stringResource(R.string.inflight_resume))
                    }
                }
            }
        }
    }
}

@Composable
fun InFlightRewardPromptOverlay(
    colors: InFlightPanelColors,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x331F1F1F))
    ) {
        GlassPanel(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            backgroundColor = Color.White,
            borderColor = colors.panelBorder
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.inflight_reward_title),
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.inflight_reward_message),
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onWatchAd) {
                        Text(androidx.compose.ui.res.stringResource(R.string.inflight_watch_ad))
                    }
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primaryText)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(R.string.action_no))
                    }
                }
            }
        }
    }
}

@Composable
fun InFlightAdRunningOverlay(
    colors: InFlightPanelColors,
    secondsRemaining: Int,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x331F1F1F))
    ) {
        GlassPanel(
            modifier = Modifier.align(Alignment.Center).padding(24.dp),
            backgroundColor = Color.White,
            borderColor = colors.panelBorder
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.inflight_ad_playing_title),
                    color = colors.primaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    androidx.compose.ui.res.stringResource(
                        R.string.inflight_ad_seconds_remaining_format,
                        secondsRemaining
                    ),
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.inflight_ad_playing_message),
                    color = colors.secondaryText,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primaryText)
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
fun InFlightGiveUpDialog(
    onDismiss: () -> Unit,
    onConfirmGiveUp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                androidx.compose.ui.res.stringResource(R.string.inflight_give_up_title),
                color = Color.White
            )
        },
        text = {
            Text(
                androidx.compose.ui.res.stringResource(R.string.inflight_give_up_message),
                color = FlightGray
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmGiveUp) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.action_give_up),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(R.string.action_continue))
            }
        },
        containerColor = Color(0xFF0D0000)
    )
}

package com.example.openflight4and.ui.inflight

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ViewInAr
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.openflight4and.R
import com.example.openflight4and.model.FlightDraft
import com.example.openflight4and.ui.components.MapOverlayPalette

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
fun InFlightStatusPanel(
    draft: FlightDraft,
    displayTimeLabel: String,
    displaySeconds: Long,
    zoomLabel: String,
    timeScaleLabel: String?,
    destinationIataFallback: String,
    colors: InFlightPanelColors,
    modifier: Modifier = Modifier
) {
    LocalGlassPanel(
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
                        text = displayTimeLabel,
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = com.example.openflight4and.utils.FlightUtils.formatTimer(displaySeconds),
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
    isBackgroundSoundEnabled: Boolean,
    mapPerspective: String,
    isCameraTracking: Boolean,
    overlayPalette: MapOverlayPalette,
    modifier: Modifier = Modifier,
    onShowReward: () -> Unit,
    onToggleBackgroundSound: () -> Unit,
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
                    text = "...",
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Icon(Icons.Default.ConfirmationNumber, contentDescription = null)
            }
        }

        SmallFloatingActionButton(
            onClick = onToggleBackgroundSound,
            containerColor = if (isBackgroundSoundEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                overlayPalette.floatingButtonContainer
            },
            contentColor = if (isBackgroundSoundEnabled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                overlayPalette.floatingButtonContent
            }
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = androidx.compose.ui.res.stringResource(R.string.inflight_background_sound_title)
            )
        }

        SmallFloatingActionButton(
            onClick = onToggleStandardPerspective,
            containerColor = overlayPalette.floatingButtonContainer,
            contentColor = overlayPalette.floatingButtonContent
        ) {
            Text(
                text = if (mapPerspective == "2d") "2D" else "2.5D",
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
            Icon(
                Icons.Default.ViewInAr,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
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
    LocalGlassPanel(
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
    LocalGlassPanel(
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

        LocalPrimaryFlightButton(
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
        LocalGlassPanel(
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
        LocalGlassPanel(
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
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x331F1F1F))
    ) {
        LocalGlassPanel(
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
                    androidx.compose.ui.res.stringResource(R.string.tickets_ad_reward_running),
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
    advancedLockEnabled: Boolean = false,
    showEmergencyUnlock: Boolean = false,
    emergencyUnlockEnabled: Boolean = false,
    emergencyUnlockStatusMessage: String? = null,
    onConfirmGiveUp: () -> Unit,
    onUnlockRequested: () -> Unit = onConfirmGiveUp,
    onEmergencyUnlockRequested: () -> Unit = {}
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    androidx.compose.ui.res.stringResource(
                        if (advancedLockEnabled) {
                            R.string.inflight_give_up_locked_message
                        } else {
                            R.string.inflight_give_up_message
                        }
                    ),
                    color = Color(0xFF8C8A80)
                )
                if (showEmergencyUnlock) {
                    Text(
                        emergencyUnlockStatusMessage
                            ?: androidx.compose.ui.res.stringResource(R.string.inflight_emergency_unlock_description),
                        color = Color(0xFF8C8A80),
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = if (advancedLockEnabled) onUnlockRequested else onConfirmGiveUp) {
                Text(
                    androidx.compose.ui.res.stringResource(
                        if (advancedLockEnabled) {
                            R.string.inflight_give_up_unlock_action
                        } else {
                            R.string.action_give_up
                        }
                    ),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showEmergencyUnlock) {
                    TextButton(
                        onClick = onEmergencyUnlockRequested,
                        enabled = emergencyUnlockEnabled
                    ) {
                        Text(
                            androidx.compose.ui.res.stringResource(R.string.inflight_emergency_unlock_action),
                            color = if (emergencyUnlockEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0xFF8C8A80)
                            }
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(androidx.compose.ui.res.stringResource(R.string.action_continue))
                }
            }
        },
        containerColor = Color(0xFF0D0000)
    )
}

@Composable
fun InFlightPinUnlockDialog(
    onDismiss: () -> Unit,
    onVerified: (String, onFailure: () -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val incorrectPinText = androidx.compose.ui.res.stringResource(R.string.inflight_give_up_pin_incorrect)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                androidx.compose.ui.res.stringResource(R.string.settings_focus_lock_pin_unlock_title),
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.settings_focus_lock_pin_unlock_description),
                    color = Color(0xFF8C8A80)
                )
                androidx.compose.material3.OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(6)
                        errorMessage = null
                    },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                    ),
                    label = {
                        Text(androidx.compose.ui.res.stringResource(R.string.settings_focus_lock_pin_input_label))
                    }
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length in 4..6,
                onClick = {
                    onVerified(pin) {
                        errorMessage = incorrectPinText
                        pin = ""
                    }
                }
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.settings_focus_lock_pin_unlock_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(R.string.action_cancel))
            }
        },
        containerColor = Color(0xFF0D0000)
    )
}

@Composable
private fun LocalGlassPanel(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White.copy(alpha = 0.15f),
    borderColor: Color = Color.White.copy(alpha = 0.2f),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                BorderStroke(1.dp, borderColor),
                RoundedCornerShape(16.dp)
            )
    ) {
        content()
    }
}

@Composable
private fun LocalPrimaryFlightButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = Color(0xFF8C8A80).copy(alpha = 0.2f),
            disabledContentColor = Color(0xFF8C8A80)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

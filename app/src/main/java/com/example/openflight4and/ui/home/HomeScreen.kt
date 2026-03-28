package com.example.openflight4and.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.Airport
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.components.RealFlightMap
import com.example.openflight4and.ui.components.rememberMapOverlayPalette
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState

private const val LABEL_CURRENT_LOCATION = "\uD604\uC7AC \uC704\uCE58"
private const val LABEL_TOTAL_FLIGHTS = "\uCD1D \uC644\uB8CC \uBE44\uD589 \uC218"
private const val LABEL_TICKETS = "\uBE44\uD589\uAD8C"
private const val LABEL_START_FLIGHT = "\uBE44\uD589 \uC2DC\uC791"
private const val LABEL_FLIGHT_HISTORY = "\uBE44\uD589 \uAE30\uB85D"
private const val LABEL_TRENDS = "\uD1B5\uACC4\uC640 \uCD94\uC138"
private const val LABEL_SETTINGS = "\uC124\uC815"

@Composable
fun HomeScreen(
    onNavigateToNewFlight: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToTrend: () -> Unit,
    onNavigateToSettings: () -> Unit,
    currentAirport: Airport,
    ticketBalance: Int,
    onNavigateToTickets: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val mapStyle by repository.mapStyle.collectAsState(initial = "standard")
    val mapOverlayStyle by repository.mapOverlayStyle.collectAsState(initial = "dark")
    val totalFlights by repository.totalFlights.collectAsState(initial = 0)
    val overlayPalette = rememberMapOverlayPalette(mapOverlayStyle)

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(currentAirport.latitude, currentAirport.longitude),
            10f
        )
    }

    LaunchedEffect(currentAirport.iata, currentAirport.latitude, currentAirport.longitude) {
        cameraPositionState.position = CameraPosition.fromLatLngZoom(
            LatLng(currentAirport.latitude, currentAirport.longitude),
            10f
        )
    }

    RealFlightMap(
        cameraPositionState = cameraPositionState,
        mapStyle = mapStyle,
        isInteractive = true,
        overlayContent = {
            if (isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .systemBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = LABEL_CURRENT_LOCATION,
                                style = MaterialTheme.typography.labelMedium,
                                color = overlayPalette.secondaryText
                            )
                            Text(
                                text = buildString {
                                    append(currentAirport.cityEn.uppercase())
                                    append(", ")
                                    append(currentAirport.country)
                                },
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = overlayPalette.primaryText
                            )
                            Text(
                                text = "$LABEL_TOTAL_FLIGHTS  $totalFlights",
                                style = MaterialTheme.typography.titleMedium,
                                color = overlayPalette.accentText
                            )
                        }

                        GlassPanel(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .clickable(onClick = onNavigateToTickets),
                            backgroundColor = overlayPalette.panelBackground,
                            borderColor = overlayPalette.panelBorder
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = overlayPalette.accentText
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = LABEL_TICKETS,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = overlayPalette.secondaryText
                                    )
                                    Text(
                                        text = "$ticketBalance",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = overlayPalette.primaryText
                                    )
                                }
                            }
                        }
                    }

                    GlassPanel(
                        modifier = Modifier
                            .padding(start = 20.dp)
                            .width(340.dp)
                            .fillMaxHeight(),
                        backgroundColor = Color.White.copy(alpha = 0.7f),
                        borderColor = overlayPalette.panelBorder
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                HomeMenuItem(
                                    icon = Icons.Default.History,
                                    title = LABEL_FLIGHT_HISTORY,
                                    onClick = onNavigateToHistory,
                                    textColor = Color.Black,
                                    iconTint = Color.White,
                                    chevronTint = overlayPalette.secondaryText
                                )
                                HorizontalDivider(color = overlayPalette.divider)
                                HomeMenuItem(
                                    icon = Icons.Default.Timeline,
                                    title = LABEL_TRENDS,
                                    onClick = onNavigateToTrend,
                                    textColor = Color.Black,
                                    iconTint = Color.White,
                                    chevronTint = overlayPalette.secondaryText
                                )
                                HorizontalDivider(color = overlayPalette.divider)
                                HomeMenuItem(
                                    icon = Icons.Default.Settings,
                                    title = LABEL_SETTINGS,
                                    onClick = onNavigateToSettings,
                                    textColor = Color.Black,
                                    iconTint = Color.White,
                                    chevronTint = overlayPalette.secondaryText
                                )
                            }

                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                PrimaryFlightButton(
                                    text = LABEL_START_FLIGHT,
                                    onClick = onNavigateToNewFlight,
                                    modifier = Modifier.widthIn(max = 260.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .systemBarsPadding(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = LABEL_CURRENT_LOCATION,
                                style = MaterialTheme.typography.labelMedium,
                                color = overlayPalette.secondaryText
                            )
                            Text(
                                text = buildString {
                                    append(currentAirport.cityEn.uppercase())
                                    append(", ")
                                    append(currentAirport.country)
                                },
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = overlayPalette.primaryText
                            )
                            Text(
                                text = "$LABEL_TOTAL_FLIGHTS  $totalFlights",
                                style = MaterialTheme.typography.titleMedium,
                                color = overlayPalette.accentText
                            )
                        }

                        GlassPanel(
                            modifier = Modifier.clickable(onClick = onNavigateToTickets),
                            backgroundColor = overlayPalette.panelBackground,
                            borderColor = overlayPalette.panelBorder
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = overlayPalette.accentText
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = LABEL_TICKETS,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = overlayPalette.secondaryText
                                    )
                                    Text(
                                        text = "$ticketBalance",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = overlayPalette.primaryText
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        PrimaryFlightButton(
                            text = LABEL_START_FLIGHT,
                            onClick = onNavigateToNewFlight
                        )

                        GlassPanel(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color.White.copy(alpha = 0.7f),
                            borderColor = overlayPalette.panelBorder
                        ) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                HomeMenuItem(
                                    icon = Icons.Default.History,
                                    title = LABEL_FLIGHT_HISTORY,
                                    onClick = onNavigateToHistory,
                                    textColor = Color.Black,
                                    iconTint = Color.White,
                                    chevronTint = overlayPalette.secondaryText
                                )
                                HorizontalDivider(color = overlayPalette.divider)
                                HomeMenuItem(
                                    icon = Icons.Default.Timeline,
                                    title = LABEL_TRENDS,
                                    onClick = onNavigateToTrend,
                                    textColor = Color.Black,
                                    iconTint = Color.White,
                                    chevronTint = overlayPalette.secondaryText
                                )
                                HorizontalDivider(color = overlayPalette.divider)
                                HomeMenuItem(
                                    icon = Icons.Default.Settings,
                                    title = LABEL_SETTINGS,
                                    onClick = onNavigateToSettings,
                                    textColor = Color.Black,
                                    iconTint = Color.White,
                                    chevronTint = overlayPalette.secondaryText
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun HomeMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    textColor: Color,
    iconTint: Color,
    chevronTint: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = textColor
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = chevronTint
        )
    }
}

package com.example.openflight4and.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.Airport
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.components.RealFlightMap
import com.example.openflight4and.ui.components.rememberMapOverlayPalette
import com.example.openflight4and.ui.theme.FlightGray
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState

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
                            text = "현재 위치",
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
                            text = "총 완료 비행 수: $totalFlights",
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
                                    text = "비행권",
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
                        text = "비행 시작",
                        onClick = onNavigateToNewFlight
                    )

                    GlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = overlayPalette.panelBackground,
                        borderColor = overlayPalette.panelBorder
                    ) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            HomeMenuItem(
                                icon = Icons.Default.History,
                                title = "비행 기록",
                                onClick = onNavigateToHistory,
                                textColor = overlayPalette.primaryText,
                                iconTint = overlayPalette.iconTint,
                                chevronTint = overlayPalette.secondaryText
                            )
                            HorizontalDivider(color = overlayPalette.divider)
                            HomeMenuItem(
                                icon = Icons.Default.Timeline,
                                title = "통계와 추세",
                                onClick = onNavigateToTrend,
                                textColor = overlayPalette.primaryText,
                                iconTint = overlayPalette.iconTint,
                                chevronTint = overlayPalette.secondaryText
                            )
                            HorizontalDivider(color = overlayPalette.divider)
                            HomeMenuItem(
                                icon = Icons.Default.Settings,
                                title = "설정",
                                onClick = onNavigateToSettings,
                                textColor = overlayPalette.primaryText,
                                iconTint = overlayPalette.iconTint,
                                chevronTint = overlayPalette.secondaryText
                            )
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

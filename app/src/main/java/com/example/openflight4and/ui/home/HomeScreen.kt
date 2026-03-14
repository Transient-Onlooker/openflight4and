package com.example.openflight4and.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.ui.components.GlassPanel
import com.example.openflight4and.ui.components.PrimaryFlightButton
import com.example.openflight4and.ui.components.RealFlightMap
import com.example.openflight4and.ui.theme.FlightGray
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun HomeScreen(
    onNavigateToNewFlight: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToTrend: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    
    // DataStore & Room States
    val mapStyle by repository.mapStyle.collectAsState(initial = "standard")
    val totalFlights by repository.totalFlights.collectAsState(initial = 0)
    
    // Map Camera (Seoul Default)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.5665, 126.9780), 10f)
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
                // Header
                Column {
                    Text(
                        text = "현재 위치",
                        style = MaterialTheme.typography.labelMedium,
                        color = FlightGray
                    )
                    Text(
                        text = "SEOUL, KOREA",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "총 ${totalFlights}회 비행 완료",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Actions
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    PrimaryFlightButton(
                        text = "비행 시작",
                        onClick = onNavigateToNewFlight
                    )

                    GlassPanel(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            HomeMenuItem(
                                icon = Icons.Default.History,
                                title = "비행 기록",
                                onClick = onNavigateToHistory
                            )
                            HorizontalDivider(color = FlightGray.copy(alpha = 0.2f))
                            HomeMenuItem(
                                icon = Icons.Default.Timeline,
                                title = "통계 및 추세",
                                onClick = onNavigateToTrend
                            )
                            HorizontalDivider(color = FlightGray.copy(alpha = 0.2f))
                            HomeMenuItem(
                                icon = Icons.Default.Settings,
                                title = "설정",
                                onClick = onNavigateToSettings
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
    onClick: () -> Unit
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
                tint = FlightGray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = FlightGray.copy(alpha = 0.5f)
        )
    }
}

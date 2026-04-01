package com.example.openflight4and.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.openflight4and.ui.theme.FlightBlack
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings

@Composable
fun RealFlightMap(
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState,
    mapStyle: String = "standard",
    isInteractive: Boolean = true,
    allowRotationGestures: Boolean = false,
    mapContent: (@Composable @GoogleMapComposable () -> Unit)? = null,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null,
    useDarkOverlay: Boolean = true
) {
    val mapType = when (mapStyle) {
        "satellite" -> MapType.SATELLITE
        "hybrid" -> MapType.HYBRID
        else -> MapType.NORMAL
    }

    // 다크 오버레이 알파 (위성 모드일 때는 조금 더 투명하게 하여 지도가 잘 보이게 함)
    val overlayAlpha = if (mapType == MapType.NORMAL) 0.6f else 0.3f

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = mapType,
                isMyLocationEnabled = false,
                isTrafficEnabled = false,
                maxZoomPreference = 20f,
                minZoomPreference = 2f
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false,
                scrollGesturesEnabled = isInteractive,
                zoomGesturesEnabled = isInteractive,
                tiltGesturesEnabled = false,
                rotationGesturesEnabled = isInteractive && allowRotationGestures
            )
        ) {
            // 지도 콘텐츠 (Polyline, Marker 등)
            mapContent?.invoke()
        }

        // 다크 오버레이 - GoogleMap 위에 반투명 레이어 (useDarkOverlay = true 일 때만)
        if (useDarkOverlay) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = FlightBlack.copy(alpha = overlayAlpha))
            }
        }

        // 오버레이 콘텐츠 (UI 패널 등)
        overlayContent?.invoke(this)
    }
}

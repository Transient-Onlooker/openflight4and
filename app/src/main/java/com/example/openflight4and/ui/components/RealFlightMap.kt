package com.example.openflight4and.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.openflight4and.ui.theme.FlightBlack
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings

@Composable
fun RealFlightMap(
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState,
    mapStyle: String = "standard",
    renderMap: Boolean = true,
    isInteractive: Boolean = true,
    allowRotationGestures: Boolean = false,
    mapContent: @Composable (() -> Unit)? = null,
    overlayContent: @Composable (BoxScope.() -> Unit)? = null,
    useDarkOverlay: Boolean = true
) {
    val mapType = when (mapStyle) {
        "satellite" -> MapType.SATELLITE
        "hybrid" -> MapType.HYBRID
        else -> MapType.NORMAL
    }

    val overlayAlpha = if (mapType == MapType.NORMAL) 0.6f else 0.3f

    Box(modifier = modifier.fillMaxSize()) {
        if (renderMap) {
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
                mapContent?.invoke()
            }
        }

        if (renderMap && useDarkOverlay) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = FlightBlack.copy(alpha = overlayAlpha))
            }
        }

        overlayContent?.invoke(this)
    }
}

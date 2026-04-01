package com.example.openflight4and.ui.inflight

import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps3d.GoogleMap3D
import com.google.android.gms.maps3d.Map3DOptions
import com.google.android.gms.maps3d.Map3DView
import com.google.android.gms.maps3d.OnMap3DViewReadyCallback
import com.google.android.gms.maps3d.model.Camera
import com.google.android.gms.maps3d.model.camera
import com.google.android.gms.maps3d.model.latLngAltitude
import kotlin.math.abs

private const val Maps3DTag = "Map3DVRView"
private const val Maps3DTiltDegrees = 60.0
private const val Maps3DRangeMeters = 4500.0
private const val Maps3DAltitudeMeters = 1200.0
private const val Maps3DManualHeadingPerPixel = 0.18
private const val Maps3DManualTiltPerPixel = 0.12

private fun normalizeMaps3DHeading(bearing: Float): Double {
    return ((bearing % 360f) + 360f).toDouble() % 360.0
}

private fun normalizedHeadingDouble(heading: Double): Double {
    return ((heading % 360.0) + 360.0) % 360.0
}

private fun adjustedCameraForDrag(
    camera: Camera,
    deltaX: Float,
    deltaY: Float
): Camera {
    val currentHeading = camera.heading ?: 0.0
    val currentTilt = camera.tilt ?: Maps3DTiltDegrees
    val updatedHeading = normalizedHeadingDouble(currentHeading - deltaX * Maps3DManualHeadingPerPixel)
    val updatedTilt = (currentTilt + deltaY * Maps3DManualTiltPerPixel).coerceIn(5.0, 88.0)
    return camera {
        center = camera.center
        heading = updatedHeading
        tilt = updatedTilt
        roll = camera.roll ?: 0.0
        range = camera.range ?: Maps3DRangeMeters
    }
}

@Composable
fun Map3DVRView(
    currentPosition: LatLng,
    bearing: Float,
    isCameraTracking: Boolean,
    modifier: Modifier = Modifier,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null,
    onUserInteraction: () -> Unit = {},
    onMapError: (Exception) -> Unit = {}
) {
    var map3D by remember { mutableStateOf<GoogleMap3D?>(null) }
    var map3DView by remember { mutableStateOf<Map3DView?>(null) }
    var isUserCameraOverride by remember { mutableStateOf(false) }
    var lastTouchX by remember { mutableStateOf(0f) }
    var lastTouchY by remember { mutableStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val normalizedHeading = remember(bearing) { normalizeMaps3DHeading(bearing) }

    DisposableEffect(lifecycleOwner, map3DView) {
        val view = map3DView
        if (view == null) {
            return@DisposableEffect onDispose {}
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        val currentState = lifecycleOwner.lifecycle.currentState
        if (currentState.isAtLeast(Lifecycle.State.STARTED)) {
            view.onStart()
        }
        if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            view.onResume()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val options = Map3DOptions(
                    centerLat = currentPosition.latitude,
                    centerLng = currentPosition.longitude,
                    centerAlt = Maps3DAltitudeMeters,
                    heading = normalizedHeading,
                    tilt = Maps3DTiltDegrees,
                    roll = 0.0,
                    range = Maps3DRangeMeters
                )
                Map3DView(context, options).apply {
                    map3DView = this
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                isUserCameraOverride = true
                                onUserInteraction()
                                lastTouchX = event.x
                                lastTouchY = event.y
                                true
                            }

                            MotionEvent.ACTION_MOVE -> {
                                if (event.pointerCount == 1) {
                                    val deltaX = event.x - lastTouchX
                                    val deltaY = event.y - lastTouchY
                                    if (abs(deltaX) > 0.5f || abs(deltaY) > 0.5f) {
                                        map3D?.getCamera()?.let { currentCamera ->
                                            map3D?.setCamera(
                                                adjustedCameraForDrag(
                                                    camera = currentCamera,
                                                    deltaX = deltaX,
                                                    deltaY = deltaY
                                                )
                                            )
                                        }
                                        lastTouchX = event.x
                                        lastTouchY = event.y
                                    }
                                    true
                                } else {
                                    false
                                }
                            }

                            MotionEvent.ACTION_POINTER_DOWN -> {
                                isUserCameraOverride = true
                                onUserInteraction()
                                false
                            }

                            else -> false
                        }
                    }
                    onCreate(null)
                    getMap3DViewAsync(
                        object : OnMap3DViewReadyCallback {
                            override fun onMap3DViewReady(googleMap3D: GoogleMap3D) {
                                map3D = googleMap3D
                            }

                            override fun onError(error: Exception) {
                                Log.e(Maps3DTag, "Map3D initialization failed", error)
                                onMapError(error)
                            }
                        }
                    )
                }
            },
            update = { view ->
                if (map3D == null) {
                    view.getMap3DViewAsync(
                        object : OnMap3DViewReadyCallback {
                            override fun onMap3DViewReady(googleMap3D: GoogleMap3D) {
                                map3D = googleMap3D
                            }

                            override fun onError(error: Exception) {
                                Log.e(Maps3DTag, "Map3D update failed", error)
                                onMapError(error)
                            }
                        }
                    )
                }
            },
            onRelease = { view ->
                map3D = null
                map3DView = null
                view.onDestroy()
            }
        )

        overlayContent?.invoke(this)
    }

    LaunchedEffect(isCameraTracking) {
        if (isCameraTracking) {
            isUserCameraOverride = false
        }
    }

    LaunchedEffect(map3D, currentPosition, bearing, isCameraTracking, isUserCameraOverride) {
        if (!isCameraTracking || isUserCameraOverride) {
            return@LaunchedEffect
        }

        map3D?.setCamera(
            camera {
                center = latLngAltitude {
                    latitude = currentPosition.latitude
                    longitude = currentPosition.longitude
                    altitude = Maps3DAltitudeMeters
                }
                heading = normalizedHeading
                tilt = Maps3DTiltDegrees
                roll = 0.0
                range = Maps3DRangeMeters
            }
        )
    }
}

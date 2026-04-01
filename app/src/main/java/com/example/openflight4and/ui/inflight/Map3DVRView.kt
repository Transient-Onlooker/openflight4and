package com.example.openflight4and.ui.inflight

import android.util.Log
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
import com.google.android.gms.maps3d.model.camera
import com.google.android.gms.maps3d.model.latLngAltitude

private const val Maps3DTag = "Map3DVRView"
private const val Maps3DTiltDegrees = 60.0
private const val Maps3DRangeMeters = 4500.0
private const val Maps3DAltitudeMeters = 1200.0

@Composable
fun Map3DVRView(
    currentPosition: LatLng,
    bearing: Float,
    isCameraTracking: Boolean,
    modifier: Modifier = Modifier,
    overlayContent: (@Composable BoxScope.() -> Unit)? = null,
    onMapError: (Exception) -> Unit = {}
) {
    var map3D by remember { mutableStateOf<GoogleMap3D?>(null) }
    var map3DView by remember { mutableStateOf<Map3DView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

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
                    heading = bearing.toDouble(),
                    tilt = Maps3DTiltDegrees,
                    roll = 0.0,
                    range = Maps3DRangeMeters
                )
                Map3DView(context, options).apply {
                    map3DView = this
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

    LaunchedEffect(map3D, currentPosition, bearing, isCameraTracking) {
        if (!isCameraTracking) {
            return@LaunchedEffect
        }

        map3D?.setCamera(
            camera {
                center = latLngAltitude {
                    latitude = currentPosition.latitude
                    longitude = currentPosition.longitude
                    altitude = Maps3DAltitudeMeters
                }
                heading = bearing.toDouble()
                tilt = Maps3DTiltDegrees
                roll = 0.0
                range = Maps3DRangeMeters
            }
        )
    }
}

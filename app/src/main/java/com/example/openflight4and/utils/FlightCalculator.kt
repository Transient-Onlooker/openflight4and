package com.example.openflight4and.utils

import android.location.Location
import com.example.openflight4and.model.Airport
import kotlin.math.roundToInt

object FlightCalculator {
    private const val AVERAGE_FLIGHT_SPEED_KMH = 700.0 // 순항 속도
    private const val TAXI_AND_BUFFER_MINUTES = 30     // 이착륙 및 활주로 시간 보정

    // 두 공항 간 거리 (km)
    fun calculateDistanceKm(origin: Airport, destination: Airport): Int {
        val results = FloatArray(1)
        Location.distanceBetween(
            origin.location.latitude, origin.location.longitude,
            destination.location.latitude, destination.location.longitude,
            results
        )
        // meter to km
        return (results[0] / 1000).roundToInt()
    }

    // 거리 기반 예상 소요 시간 (분)
    fun estimateDurationMinutes(distanceKm: Int): Int {
        val flightHours = distanceKm / AVERAGE_FLIGHT_SPEED_KMH
        val flightMinutes = (flightHours * 60).roundToInt()
        return flightMinutes + TAXI_AND_BUFFER_MINUTES
    }

    // 포맷팅 (예: 1h 25m)
    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}

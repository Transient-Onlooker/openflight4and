package com.example.openflight4and.utils

import android.content.Context
import android.location.Location
import android.provider.Settings
import com.example.openflight4and.model.Airport
import kotlin.math.roundToInt

object FlightUtils {
    private const val AVERAGE_FLIGHT_SPEED_KMH = 800.0
    private const val TAXI_AND_BUFFER_MINUTES = 25 // 대기 시간 현실화

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0] / 1000.0
    }

    fun calculateDistance(origin: Airport, destination: Airport): Double {
        return calculateDistance(origin.latitude, origin.longitude, destination.latitude, destination.longitude)
    }

    /**
     * 두 지점 사이의 방위각(Bearing)을 계산합니다. (비행기 아이콘 회전용)
     */
    fun calculateBearing(origin: Airport, destination: Airport): Float {
        val startLocation = Location("origin").apply {
            latitude = origin.latitude
            longitude = origin.longitude
        }
        val endLocation = Location("destination").apply {
            latitude = destination.latitude
            longitude = destination.longitude
        }
        return startLocation.bearingTo(endLocation)
    }

    fun convertDistance(km: Double, unitSystem: String): Int {
        return if (unitSystem == "mi") (km * 0.621371).roundToInt() else km.roundToInt()
    }

    fun convertDistance(km: Int, unitSystem: String): Int = convertDistance(km.toDouble(), unitSystem)

    fun formatDistance(km: Double, unitSystem: String): String {
        val converted = convertDistance(km, unitSystem)
        return "$converted $unitSystem"
    }

    fun formatDistance(km: Int, unitSystem: String): String = formatDistance(km.toDouble(), unitSystem)

    fun estimateDurationMinutes(distanceKm: Double): Int {
        val flightHours = distanceKm / AVERAGE_FLIGHT_SPEED_KMH
        val flightMinutes = (flightHours * 60).roundToInt()
        return flightMinutes + TAXI_AND_BUFFER_MINUTES
    }

    // "1시간 20분" 형태
    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}시간 ${m}분" else "${m}분"
    }
    
    // 타이머용 "MM:SS" 또는 "HH:MM:SS" 형태
    fun formatTimer(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) 
        else String.format("%02d:%02d", m, s)
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
    }
}

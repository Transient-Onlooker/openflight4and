package com.example.openflight4and.utils

import com.example.openflight4and.model.Airport

object FlightCalculator {
    fun calculateDistanceKm(origin: Airport, destination: Airport): Int {
        return FlightUtils.calculateDistance(origin, destination).toInt()
    }

    fun estimateDurationMinutes(distanceKm: Int): Int {
        return FlightUtils.estimateDurationMinutes(distanceKm.toDouble())
    }

    fun formatDuration(minutes: Int): String {
        return FlightUtils.formatDuration(minutes)
    }
}

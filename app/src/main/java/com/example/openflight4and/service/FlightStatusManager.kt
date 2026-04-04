package com.example.openflight4and.service

object FlightStatusManager {
    fun startFlight(
        originIata: String,
        destinationIata: String,
        originName: String,
        destinationName: String,
        totalSeconds: Long
    ) {
        // Legacy compatibility shim. FlightService.runtimeState is now the single source of truth.
    }

    fun updateProgress(currentSecondsElapsed: Long) {
        // Legacy compatibility shim. Progress is published by FlightService directly.
    }

    fun stopFlight() {
        // Legacy compatibility shim. Stop state is published by FlightService directly.
    }

    fun syncFromService(): Boolean {
        return FlightService.isServiceRunning()
    }
}

package com.example.openflight4and.data

import android.content.Context
import com.example.openflight4and.data.local.AppDatabase
import com.example.openflight4and.model.FlightSession
import kotlinx.coroutines.flow.Flow

class FlightSessionRepository(context: Context) {
    private val flightDao = AppDatabase.getDatabase(context).flightSessionDao()

    val allSessions: Flow<List<FlightSession>> = flightDao.getAllSessions()
    val recentSessions: Flow<List<FlightSession>> = flightDao.getRecentCompletedSessions()
    val totalFlights: Flow<Int> = flightDao.getTotalFlightsCount()
    val totalDistance: Flow<Int?> = flightDao.getTotalDistance()
    val totalFocusMinutes: Flow<Int?> = flightDao.getTotalFocusMinutes()

    suspend fun saveSession(session: FlightSession) {
        flightDao.insertSession(session)
    }
}

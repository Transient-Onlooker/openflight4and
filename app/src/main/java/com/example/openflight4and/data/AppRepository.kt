package com.example.openflight4and.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.openflight4and.data.local.AppDatabase
import com.example.openflight4and.model.Airport
import com.example.openflight4and.model.FlightSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.BufferedReader
import java.io.InputStreamReader

// DataStore Extension
val Context.dataStore by preferencesDataStore(name = "settings")

class AppRepository(private val context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val flightDao = database.flightSessionDao()

    // --- Asset Loader (JSON) ---
    fun getAirports(): List<Airport> {
        return try {
            val inputStream = context.assets.open("airports.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            Json.decodeFromString<List<Airport>>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // --- Room Database (History/Stats) ---
    val allSessions: Flow<List<FlightSession>> = flightDao.getAllSessions()
    val recentSessions: Flow<List<FlightSession>> = flightDao.getRecentCompletedSessions()
    val totalFlights: Flow<Int> = flightDao.getTotalFlightsCount()
    val totalDistance: Flow<Int?> = flightDao.getTotalDistance()
    val totalFocusMinutes: Flow<Int?> = flightDao.getTotalFocusMinutes()

    suspend fun saveSession(session: FlightSession) {
        flightDao.insertSession(session)
    }

    // --- DataStore Keys ---
    companion object {
        val KEY_UNIT_SYSTEM = stringPreferencesKey("unit_system") // "km" or "mi"
        val KEY_MAP_STYLE = stringPreferencesKey("map_style")     // "standard", "satellite", "hybrid"
        val KEY_AIRPLANE_MODE_CHECK = booleanPreferencesKey("airplane_mode_check")
        val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        val KEY_LOCK_LEVEL = stringPreferencesKey("lock_level")   // "soft", "strong", "hardcore"
        val KEY_CURRENT_LOCATION = stringPreferencesKey("current_location") // JSON string of Airport
        val KEY_SANDBOX_TIME_SCALE = stringPreferencesKey("sandbox_time_scale") // "1.0" ~ "100.0"
    }

    // --- Settings Flows ---
    val unitSystem: Flow<String> = context.dataStore.data.map { it[KEY_UNIT_SYSTEM] ?: "km" }
    val mapStyle: Flow<String> = context.dataStore.data.map { it[KEY_MAP_STYLE] ?: "standard" }
    val airplaneModeCheck: Flow<Boolean> = context.dataStore.data.map { it[KEY_AIRPLANE_MODE_CHECK] ?: true }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTIFICATIONS] ?: true }
    val lockLevel: Flow<String> = context.dataStore.data.map { it[KEY_LOCK_LEVEL] ?: "soft" }
    
    // 현재 위치 (저장된 공항)
    val currentLocation: Flow<Airport?> = context.dataStore.data.map { preferences ->
        val json = preferences[KEY_CURRENT_LOCATION]
        Log.d("AppRepository", "Reading current location from DataStore: $json")
        json?.let { 
            try {
                val airport = Json.decodeFromString<Airport>(it)
                Log.d("AppRepository", "Decoded airport: ${airport.iata}")
                airport
            } catch (e: Exception) {
                Log.e("AppRepository", "Failed to decode airport", e)
                null
            }
        }
    }
    
    // 샌드박스 시간 배율
    val sandboxTimeScale: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[KEY_SANDBOX_TIME_SCALE]?.toFloatOrNull() ?: 1f
    }

    // --- Settings Actions ---
    suspend fun setUnitSystem(unit: String) {
        context.dataStore.edit { it[KEY_UNIT_SYSTEM] = unit }
    }

    suspend fun setMapStyle(style: String) {
        context.dataStore.edit { it[KEY_MAP_STYLE] = style }
    }

    suspend fun setAirplaneModeCheck(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AIRPLANE_MODE_CHECK] = enabled }
    }
    
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun setLockLevel(level: String) {
        context.dataStore.edit { it[KEY_LOCK_LEVEL] = level }
    }

    suspend fun setCurrentLocation(airport: Airport) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CURRENT_LOCATION] = Json.encodeToString(Airport.serializer(), airport)
        }
    }

    suspend fun setSandboxTimeScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SANDBOX_TIME_SCALE] = scale.toString()
        }
    }
}

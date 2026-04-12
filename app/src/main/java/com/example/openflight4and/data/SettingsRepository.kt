package com.example.openflight4and.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.example.openflight4and.model.Airport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepository(
    private val context: Context,
    private val reportDataError: (String, Throwable) -> Unit
) {
    private companion object {
        private const val TAG = "SettingsRepository"
    }

    val unitSystem: Flow<String> = context.dataStore.data.map { it[AppPreferenceKeys.KEY_UNIT_SYSTEM] ?: "km" }
    val appLanguage: Flow<String> = context.dataStore.data.map { it[AppPreferenceKeys.KEY_APP_LANGUAGE] ?: "system" }
    val mapStyle: Flow<String> = context.dataStore.data.map { it[AppPreferenceKeys.KEY_MAP_STYLE] ?: "standard" }
    val mapOverlayStyle: Flow<String> = context.dataStore.data.map { it[AppPreferenceKeys.KEY_MAP_OVERLAY_STYLE] ?: "dark" }
    val mapPerspective: Flow<String> = context.dataStore.data.map {
        it[AppPreferenceKeys.KEY_MAP_PERSPECTIVE] ?: "2_5d"
    }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[AppPreferenceKeys.KEY_NOTIFICATIONS] ?: true }
    val notificationUpdateSeconds: Flow<Int> = context.dataStore.data.map {
        (it[AppPreferenceKeys.KEY_NOTIFICATION_UPDATE_SECONDS] ?: 10).coerceIn(1, 30)
    }
    val focusLockEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[AppPreferenceKeys.KEY_FOCUS_LOCK_ENABLED] ?: false
    }
    val focusLockPinEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        !preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_HASH].isNullOrBlank() &&
            !preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_SALT].isNullOrBlank()
    }
    val focusLockAllowedApps: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val json = preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_ALLOWED_APPS]
        if (json.isNullOrBlank()) {
            emptySet()
        } else {
            runCatching {
                Json.decodeFromString<List<String>>(json).toSet()
            }.getOrElse {
                reportDataError("Failed to decode focus lock allowed apps", it)
                emptySet()
            }
        }
    }
    val screenOrientationMode: Flow<String> = context.dataStore.data.map {
        it[AppPreferenceKeys.KEY_SCREEN_ORIENTATION_MODE] ?: "auto"
    }
    val initialOriginSetupCompleted: Flow<Boolean> = context.dataStore.data.map {
        it[AppPreferenceKeys.KEY_INITIAL_ORIGIN_SETUP_COMPLETED] ?: false
    }
    val currentLocation: Flow<Airport?> = context.dataStore.data.map { preferences ->
        val json = preferences[AppPreferenceKeys.KEY_CURRENT_LOCATION]
        Log.d(TAG, "Reading current location from DataStore: $json")
        json?.let {
            try {
                val airport = Json.decodeFromString<Airport>(it)
                Log.d(TAG, "Decoded airport: ${airport.iata}")
                airport
            } catch (e: Exception) {
                reportDataError("Failed to decode current location", e)
                null
            }
        }
    }
    val sandboxTimeScale: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[AppPreferenceKeys.KEY_SANDBOX_TIME_SCALE]?.toFloatOrNull() ?: 1f
    }
    val debugFlightMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AppPreferenceKeys.KEY_DEBUG_FLIGHT_MODE] ?: false
    }

    suspend fun setUnitSystem(unit: String) {
        context.dataStore.edit { it[AppPreferenceKeys.KEY_UNIT_SYSTEM] = unit }
    }

    suspend fun setAppLanguage(language: String) {
        context.dataStore.edit { it[AppPreferenceKeys.KEY_APP_LANGUAGE] = language }
    }

    suspend fun setMapStyle(style: String) {
        context.dataStore.edit { it[AppPreferenceKeys.KEY_MAP_STYLE] = style }
    }

    suspend fun setMapPerspective(perspective: String) {
        context.dataStore.edit { it[AppPreferenceKeys.KEY_MAP_PERSPECTIVE] = perspective }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AppPreferenceKeys.KEY_NOTIFICATIONS] = enabled }
    }

    suspend fun setNotificationUpdateSeconds(seconds: Int) {
        context.dataStore.edit {
            it[AppPreferenceKeys.KEY_NOTIFICATION_UPDATE_SECONDS] = seconds.coerceIn(1, 30)
        }
    }

    suspend fun setFocusLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AppPreferenceKeys.KEY_FOCUS_LOCK_ENABLED] = enabled }
    }

    suspend fun setFocusLockAllowedApps(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_ALLOWED_APPS] =
                Json.encodeToString(packages.sorted())
        }
    }

    suspend fun setFocusLockPin(pin: String) {
        val pinHash = FocusLockPinSecurity.createHash(pin)
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_HASH] = pinHash.hashBase64
            preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_SALT] = pinHash.saltBase64
        }
    }

    suspend fun verifyFocusLockPin(pin: String): Boolean {
        val preferences = context.dataStore.data.map { it }.firstOrNull() ?: return false
        val hash = preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_HASH] ?: return false
        val salt = preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_SALT] ?: return false
        return FocusLockPinSecurity.verify(pin, salt, hash)
    }

    suspend fun changeFocusLockPin(currentPin: String, newPin: String): Boolean {
        if (!verifyFocusLockPin(currentPin)) {
            return false
        }
        setFocusLockPin(newPin)
        return true
    }

    suspend fun clearFocusLockPin(currentPin: String): Boolean {
        if (!verifyFocusLockPin(currentPin)) {
            return false
        }
        context.dataStore.edit { preferences ->
            preferences.remove(AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_HASH)
            preferences.remove(AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_SALT)
        }
        return true
    }

    suspend fun setScreenOrientationMode(mode: String) {
        context.dataStore.edit { it[AppPreferenceKeys.KEY_SCREEN_ORIENTATION_MODE] = mode }
    }

    suspend fun setCurrentLocation(airport: Airport) {
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_CURRENT_LOCATION] = Json.encodeToString(Airport.serializer(), airport)
            preferences[AppPreferenceKeys.KEY_INITIAL_ORIGIN_SETUP_COMPLETED] = true
        }
    }

    suspend fun setInitialOriginSetupCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_INITIAL_ORIGIN_SETUP_COMPLETED] = completed
        }
    }

    suspend fun setSandboxTimeScale(scale: Float) {
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_SANDBOX_TIME_SCALE] = scale.toString()
        }
    }

    suspend fun setDebugFlightMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_DEBUG_FLIGHT_MODE] = enabled
        }
    }
}

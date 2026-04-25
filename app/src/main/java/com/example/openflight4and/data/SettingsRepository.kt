package com.example.openflight4and.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.example.openflight4and.focus.FocusLockUtils
import com.example.openflight4and.model.Airport
import com.example.openflight4and.model.FlightBackgroundSound
import com.example.openflight4and.model.FlightTimeDisplayMode
import java.time.LocalDate
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
    val advancedLockEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[AppPreferenceKeys.KEY_ADVANCED_LOCK_ENABLED] ?: false
    }
    val focusLockPinEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        !preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_HASH].isNullOrBlank() &&
            !preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_SALT].isNullOrBlank()
    }
    val focusLockAllowedApps: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val json = preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_ALLOWED_APPS]
        if (json.isNullOrBlank()) {
            normalizeFocusLockAllowedApps(FocusLockUtils.getDefaultAllowedPackages(context))
        } else {
            runCatching {
                normalizeFocusLockAllowedApps(Json.decodeFromString<List<String>>(json).toSet())
            }.getOrElse {
                reportDataError("Failed to decode focus lock allowed apps", it)
                normalizeFocusLockAllowedApps(emptySet())
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
    val flightBackgroundSoundEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AppPreferenceKeys.KEY_FLIGHT_BACKGROUND_SOUND_ENABLED] ?: true
    }
    val flightBackgroundSound: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AppPreferenceKeys.KEY_FLIGHT_BACKGROUND_SOUND] ?: FlightBackgroundSound.AIRPLANE_WHITE_NOISE
    }
    val flightTimeDisplayMode: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[AppPreferenceKeys.KEY_FLIGHT_TIME_DISPLAY_MODE] ?: FlightTimeDisplayMode.REMAINING
    }
    val emergencyUnlockActiveUntilMillis: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[AppPreferenceKeys.KEY_EMERGENCY_UNLOCK_ACTIVE_UNTIL] ?: 0L
    }
    val canUseEmergencyUnlockToday: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AppPreferenceKeys.KEY_EMERGENCY_UNLOCK_LAST_USED_DATE] != LocalDate.now().toString()
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

    suspend fun setAdvancedLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AppPreferenceKeys.KEY_ADVANCED_LOCK_ENABLED] = enabled }
    }

    suspend fun setFocusLockAllowedApps(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_FOCUS_LOCK_ALLOWED_APPS] =
                Json.encodeToString(normalizeFocusLockAllowedApps(packages).sorted())
        }
    }

    private fun normalizeFocusLockAllowedApps(packages: Set<String>): Set<String> {
        return FocusLockUtils.normalizeAllowedPackages(context, packages)
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

    suspend fun setFlightBackgroundSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_FLIGHT_BACKGROUND_SOUND_ENABLED] = enabled
        }
    }

    suspend fun setFlightBackgroundSound(sound: String) {
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_FLIGHT_BACKGROUND_SOUND] = sound
        }
    }

    suspend fun setFlightTimeDisplayMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_FLIGHT_TIME_DISPLAY_MODE] = mode
        }
    }

    suspend fun startEmergencyUnlock(
        nowMillis: Long = System.currentTimeMillis(),
        durationMinutes: Int = 20
    ): Boolean {
        val today = LocalDate.now().toString()
        var started = false
        context.dataStore.edit { preferences ->
            val alreadyUsedToday =
                preferences[AppPreferenceKeys.KEY_EMERGENCY_UNLOCK_LAST_USED_DATE] == today
            if (alreadyUsedToday) {
                return@edit
            }

            preferences[AppPreferenceKeys.KEY_EMERGENCY_UNLOCK_LAST_USED_DATE] = today
            preferences[AppPreferenceKeys.KEY_EMERGENCY_UNLOCK_ACTIVE_UNTIL] =
                nowMillis + durationMinutes.coerceAtLeast(1) * 60_000L
            started = true
        }
        return started
    }

    suspend fun isEmergencyUnlockActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val preferences = context.dataStore.data.firstOrNull() ?: return false
        val activeUntil = preferences[AppPreferenceKeys.KEY_EMERGENCY_UNLOCK_ACTIVE_UNTIL] ?: 0L
        return activeUntil > nowMillis
    }

    suspend fun clearEmergencyUnlockActive() {
        context.dataStore.edit { preferences ->
            preferences[AppPreferenceKeys.KEY_EMERGENCY_UNLOCK_ACTIVE_UNTIL] = 0L
        }
    }

    suspend fun resetAppSettings() {
        context.dataStore.edit { preferences ->
            preferences.remove(AppPreferenceKeys.KEY_UNIT_SYSTEM)
            preferences.remove(AppPreferenceKeys.KEY_APP_LANGUAGE)
            preferences.remove(AppPreferenceKeys.KEY_MAP_STYLE)
            preferences.remove(AppPreferenceKeys.KEY_MAP_OVERLAY_STYLE)
            preferences.remove(AppPreferenceKeys.KEY_MAP_PERSPECTIVE)
            preferences.remove(AppPreferenceKeys.KEY_NOTIFICATIONS)
            preferences.remove(AppPreferenceKeys.KEY_NOTIFICATION_UPDATE_SECONDS)
            preferences.remove(AppPreferenceKeys.KEY_LOCK_LEVEL)
            preferences.remove(AppPreferenceKeys.KEY_FOCUS_LOCK_ENABLED)
            preferences.remove(AppPreferenceKeys.KEY_ADVANCED_LOCK_ENABLED)
            preferences.remove(AppPreferenceKeys.KEY_FOCUS_LOCK_ALLOWED_APPS)
            preferences.remove(AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_HASH)
            preferences.remove(AppPreferenceKeys.KEY_FOCUS_LOCK_PIN_SALT)
            preferences.remove(AppPreferenceKeys.KEY_SCREEN_ORIENTATION_MODE)
            preferences.remove(AppPreferenceKeys.KEY_CURRENT_LOCATION)
            preferences.remove(AppPreferenceKeys.KEY_INITIAL_ORIGIN_SETUP_COMPLETED)
            preferences.remove(AppPreferenceKeys.KEY_SANDBOX_TIME_SCALE)
            preferences.remove(AppPreferenceKeys.KEY_DEBUG_FLIGHT_MODE)
            preferences.remove(AppPreferenceKeys.KEY_FLIGHT_BACKGROUND_SOUND_ENABLED)
            preferences.remove(AppPreferenceKeys.KEY_FLIGHT_BACKGROUND_SOUND)
            preferences.remove(AppPreferenceKeys.KEY_FLIGHT_TIME_DISPLAY_MODE)
            preferences.remove(AppPreferenceKeys.KEY_EMERGENCY_UNLOCK_LAST_USED_DATE)
            preferences.remove(AppPreferenceKeys.KEY_EMERGENCY_UNLOCK_ACTIVE_UNTIL)
        }
    }

    suspend fun resetAllPreferences() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

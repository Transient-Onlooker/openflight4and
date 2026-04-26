package com.example.openflight4and.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "settings")

object AppPreferenceKeys {
    val KEY_UNIT_SYSTEM = stringPreferencesKey("unit_system")
    val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
    val KEY_MAP_STYLE = stringPreferencesKey("map_style")
    val KEY_MAP_OVERLAY_STYLE = stringPreferencesKey("map_overlay_style")
    val KEY_MAP_PERSPECTIVE = stringPreferencesKey("map_perspective")
    val KEY_NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    val KEY_NOTIFICATION_UPDATE_SECONDS = intPreferencesKey("notification_update_seconds")
    val KEY_LOCK_LEVEL = stringPreferencesKey("lock_level")
    val KEY_FOCUS_LOCK_ENABLED = booleanPreferencesKey("focus_lock_enabled")
    val KEY_ADVANCED_LOCK_ENABLED = booleanPreferencesKey("advanced_lock_enabled")
    val KEY_FOCUS_LOCK_ALLOWED_APPS = stringPreferencesKey("focus_lock_allowed_apps")
    val KEY_FOCUS_LOCK_TEMPORARY_ALLOWED_APPS = stringPreferencesKey("focus_lock_temporary_allowed_apps")
    val KEY_FOCUS_LOCK_TEMPORARY_ALLOWED_UNTIL = longPreferencesKey("focus_lock_temporary_allowed_until")
    val KEY_FOCUS_LOCK_PIN_HASH = stringPreferencesKey("focus_lock_pin_hash")
    val KEY_FOCUS_LOCK_PIN_SALT = stringPreferencesKey("focus_lock_pin_salt")
    val KEY_SCREEN_ORIENTATION_MODE = stringPreferencesKey("screen_orientation_mode")
    val KEY_CURRENT_LOCATION = stringPreferencesKey("current_location")
    val KEY_INITIAL_ORIGIN_SETUP_COMPLETED = booleanPreferencesKey("initial_origin_setup_completed")
    val KEY_SANDBOX_TIME_SCALE = stringPreferencesKey("sandbox_time_scale")
    val KEY_FLIGHT_TICKETS = intPreferencesKey("flight_tickets")
    val KEY_LAST_DAILY_TICKET_DATE = stringPreferencesKey("last_daily_ticket_date")
    val KEY_AD_REWARD_DATE = stringPreferencesKey("ad_reward_date")
    val KEY_AD_WATCH_COUNT_TODAY = intPreferencesKey("ad_watch_count_today")
    val KEY_AD_REWARD_PROGRESS = intPreferencesKey("ad_reward_progress")
    val KEY_EMERGENCY_UNLOCK_LAST_USED_DATE = stringPreferencesKey("emergency_unlock_last_used_date")
    val KEY_EMERGENCY_UNLOCK_ACTIVE_UNTIL = longPreferencesKey("emergency_unlock_active_until")
    val KEY_TICKET_HISTORY = stringPreferencesKey("ticket_history")
    val KEY_USED_REDEEM_CODES = stringPreferencesKey("used_redeem_codes")
    val KEY_INSTALLATION_ID = stringPreferencesKey("installation_id")
    val KEY_ACCOUNT_FIREBASE_UID = stringPreferencesKey("account_firebase_uid")
    val KEY_ACCOUNT_USER_CODE = stringPreferencesKey("account_user_code")
    val KEY_PENDING_TICKET_EVENTS = stringPreferencesKey("pending_ticket_events")
    val KEY_DEBUG_FLIGHT_MODE = booleanPreferencesKey("debug_flight_mode")
    val KEY_FLIGHT_BACKGROUND_SOUND_ENABLED = booleanPreferencesKey("flight_background_sound_enabled")
    val KEY_FLIGHT_BACKGROUND_SOUND = stringPreferencesKey("flight_background_sound")
    val KEY_FLIGHT_BACKGROUND_SOUND_CUSTOM_URI = stringPreferencesKey("flight_background_sound_custom_uri")
    val KEY_FLIGHT_BACKGROUND_SOUND_CUSTOM_NAME = stringPreferencesKey("flight_background_sound_custom_name")
    val KEY_FLIGHT_TIME_DISPLAY_MODE = stringPreferencesKey("flight_time_display_mode")
}

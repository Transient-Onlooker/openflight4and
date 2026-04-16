package com.example.openflight4and.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.openflight4and.BuildConfig
import com.example.openflight4and.MainActivity
import com.example.openflight4and.R
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.model.FlightSession
import com.example.openflight4and.focus.FocusLockUtils
import com.example.openflight4and.model.Airport
import com.example.openflight4and.utils.FlightUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit

// DataStore Extension
val android.content.Context.dataStore by preferencesDataStore(name = "settings")

class FlightService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private lateinit var repository: AppRepository
    private lateinit var focusLockOverlayController: FocusLockOverlayController
    private var focusLockSettingsJob: Job? = null
    private var focusLockMonitorJob: Job? = null
    private var focusLockEnabled = false
    private var focusLockAllowedPackages = defaultFocusLockAllowedPackages
    private var emergencyUnlockUntilMillis = 0L
    private var currentDurationMinutes: Int = 0
    private var currentFlightNumber: String = ""
    private var currentOriginName: String = ""
    private var currentDestinationName: String = ""
    private var currentSeatNumber: String? = null
    private var currentFocusCategory: String? = null
    private var currentDistanceKm: Int = 0
    private var flightStartedAtMillis: Long = 0L
    private var currentNotificationUpdateSeconds: Int = 10

    companion object {
        const val CHANNEL_ID = "flight_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "stop_flight"
        const val ACTION_PAUSE = "pause_flight"
        const val ACTION_RESUME = "resume_flight"
        private const val TAG = "FlightService"
        private const val MIN_SESSION_HISTORY_MINUTES = 10

        private val defaultFocusLockAllowedPackages = setOf(
        BuildConfig.APPLICATION_ID,
        "com.android.settings",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
        )

        data class RuntimeState(
            val isRunning: Boolean = false,
            val isPaused: Boolean = false,
            val secondsElapsed: Long = 0L,
            val totalSeconds: Long = 0L,
            val ticketCharged: Boolean = false,
            val originIata: String = "N/A",
            val destinationIata: String = "N/A"
        )

        // UI ?먯꽌 ?쒕퉬???곹깭瑜??쎄린 ?꾪븳 怨듦컻 蹂??
        @Volatile private var instance: FlightService? = null
        @Volatile private var _secondsElapsed = 0L
        @Volatile private var _totalSeconds = 0L
        @Volatile private var _isRunning = false
        @Volatile private var _isPaused = false
        @Volatile private var _ticketCharged = false
        @Volatile private var _pendingJumpSeconds: Long? = null
        @Volatile private var _isInFlightScreenVisible = false
        private val _runtimeState = MutableStateFlow(RuntimeState())
        val runtimeState: StateFlow<RuntimeState> = _runtimeState.asStateFlow()

        fun isServiceRunning(): Boolean = _isRunning
        fun isPaused(): Boolean = _isPaused
        fun getSecondsElapsed(): Long = _secondsElapsed
        fun getTotalSeconds(): Long = _totalSeconds
        fun getServiceInstance(): FlightService? = instance
        fun isTicketCharged(): Boolean = _ticketCharged
        fun markTicketCharged() {
            _ticketCharged = true
            publishRuntimeState()
        }
        fun jumpToElapsedSeconds(seconds: Long) {
            _pendingJumpSeconds = seconds
        }
        fun setInFlightScreenVisible(isVisible: Boolean) {
            _isInFlightScreenVisible = isVisible
            instance?.handleInFlightScreenVisibilityChanged(isVisible)
        }

        private fun publishRuntimeState() {
            _runtimeState.value = RuntimeState(
                isRunning = _isRunning,
                isPaused = _isPaused,
                secondsElapsed = _secondsElapsed,
                totalSeconds = _totalSeconds,
                ticketCharged = _ticketCharged,
                originIata = instance?.currentOriginIata ?: "N/A",
                destinationIata = instance?.currentDestinationIata ?: "N/A"
            )
        }
    }

    private var currentOriginIata: String = "N/A"
    private var currentDestinationIata: String = "N/A"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate()")
        instance = this
        repository = AppRepository(applicationContext)
        focusLockOverlayController = FocusLockOverlayController(applicationContext)
        createNotificationChannel()
        observeFocusLockSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand()")

        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Service stop requested")
            serviceScope.launch {
                persistFlightSession(isCompleted = false)
                repository.clearEmergencyUnlockActive()
                stopFocusLockMonitor()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_PAUSE) {
            Log.d(TAG, "Service pause requested")
            _isPaused = true
            publishRuntimeState()
            refreshOngoingNotification(force = true)
            return START_STICKY
        }

        if (intent?.action == ACTION_RESUME) {
            Log.d(TAG, "Service resume requested")
            _isPaused = false
            publishRuntimeState()
            refreshOngoingNotification(force = true)
            return START_STICKY
        }

        val durationMinutes = intent?.getIntExtra("duration_minutes", 0) ?: 0
        val totalSeconds = (durationMinutes * 60).toLong()
        val originIata = intent?.getStringExtra("origin_iata") ?: "N/A"
        val destinationIata = intent?.getStringExtra("destination_iata") ?: "N/A"
        val originName = intent?.getStringExtra("origin_name") ?: ""
        val destinationName = intent?.getStringExtra("destination_name") ?: ""
        val flightNumber = intent?.getStringExtra("flight_number") ?: ""
        val seatNumber = intent?.getStringExtra("seat_number")
        val focusCategory = intent?.getStringExtra("focus_category")
        val distanceKm = intent?.getIntExtra("distance_km", 0) ?: 0
        val timeScale = intent?.getFloatExtra("time_scale", 1f) ?: 1f
        val notificationUpdateSeconds =
            (intent?.getIntExtra("notification_update_seconds", 10) ?: 10).coerceIn(1, 30)
        currentNotificationUpdateSeconds = notificationUpdateSeconds

        Log.d(
            TAG,
            "Starting flight: $originIata -> $destinationIata, Duration: $durationMinutes min, TimeScale: $timeScale, NotificationUpdateSeconds: $notificationUpdateSeconds"
        )

        // ?쒕퉬???곹깭 珥덇린??
        _isRunning = true
        _isPaused = false
        _totalSeconds = totalSeconds
        _secondsElapsed = 0L
        _ticketCharged = false
        _pendingJumpSeconds = null
        _isInFlightScreenVisible = false
        currentOriginIata = originIata
        currentDestinationIata = destinationIata
        currentDurationMinutes = durationMinutes
        currentFlightNumber = flightNumber
        currentOriginName = originName
        currentDestinationName = destinationName
        currentSeatNumber = seatNumber
        currentFocusCategory = focusCategory
        currentDistanceKm = distanceKm
        flightStartedAtMillis = System.currentTimeMillis()
        publishRuntimeState()
        if (focusLockEnabled) {
            startFocusLockMonitor()
        }

        // 珥덇린 ?뚮┝怨??④퍡 ?ш렇?쇱슫???쒖옉
        val initialContent = "$originIata -> $destinationIata | ${getString(R.string.flight_notification_preparing)}"
        startForeground(NOTIFICATION_ID, createNotification(initialContent, originIata, destinationIata))
        Log.d(TAG, "Foreground service started with notification ID: $NOTIFICATION_ID")
        updateNotification(
            createNotification(
                "$originIata -> $destinationIata | ${FlightUtils.formatTimer(totalSeconds)}",
                originIata,
                destinationIata
            )
        )

        startTimer(
            totalSeconds,
            originIata,
            destinationIata,
            originName,
            destinationName,
            timeScale
        )
        return START_STICKY
    }

    private fun observeFocusLockSettings() {
        focusLockSettingsJob?.cancel()
        focusLockSettingsJob = serviceScope.launch {
            launch {
                repository.focusLockEnabled.collect { enabled ->
                    focusLockEnabled = enabled
                    if (_isRunning && enabled) {
                        startFocusLockMonitor()
                    } else {
                        stopFocusLockMonitor()
                    }
                }
            }
            launch {
                repository.focusLockAllowedApps.collect { packages ->
                    focusLockAllowedPackages = defaultFocusLockAllowedPackages + packages
                }
            }
            launch {
                repository.notificationUpdateSeconds.collect { seconds ->
                    currentNotificationUpdateSeconds = seconds.coerceIn(1, 30)
                    refreshOngoingNotification(force = !_isInFlightScreenVisible)
                }
            }
            launch {
                repository.emergencyUnlockActiveUntilMillis.collect { activeUntil ->
                    emergencyUnlockUntilMillis = activeUntil
                    if (activeUntil > System.currentTimeMillis()) {
                        withContext(Dispatchers.Main) {
                            focusLockOverlayController.hide()
                        }
                    }
                }
            }
        }
    }

    private fun startFocusLockMonitor() {
        if (focusLockMonitorJob?.isActive == true) {
            return
        }

        focusLockMonitorJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive && _isRunning && focusLockEnabled) {
                val hasPermissions =
                    FocusLockUtils.hasUsageAccess(applicationContext) &&
                    FocusLockUtils.canDrawOverlays(applicationContext)

                if (!hasPermissions) {
                    withContext(Dispatchers.Main) {
                        focusLockOverlayController.hide()
                    }
                    delay(1000)
                    continue
                }

                val foregroundPackage = FocusLockUtils.getForegroundPackage(applicationContext)
                val isEmergencyUnlockActive = emergencyUnlockUntilMillis > System.currentTimeMillis()
                val shouldBlock = when {
                    isEmergencyUnlockActive -> false
                    foregroundPackage == null -> focusLockOverlayController.isShowing()
                    foregroundPackage in focusLockAllowedPackages -> false
                    else -> true
                }

                withContext(Dispatchers.Main) {
                    if (shouldBlock) {
                        focusLockOverlayController.show(
                            originIata = currentOriginIata,
                            destinationIata = currentDestinationIata,
                            durationMinutes = currentDurationMinutes,
                            allowedPackages = focusLockAllowedPackages
                        )
                    } else {
                        focusLockOverlayController.hide()
                    }
                }

                delay(750)
            }
        }
    }

    private fun stopFocusLockMonitor() {
        focusLockMonitorJob?.cancel()
        focusLockMonitorJob = null
        focusLockOverlayController.hide()
    }

    private fun startTimer(
        totalSeconds: Long,
        originIata: String,
        destinationIata: String,
        originName: String,
        destinationName: String,
        timeScale: Float = 1f
    ) {
        Log.d(TAG, "Timer started: $totalSeconds seconds, TimeScale: $timeScale")

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var lastNotificationTime = System.currentTimeMillis()
            _secondsElapsed = _secondsElapsed.coerceIn(0L, totalSeconds)

            while (_secondsElapsed < totalSeconds) {
                while (_isPaused) {
                    delay(200)
                }

                _pendingJumpSeconds?.let { jumpTarget ->
                    _secondsElapsed = jumpTarget.coerceIn(0L, totalSeconds)
                    _pendingJumpSeconds = null
                    publishRuntimeState()
                }

                val safeTimeScale = timeScale.coerceIn(0.001f, 1000f)
                val delayMs = (1000f / safeTimeScale).toLong()
                delay(delayMs.coerceAtLeast(100)) // 理쒖냼 100ms (?덈Т 鍮좊Ⅸ ?낅뜲?댄듃 諛⑹?)

                if (_isPaused) {
                    continue
                }

                _pendingJumpSeconds?.let { jumpTarget ->
                    _secondsElapsed = jumpTarget.coerceIn(0L, totalSeconds)
                    _pendingJumpSeconds = null
                    publishRuntimeState()
                }

                _secondsElapsed = (_secondsElapsed + 1).coerceAtMost(totalSeconds)

                // ?쒕퉬???곹깭 ?낅뜲?댄듃 (UI ?곕룞??
                publishRuntimeState()

                // ?뚮┝李?媛깆떊 (?ㅼ젣 ?쒓컙 湲곗? 30 珥덈쭏?ㅻ쭔 ?낅뜲?댄듃 - ?깅뒫 理쒖쟻??
                val currentTime = System.currentTimeMillis()
                val remaining = totalSeconds - _secondsElapsed

                // ?⑥? ?쒓컙??0 ?대㈃ 利됱떆 ?꾨즺 泥섎━
                if (remaining <= 0) {
                    Log.d(TAG, "Flight completed! Remaining: 0")
                    val completedText = "$originIata -> $destinationIata | ${getString(R.string.flight_notification_completed)}"
                    updateNotification(createNotification(completedText, originIata, destinationIata, true))

                    persistFlightSession(isCompleted = true)
                    saveDestinationAsCurrentLocation(destinationIata)
                    repository.clearEmergencyUnlockActive()
                    _isRunning = false
                    _isPaused = false
                    _secondsElapsed = totalSeconds
                    publishRuntimeState()
                    stopFocusLockMonitor()

                    // ?뚮┝ ?쒓굅 (?꾨즺 ??5 珥????먮룞 ??젣)
                    withContext(Dispatchers.IO) {
                        delay(5000)
                        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(NOTIFICATION_ID)
                    }

                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                if (
                    !_isInFlightScreenVisible &&
                    currentTime - lastNotificationTime >= currentNotificationUpdateSeconds * 1000L
                ) {
                    refreshOngoingNotification(force = true)
                    Log.d(TAG, "Notification updated: $remainingText remaining")
                    lastNotificationTime = currentTime
                }
            }

            // 猷⑦봽 ?꾨즺 ??泥섎━ (?덉쟾?μ튂)
            Log.d(TAG, "Flight completed! (loop finished)")
            val completedText = "$originIata -> $destinationIata | ${getString(R.string.flight_notification_completed)}"
            updateNotification(createNotification(completedText, originIata, destinationIata, true))

            persistFlightSession(isCompleted = true)
            saveDestinationAsCurrentLocation(destinationIata)
            repository.clearEmergencyUnlockActive()
            _isRunning = false
            _isPaused = false
            _secondsElapsed = totalSeconds
            publishRuntimeState()
            stopFocusLockMonitor()

            // ?뚮┝ ?쒓굅 (?꾨즺 ??5 珥????먮룞 ??젣)
            withContext(Dispatchers.IO) {
                delay(5000)
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handleInFlightScreenVisibilityChanged(isVisible: Boolean) {
        if (_isRunning && !isVisible) {
            refreshOngoingNotification(force = true)
        }

        if (isVisible || !_isRunning || _totalSeconds <= 0L) {
            return
        }
    }

    private fun refreshOngoingNotification(force: Boolean = false) {
        if (!_isRunning || _totalSeconds <= 0L) {
            return
        }
        if (!force && _isInFlightScreenVisible) {
            return
        }

        val contentText = if (_isPaused) {
            "$currentOriginIata -> $currentDestinationIata | ${getString(R.string.inflight_paused_title)}"
        } else {
            val remaining = (_totalSeconds - _secondsElapsed).coerceAtLeast(0L)
            "$currentOriginIata -> $currentDestinationIata | ${FlightUtils.formatTimer(remaining)}"
        }

        updateNotification(
            createNotification(
                content = contentText,
                originIata = currentOriginIata,
                destinationIata = currentDestinationIata
            )
        )
    }

    private suspend fun persistFlightSession(isCompleted: Boolean) {
        if (currentFlightNumber.isBlank()) {
            return
        }

        val elapsedMinutes = ((System.currentTimeMillis() - flightStartedAtMillis) / 1000 / 60).toInt()
        if (elapsedMinutes < MIN_SESSION_HISTORY_MINUTES) {
            Log.d(TAG, "Skipping session history save because duration is under $MIN_SESSION_HISTORY_MINUTES minutes")
            return
        }
        val progress = if (_totalSeconds > 0L) {
            (_secondsElapsed.toFloat() / _totalSeconds.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        repository.saveSession(
            FlightSession(
                flightNumber = currentFlightNumber,
                originIata = currentOriginIata,
                originName = currentOriginName,
                destinationIata = currentDestinationIata,
                destinationName = currentDestinationName,
                seatNumber = currentSeatNumber,
                focusCategory = currentFocusCategory,
                distanceKm = (currentDistanceKm * progress).toInt(),
                durationMinutes = elapsedMinutes,
                startTime = flightStartedAtMillis,
                endTime = System.currentTimeMillis(),
                isCompleted = isCompleted
            )
        )
    }

    /**
     * ?꾩갑吏瑜??꾩옱 ?꾩튂濡????(DataStore)
     */
    private suspend fun saveDestinationAsCurrentLocation(destinationIata: String) {
        try {
            // 怨듯빆 紐⑸줉?먯꽌 ?꾩갑吏 李얘린
            val inputStream = applicationContext.assets.open("airports.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            val allAirports = Json.decodeFromString<List<Airport>>(jsonString)

            val destinationAirport = allAirports.find { it.iata == destinationIata }

            if (destinationAirport != null) {
                // DataStore ?????- AppRepository ? ?숈씪?????ъ슜
                val key = com.example.openflight4and.data.AppRepository.KEY_CURRENT_LOCATION
                Log.d(TAG, "Saving current location with key: $key")
                applicationContext.dataStore.edit { preferences ->
                    preferences[key] =
                        Json.encodeToString(Airport.serializer(), destinationAirport)
                    Log.d(TAG, "DataStore updated: ${preferences[key]}")
                }
                Log.d(TAG, "Current location saved to: $destinationIata")
            } else {
                Log.d(TAG, "Destination airport not found: $destinationIata")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save current location", e)
        }
    }

    private fun createNotification(
        content: String,
        originIata: String,
        destinationIata: String,
        isCompleted: Boolean = false
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_INFLIGHT, true)
            putExtra(MainActivity.EXTRA_ORIGIN_IATA, originIata)
            putExtra(MainActivity.EXTRA_DESTINATION_IATA, destinationIata)
            putExtra(MainActivity.EXTRA_DURATION_MINUTES, (_totalSeconds / 60L).toInt())
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(
                if (isCompleted) getString(R.string.flight_notification_title_completed)
                else getString(R.string.flight_notification_title_active)
            )
            .setContentText(content)
            .setSubText("$originIata -> $destinationIata")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pendingIntent)
            .setOngoing(!isCompleted)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun updateNotification(notification: Notification) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.flight_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = getString(R.string.flight_notification_channel_description)
            channel.enableLights(true)
            channel.enableVibration(false)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy()")
        _isRunning = false
        _isPaused = false
        _ticketCharged = false
        _pendingJumpSeconds = null
        _isInFlightScreenVisible = false
        emergencyUnlockUntilMillis = 0L
        publishRuntimeState()
        instance = null
        stopFocusLockMonitor()
        focusLockSettingsJob?.cancel()
        timerJob?.cancel()
        serviceScope.cancel()
        focusLockOverlayController.destroy()
        super.onDestroy()
    }
}


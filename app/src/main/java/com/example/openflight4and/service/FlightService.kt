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
import com.example.openflight4and.MainActivity
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.focus.FocusLockUtils
import com.example.openflight4and.model.Airport
import com.example.openflight4and.utils.FlightUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
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
    private var currentDurationMinutes: Int = 0
    private val focusLockAllowedPackages = setOf(
        "com.example.openflight4and",
        "com.android.settings",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller"
    )

    companion object {
        const val CHANNEL_ID = "flight_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "stop_flight"
        const val ACTION_PAUSE = "pause_flight"
        const val ACTION_RESUME = "resume_flight"
        private const val TAG = "FlightService"

        // UI ?먯꽌 ?쒕퉬???곹깭瑜??쎄린 ?꾪븳 怨듦컻 蹂??
        @Volatile private var instance: FlightService? = null
        @Volatile private var _secondsElapsed = 0L
        @Volatile private var _totalSeconds = 0L
        @Volatile private var _isRunning = false
        @Volatile private var _isPaused = false
        @Volatile private var _ticketCharged = false
        @Volatile private var _pendingJumpSeconds: Long? = null
        @Volatile private var _isInFlightScreenVisible = false

        fun isServiceRunning(): Boolean = _isRunning
        fun isPaused(): Boolean = _isPaused
        fun getSecondsElapsed(): Long = _secondsElapsed
        fun getTotalSeconds(): Long = _totalSeconds
        fun getServiceInstance(): FlightService? = instance
        fun isTicketCharged(): Boolean = _ticketCharged
        fun markTicketCharged() {
            _ticketCharged = true
        }
        fun jumpToElapsedSeconds(seconds: Long) {
            _pendingJumpSeconds = seconds
        }
        fun setInFlightScreenVisible(isVisible: Boolean) {
            _isInFlightScreenVisible = isVisible
            instance?.handleInFlightScreenVisibilityChanged(isVisible)
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
            stopFocusLockMonitor()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_PAUSE) {
            Log.d(TAG, "Service pause requested")
            _isPaused = true
            return START_STICKY
        }

        if (intent?.action == ACTION_RESUME) {
            Log.d(TAG, "Service resume requested")
            _isPaused = false
            return START_STICKY
        }

        val durationMinutes = intent?.getIntExtra("duration_minutes", 0) ?: 0
        val totalSeconds = (durationMinutes * 60).toLong()
        val originIata = intent?.getStringExtra("origin_iata") ?: "N/A"
        val destinationIata = intent?.getStringExtra("destination_iata") ?: "N/A"
        val originName = intent?.getStringExtra("origin_name") ?: ""
        val destinationName = intent?.getStringExtra("destination_name") ?: ""
        val timeScale = intent?.getFloatExtra("time_scale", 1f) ?: 1f
        val notificationUpdateSeconds =
            (intent?.getIntExtra("notification_update_seconds", 10) ?: 10).coerceIn(1, 30)

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
        if (focusLockEnabled) {
            startFocusLockMonitor()
        }

        // 珥덇린 ?뚮┝怨??④퍡 ?ш렇?쇱슫???쒖옉
        val initialContent = "$originIata -> $destinationIata | 비행 준비 중..."
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
            timeScale,
            notificationUpdateSeconds
        )
        return START_STICKY
    }

    private fun observeFocusLockSettings() {
        focusLockSettingsJob?.cancel()
        focusLockSettingsJob = serviceScope.launch {
            repository.focusLockEnabled.collect { enabled ->
                focusLockEnabled = enabled
                if (_isRunning && enabled) {
                    startFocusLockMonitor()
                } else {
                    stopFocusLockMonitor()
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
                val shouldBlock = when {
                    foregroundPackage == null -> focusLockOverlayController.isShowing()
                    foregroundPackage in focusLockAllowedPackages -> false
                    else -> true
                }

                withContext(Dispatchers.Main) {
                    if (shouldBlock) {
                        focusLockOverlayController.show(
                            originIata = currentOriginIata,
                            destinationIata = currentDestinationIata,
                            durationMinutes = currentDurationMinutes
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
        timeScale: Float = 1f,
        notificationUpdateSeconds: Int = 10
    ) {
        Log.d(TAG, "Timer started: $totalSeconds seconds, TimeScale: $timeScale")

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var secondsElapsed = 0L
            var lastNotificationTime = System.currentTimeMillis()

            while (secondsElapsed < totalSeconds) {
                while (_isPaused) {
                    delay(200)
                }
                val safeTimeScale = timeScale.coerceIn(0.001f, 1000f)
                val delayMs = (1000f / safeTimeScale).toLong()
                delay(delayMs.coerceAtLeast(100)) // 理쒖냼 100ms (?덈Т 鍮좊Ⅸ ?낅뜲?댄듃 諛⑹?)
                _pendingJumpSeconds?.let { jumpTarget ->
                    secondsElapsed = jumpTarget.coerceIn(0L, totalSeconds)
                    _pendingJumpSeconds = null
                }
                secondsElapsed++

                // ?쒕퉬???곹깭 ?낅뜲?댄듃 (UI ?곕룞??
                _secondsElapsed = secondsElapsed
                FlightStatusManager.updateProgress(secondsElapsed)

                // ?뚮┝李?媛깆떊 (?ㅼ젣 ?쒓컙 湲곗? 30 珥덈쭏?ㅻ쭔 ?낅뜲?댄듃 - ?깅뒫 理쒖쟻??
                val currentTime = System.currentTimeMillis()
                val remaining = totalSeconds - secondsElapsed

                // ?⑥? ?쒓컙??0 ?대㈃ 利됱떆 ?꾨즺 泥섎━
                if (remaining <= 0) {
                    Log.d(TAG, "Flight completed! Remaining: 0")
                    FlightStatusManager.stopFlight()
                    val completedText = "$originIata -> $destinationIata | 비행 완료!"
                    updateNotification(createNotification(completedText, originIata, destinationIata, true))

                    // ?꾩갑吏瑜??꾩옱 ?꾩튂濡????(?ㅼ쓬 鍮꾪뻾???꾪븳 異쒕컻吏)
                    saveDestinationAsCurrentLocation(destinationIata)

                    // ?뚮┝ ?쒓굅 (?꾨즺 ??5 珥????먮룞 ??젣)
                    withContext(Dispatchers.IO) {
                        delay(5000)
                        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(NOTIFICATION_ID)
                    }

                    _isRunning = false
                    stopFocusLockMonitor()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                if (
                    !_isInFlightScreenVisible &&
                    currentTime - lastNotificationTime >= notificationUpdateSeconds * 1000L
                ) {
                    val remainingText = FlightUtils.formatTimer(remaining)
                    val contentText = "$originIata -> $destinationIata | $remainingText"
                    updateNotification(createNotification(contentText, originIata, destinationIata))
                    Log.d(TAG, "Notification updated: $remainingText remaining")
                    lastNotificationTime = currentTime
                }
            }

            // 猷⑦봽 ?꾨즺 ??泥섎━ (?덉쟾?μ튂)
            Log.d(TAG, "Flight completed! (loop finished)")
            FlightStatusManager.stopFlight()
            val completedText = "$originIata -> $destinationIata | 비행 완료!"
            updateNotification(createNotification(completedText, originIata, destinationIata, true))

            // ?꾩갑吏瑜??꾩옱 ?꾩튂濡????(?ㅼ쓬 鍮꾪뻾???꾪븳 異쒕컻吏)
            saveDestinationAsCurrentLocation(destinationIata)

            // ?뚮┝ ?쒓굅 (?꾨즺 ??5 珥????먮룞 ??젣)
            withContext(Dispatchers.IO) {
                delay(5000)
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
            }

            _isRunning = false
            stopFocusLockMonitor()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handleInFlightScreenVisibilityChanged(isVisible: Boolean) {
        if (isVisible || !_isRunning || _totalSeconds <= 0L) {
            return
        }

        val remaining = (_totalSeconds - _secondsElapsed).coerceAtLeast(0L)
        val contentText = "$currentOriginIata -> $currentDestinationIata | ${FlightUtils.formatTimer(remaining)}"
        updateNotification(createNotification(contentText, currentOriginIata, currentDestinationIata))
    }

    /**
     * ?꾩갑吏瑜??꾩옱 ?꾩튂濡????(DataStore)
     */
    private fun saveDestinationAsCurrentLocation(destinationIata: String) {
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
                runBlocking {
                    applicationContext.dataStore.edit { preferences ->
                        preferences[key] =
                            Json.encodeToString(Airport.serializer(), destinationAirport)
                        Log.d(TAG, "DataStore updated: ${preferences[key]}")
                    }
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
            .setContentTitle(if (isCompleted) "\uBE44\uD589 \uC644\uB8CC" else "\uD604\uC7AC \uBE44\uD589 \uC911")
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
                "\uBE44\uD589 \uC0C1\uD0DC \uC54C\uB9BC",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "\uBE44\uD589 \uC911 \uC0C1\uD0DC\uC640 \uB0A8\uC740 \uC2DC\uAC04\uC744 \uD45C\uC2DC\uD569\uB2C8\uB2E4."
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
        instance = null
        stopFocusLockMonitor()
        focusLockSettingsJob?.cancel()
        timerJob?.cancel()
        serviceScope.cancel()
        focusLockOverlayController.destroy()
        FlightStatusManager.stopFlight()
        super.onDestroy()
    }
}


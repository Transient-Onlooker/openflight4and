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
import com.example.openflight4and.model.Airport
import com.example.openflight4and.utils.FlightUtils
import kotlinx.coroutines.*
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

    companion object {
        const val CHANNEL_ID = "flight_service_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "stop_flight"
        const val ACTION_PAUSE = "pause_flight"
        const val ACTION_RESUME = "resume_flight"
        private const val TAG = "FlightService"

        // UI 에서 서비스 상태를 읽기 위한 공개 변수
        @Volatile private var instance: FlightService? = null
        @Volatile private var _secondsElapsed = 0L
        @Volatile private var _totalSeconds = 0L
        @Volatile private var _isRunning = false
        @Volatile private var _isPaused = false
        @Volatile private var _ticketCharged = false
        @Volatile private var _pendingJumpSeconds: Long? = null

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
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate()")
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand()")

        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Service stop requested")
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

        Log.d(TAG, "Starting flight: $originIata -> $destinationIata, Duration: $durationMinutes min, TimeScale: $timeScale")

        // 서비스 상태 초기화
        _isRunning = true
        _isPaused = false
        _totalSeconds = totalSeconds
        _secondsElapsed = 0L
        _ticketCharged = false
        _pendingJumpSeconds = null

        // 초기 알림과 함께 포그라운드 시작
        val initialContent = "$originIata ➔ $destinationIata | 비행 준비 중..."
        startForeground(NOTIFICATION_ID, createNotification(initialContent, originIata, destinationIata))
        Log.d(TAG, "Foreground service started with notification ID: $NOTIFICATION_ID")

        startTimer(totalSeconds, originIata, destinationIata, originName, destinationName, timeScale)
        return START_STICKY
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
            var secondsElapsed = 0L
            var lastNotificationTime = System.currentTimeMillis()

            while (secondsElapsed < totalSeconds) {
                while (_isPaused) {
                    delay(200)
                }
                val safeTimeScale = timeScale.coerceIn(0.001f, 1000f)
                val delayMs = (1000f / safeTimeScale).toLong()
                delay(delayMs.coerceAtLeast(100)) // 최소 100ms (너무 빠른 업데이트 방지)
                _pendingJumpSeconds?.let { jumpTarget ->
                    secondsElapsed = jumpTarget.coerceIn(0L, totalSeconds)
                    _pendingJumpSeconds = null
                }
                secondsElapsed++

                // 서비스 상태 업데이트 (UI 연동용)
                _secondsElapsed = secondsElapsed
                FlightStatusManager.updateProgress(secondsElapsed)

                // 알림창 갱신 (실제 시간 기준 30 초마다만 업데이트 - 성능 최적화)
                val currentTime = System.currentTimeMillis()
                val remaining = totalSeconds - secondsElapsed

                // 남은 시간이 0 이면 즉시 완료 처리
                if (remaining <= 0) {
                    Log.d(TAG, "Flight completed! Remaining: 0")
                    FlightStatusManager.stopFlight()
                    val completedText = "$originIata ➔ $destinationIata | 비행 완료!"
                    updateNotification(createNotification(completedText, originIata, destinationIata, true))

                    // 도착지를 현재 위치로 저장 (다음 비행을 위한 출발지)
                    saveDestinationAsCurrentLocation(destinationIata)

                    // 알림 제거 (완료 후 5 초 후 자동 삭제)
                    withContext(Dispatchers.IO) {
                        delay(5000)
                        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(NOTIFICATION_ID)
                    }

                    _isRunning = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                if (currentTime - lastNotificationTime >= 30000) { // 30 초
                    val remainingText = FlightUtils.formatTimer(remaining)
                    val contentText = "$originIata ➔ $destinationIata | 남은 시간: $remainingText"
                    updateNotification(createNotification(contentText, originIata, destinationIata))
                    Log.d(TAG, "Notification updated: $remainingText remaining")
                    lastNotificationTime = currentTime
                }
            }

            // 루프 완료 후 처리 (안전장치)
            Log.d(TAG, "Flight completed! (loop finished)")
            FlightStatusManager.stopFlight()
            val completedText = "$originIata ➔ $destinationIata | 비행 완료!"
            updateNotification(createNotification(completedText, originIata, destinationIata, true))

            // 도착지를 현재 위치로 저장 (다음 비행을 위한 출발지)
            saveDestinationAsCurrentLocation(destinationIata)

            // 알림 제거 (완료 후 5 초 후 자동 삭제)
            withContext(Dispatchers.IO) {
                delay(5000)
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID)
            }

            _isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * 도착지를 현재 위치로 저장 (DataStore)
     */
    private fun saveDestinationAsCurrentLocation(destinationIata: String) {
        try {
            // 공항 목록에서 도착지 찾기
            val inputStream = applicationContext.assets.open("airports.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            val allAirports = Json.decodeFromString<List<Airport>>(jsonString)

            val destinationAirport = allAirports.find { it.iata == destinationIata }

            if (destinationAirport != null) {
                // DataStore 에 저장 - AppRepository 와 동일한 키 사용
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
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FlightService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (isCompleted) "비행 완료 ✈️" else "현재 비행 중입니다 ✈️")
            .setContentText(content)
            .setSubText("$originIata ➔ $destinationIata")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "비행 중단", stopPendingIntent)
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
                "비행 상태 알림",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "비행 중 상태와 남은 시간을 표시합니다"
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
        instance = null
        timerJob?.cancel()
        serviceScope.cancel()
        FlightStatusManager.stopFlight()
        super.onDestroy()
    }
}

package com.example.openflight4and.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object FlightStatusManager {
    // 비행 상태
    private val _isFlying = MutableStateFlow(false)
    val isFlying: StateFlow<Boolean> = _isFlying.asStateFlow()

    // 진행률 (0.0 ~ 1.0)
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // 남은 시간 (초)
    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    // 총 비행 시간 (초)
    private val _totalDurationSeconds = MutableStateFlow(0L)
    val totalDurationSeconds: StateFlow<Long> = _totalDurationSeconds.asStateFlow()

    // 비행 정보 (출발지, 도착지 IATA)
    data class FlightInfo(
        val originIata: String = "",
        val destinationIata: String = "",
        val originName: String = "",
        val destinationName: String = ""
    )
    
    private val _flightInfo = MutableStateFlow(FlightInfo())
    val flightInfo: StateFlow<FlightInfo> = _flightInfo.asStateFlow()

    // 상태 업데이트 함수들
    fun startFlight(originIata: String, destinationIata: String, originName: String, destinationName: String, totalSeconds: Long) {
        _flightInfo.value = FlightInfo(originIata, destinationIata, originName, destinationName)
        _totalDurationSeconds.value = totalSeconds
        _remainingSeconds.value = totalSeconds
        _progress.value = 0f
        _isFlying.value = true
    }

    fun updateProgress(currentSecondsElapsed: Long) {
        val total = _totalDurationSeconds.value
        if (total > 0) {
            val remaining = (total - currentSecondsElapsed).coerceAtLeast(0)
            _remainingSeconds.value = remaining
            _progress.value = currentSecondsElapsed.toFloat() / total.toFloat()
        }
    }

    fun stopFlight() {
        _isFlying.value = false
        _progress.value = 1f
        _remainingSeconds.value = 0
    }
}

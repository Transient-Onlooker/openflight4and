package com.example.openflight4and

import android.app.Application

class OpenFlightApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // SDK 초기화(Firebase, Hilt 등)가 필요할 때 여기서 진행합니다.
    }
}

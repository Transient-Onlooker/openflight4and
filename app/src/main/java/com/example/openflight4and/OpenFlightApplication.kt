package com.example.openflight4and

import android.app.Application
import com.google.android.gms.ads.MobileAds

class OpenFlightApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
    }
}

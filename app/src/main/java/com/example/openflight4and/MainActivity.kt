package com.example.openflight4and

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.ui.LocalAppRepository
import com.example.openflight4and.ui.MainScreen
import com.example.openflight4and.ui.theme.OpenFlightTheme
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_INFLIGHT = "open_inflight"
        const val EXTRA_ORIGIN_IATA = "origin_iata"
        const val EXTRA_DESTINATION_IATA = "destination_iata"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
    }

    private var inflightLaunchRequest by mutableStateOf<InFlightLaunchRequest?>(null)
    private val repository by lazy(LazyThreadSafetyMode.NONE) { AppRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inflightLaunchRequest = intent.toInFlightLaunchRequest()
        observeAppLanguagePreference()
        observeScreenOrientationPreference()

        // 알림 권한 요청 (Android 13+)
        requestNotificationPermission()

        // Edge-to-edge (상태바와 내비게이션바 투명 처리)
        enableEdgeToEdge()

        setContent {
            CompositionLocalProvider(LocalAppRepository provides repository) {
                OpenFlightTheme {
                    MainScreen(
                        inflightLaunchRequest = inflightLaunchRequest,
                        onInflightLaunchHandled = { inflightLaunchRequest = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inflightLaunchRequest = intent.toInFlightLaunchRequest()
    }

    private fun observeScreenOrientationPreference() {
        lifecycleScope.launch {
            repository.screenOrientationMode.collect { mode ->
                requestedOrientation = when (mode) {
                    "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                }
            }
        }
    }

    private fun observeAppLanguagePreference() {
        lifecycleScope.launch {
            repository.appLanguage.collect { language ->
                val locales = if (language == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(language)
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            
            if (!granted) {
                // 권한 요청 (필수는 아님 - 거부해도 앱 사용 가능)
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 권한 허용됨 - 알림 표시 가능
        } else {
            // 권한 거부됨 - 설정에서 허용하도록 안내 필요
            // 하지만 비행 기능은 계속 작동함 (포그라운드 서비스)
        }
    }

    private fun android.content.Intent?.toInFlightLaunchRequest(): InFlightLaunchRequest? {
        if (this?.getBooleanExtra(EXTRA_OPEN_INFLIGHT, false) != true) {
            return null
        }

        return InFlightLaunchRequest(
            originIata = getStringExtra(EXTRA_ORIGIN_IATA),
            destinationIata = getStringExtra(EXTRA_DESTINATION_IATA),
            durationMinutes = getIntExtra(EXTRA_DURATION_MINUTES, 0)
        )
    }
}

data class InFlightLaunchRequest(
    val originIata: String?,
    val destinationIata: String?,
    val durationMinutes: Int
)

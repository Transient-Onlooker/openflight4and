package com.example.openflight4and

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.openflight4and.ui.MainScreen
import com.example.openflight4and.ui.theme.OpenFlightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 알림 권한 요청 (Android 13+)
        requestNotificationPermission()

        // Edge-to-edge (상태바와 내비게이션바 투명 처리)
        enableEdgeToEdge()

        setContent {
            OpenFlightTheme {
                MainScreen()
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
}

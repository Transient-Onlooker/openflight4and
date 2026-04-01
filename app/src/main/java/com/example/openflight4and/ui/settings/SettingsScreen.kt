package com.example.openflight4and.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.openflight4and.BuildConfig
import com.example.openflight4and.data.AppRepository
import com.example.openflight4and.focus.FocusLockUtils
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.components.SectionHeader
import com.example.openflight4and.ui.theme.FlightGray
import kotlinx.coroutines.launch

private const val TITLE_SETTINGS = "\uC124\uC815"
private const val TITLE_UNIT_SYSTEM = "\uCE21\uC815 \uB2E8\uC704"
private const val TITLE_SCREEN_ORIENTATION = "\uD654\uBA74 \uBC29\uD5A5"
private const val TITLE_MAP_STYLE = "\uC9C0\uB3C4 \uC2A4\uD0C0\uC77C"
private const val TITLE_AIRPLANE_MODE_CHECK = "\uBE44\uD589\uAE30 \uBAA8\uB4DC \uD655\uC778"
private const val TITLE_NOTIFICATIONS = "\uC54C\uB9BC"
private const val TITLE_NOTIFICATION_INTERVAL = "\uC54C\uB9BC \uAC31\uC2E0 \uC8FC\uAE30"
private const val TITLE_FOCUS_LOCK = "\uC9D1\uC911 \uC7A0\uAE08"
private const val TITLE_BATTERY_OPT_OUT = "\uBC30\uD130\uB9AC \uCD5C\uC801\uD654 \uC608\uC678"
private const val LABEL_AUTO = "\uC790\uB3D9"
private const val LABEL_PORTRAIT = "\uC138\uB85C"
private const val LABEL_LANDSCAPE = "\uAC00\uB85C"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    restrictInFlightSettings: Boolean = false
) {
    val context = LocalContext.current
    val repository = remember { AppRepository(context) }
    val scope = rememberCoroutineScope()

    val unitSystem by repository.unitSystem.collectAsState(initial = "km")
    val mapStyle by repository.mapStyle.collectAsState(initial = "standard")
    val airplaneModeCheck by repository.airplaneModeCheck.collectAsState(initial = true)
    val notificationsEnabled by repository.notificationsEnabled.collectAsState(initial = true)
    val notificationUpdateSeconds by repository.notificationUpdateSeconds.collectAsState(initial = 10)
    val focusLockEnabled by repository.focusLockEnabled.collectAsState(initial = false)
    val screenOrientationMode by repository.screenOrientationMode.collectAsState(initial = "auto")
    val isScreenOrientationLocked = restrictInFlightSettings
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasUsageAccess by remember { mutableStateOf(FocusLockUtils.hasUsageAccess(context)) }
    var canDrawOverlays by remember { mutableStateOf(FocusLockUtils.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = FocusLockUtils.hasUsageAccess(context)
                canDrawOverlays = FocusLockUtils.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    FlightMapBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(TITLE_SETTINGS, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (restrictInFlightSettings) {
                    Text(
                        text = "\uBE44\uD589 \uC911\uC5D0\uB294 \uD654\uBA74 \uBC29\uD5A5 \uC124\uC815\uB9CC \uC7A0\uAE08\uB2C8\uB2E4.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FlightGray,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                    Spacer(modifier = Modifier.padding(top = 16.dp))
                }

                SectionHeader(TITLE_UNIT_SYSTEM)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = unitSystem == "km",
                        onClick = { scope.launch { repository.setUnitSystem("km") } },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Kilometers (km)") }
                    SegmentedButton(
                        selected = unitSystem == "mi",
                        onClick = { scope.launch { repository.setUnitSystem("mi") } },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("Miles (mi)") }
                }

                Spacer(modifier = Modifier.padding(top = 24.dp))

                SectionHeader(TITLE_SCREEN_ORIENTATION)
                val orientationModes = listOf(
                    "auto" to LABEL_AUTO,
                    "portrait" to LABEL_PORTRAIT,
                    "landscape" to LABEL_LANDSCAPE
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    orientationModes.forEachIndexed { index, (id, label) ->
                        SegmentedButton(
                            selected = screenOrientationMode == id,
                            enabled = !isScreenOrientationLocked,
                            onClick = { scope.launch { repository.setScreenOrientationMode(id) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = orientationModes.size)
                        ) { Text(label) }
                    }
                }
                Text(
                    text = "\uC790\uB3D9\uC740 \uAE30\uAE30 \uD68C\uC804\uC5D0 \uB9DE\uCDB0 \uBC14\uB00C\uACE0, \uC138\uB85C/\uAC00\uB85C\uB294 \uD574\uB2F9 \uBC29\uD5A5\uC73C\uB85C \uACE0\uC815\uB429\uB2C8\uB2E4.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightGray,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )

                Spacer(modifier = Modifier.padding(top = 24.dp))

                SectionHeader(TITLE_MAP_STYLE)
                val styles = listOf(
                    "standard" to "\uC77C\uBC18",
                    "satellite" to "\uC704\uC131",
                    "hybrid" to "\uD558\uC774\uBE0C\uB9AC\uB4DC"
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    styles.forEachIndexed { index, (id, label) ->
                        SegmentedButton(
                            selected = mapStyle == id,
                            onClick = { scope.launch { repository.setMapStyle(id) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = styles.size)
                        ) { Text(label) }
                    }
                }

                Spacer(modifier = Modifier.padding(top = 24.dp))

                ToggleSettingItem(
                    title = TITLE_AIRPLANE_MODE_CHECK,
                    description = "\uC138\uC158 \uC2DC\uC791 \uC804 \uBE44\uD589\uAE30 \uBAA8\uB4DC \uD65C\uC131\uD654 \uC5EC\uBD80\uB97C \uD655\uC778\uD569\uB2C8\uB2E4.",
                    checked = airplaneModeCheck,
                    onCheckedChange = { scope.launch { repository.setAirplaneModeCheck(it) } }
                )

                ToggleSettingItem(
                    title = TITLE_NOTIFICATIONS,
                    description = "\uC138\uC158 \uC644\uB8CC \uBC0F \uC911\uC694 \uC54C\uB9BC\uC744 \uBC1B\uC2B5\uB2C8\uB2E4.",
                    checked = notificationsEnabled,
                    onCheckedChange = { scope.launch { repository.setNotificationsEnabled(it) } }
                )

                if (notificationsEnabled) {
                    SectionHeader(TITLE_NOTIFICATION_INTERVAL)
                    val notificationIntervalRows = listOf(
                        listOf(1, 2, 5),
                        listOf(10, 20, 30)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        notificationIntervalRows.forEach { row ->
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                row.forEachIndexed { index, seconds ->
                                    SegmentedButton(
                                        modifier = Modifier.weight(1f),
                                        selected = notificationUpdateSeconds == seconds,
                                        onClick = { scope.launch { repository.setNotificationUpdateSeconds(seconds) } },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = row.size)
                                    ) { Text("${seconds}s") }
                                }
                            }
                        }
                    }
                    Text(
                        text = "\uAE30\uBCF8\uAC12 10\uCD08. \uBE44\uD589 \uC911 \uC54C\uB9BC \uAC31\uC2E0 \uC8FC\uAE30\uB97C \uC124\uC815\uD569\uB2C8\uB2E4.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FlightGray,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                    )
                }

                BatteryOptimizationItem()

                Spacer(modifier = Modifier.padding(top = 24.dp))

                SectionHeader(TITLE_FOCUS_LOCK)
                ToggleSettingItem(
                    title = TITLE_FOCUS_LOCK,
                    description = "\uBE44\uD589 \uC911 \uB2E4\uB978 \uC571\uC73C\uB85C \uB098\uAC00\uBA74 \uC624\uBC84\uB808\uC774\uB85C \uBCF5\uADC0\uB97C \uC720\uB3C4\uD569\uB2C8\uB2E4.",
                    checked = focusLockEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { repository.setFocusLockEnabled(enabled) }
                    }
                )
                Text(
                    text = "\uC791\uB3D9 \uC870\uAC74: \uC0AC\uC6A9 \uC815\uBCF4 \uC811\uADFC + \uB2E4\uB978 \uC571 \uC704\uC5D0 \uD45C\uC2DC \uAD8C\uD55C\uC774 \uBAA8\uB450 \uD544\uC694\uD569\uB2C8\uB2E4.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightGray,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
                if (focusLockEnabled) {
                    PermissionSettingItem(
                        title = "\uC0AC\uC6A9 \uC815\uBCF4 \uC811\uADFC",
                        description = if (hasUsageAccess) {
                            "\uD5C8\uC6A9\uB428"
                        } else {
                            "\uD544\uC694 \u2014 \uD604\uC7AC \uC804\uBA74 \uC571\uC744 \uD655\uC778\uD558\uAE30 \uC704\uD574 \uD5C8\uC6A9\uD574\uC57C \uD569\uB2C8\uB2E4."
                        },
                        onClick = { FocusLockUtils.openUsageAccessSettings(context) }
                    )
                    PermissionSettingItem(
                        title = "\uB2E4\uB978 \uC571 \uC704\uC5D0 \uD45C\uC2DC",
                        description = if (canDrawOverlays) {
                            "\uD5C8\uC6A9\uB428"
                        } else {
                            "\uD544\uC694 \u2014 \uB2E4\uB978 \uC571 \uC774\uD0C8 \uC2DC \uBCF5\uADC0 \uC624\uBC84\uB808\uC774\uB97C \uB744\uC6B0\uAE30 \uC704\uD574 \uD5C8\uC6A9\uD574\uC57C \uD569\uB2C8\uB2E4."
                        },
                        onClick = { FocusLockUtils.openOverlaySettings(context) }
                    )
                }

                Spacer(modifier = Modifier.padding(top = 48.dp))

                Text(
                    text = "\uBC84\uC804 ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = FlightGray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.padding(top = 32.dp))
            }
        }
    }
}

@Composable
fun ToggleSettingItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = FlightGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = FlightGray,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}

@Composable
fun PermissionSettingItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = FlightGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.White
        )
    }
}

@Composable
fun BatteryOptimizationItem() {
    val context = LocalContext.current
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable {
                val intent = if (isIgnoring) {
                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                } else {
                    android.content.Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    )
                }
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = TITLE_BATTERY_OPT_OUT,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isIgnoring) {
                    Text(
                        text = "ON",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "\uBC31\uADF8\uB77C\uC6B4\uB4DC\uC5D0\uC11C\uB3C4 \uBE44\uD589\uC774 \uC911\uB2E8\uB418\uC9C0 \uC54A\uB3C4\uB85D \uC124\uC815\uD569\uB2C8\uB2E4.",
                color = FlightGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = FlightGray
        )
    }
}

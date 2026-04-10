package com.example.openflight4and.ui.settings

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.openflight4and.BuildConfig
import com.example.openflight4and.R
import com.example.openflight4and.focus.LaunchableApp
import com.example.openflight4and.focus.FocusLockUtils
import com.example.openflight4and.ui.LocalAppRepository
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.components.SectionHeader
import com.example.openflight4and.ui.theme.FlightGray
import kotlinx.coroutines.launch

private val SettingsHorizontalPadding = 24.dp
private val SettingsSectionSpacing = 24.dp
private val SettingsNoticeTopPadding = 8.dp
private val SettingsNoticeStartPadding = 4.dp
private val SettingsNoticeBottomSpacing = 16.dp
private val SettingsItemVerticalPadding = 12.dp
private val SettingsChevronSpacing = 12.dp
private val SettingsBatteryBadgeSpacing = 8.dp
private val SettingsBatteryBadgeHorizontalPadding = 6.dp
private val SettingsBatteryBadgeVerticalPadding = 2.dp
private val SettingsFooterTopSpacing = 48.dp
private val SettingsFooterBottomSpacing = 32.dp
private val SettingsNotificationRowSpacing = 8.dp
private val SettingsNotificationRows = listOf(
    listOf(1, 2, 5),
    listOf(10, 20, 30)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    restrictInFlightSettings: Boolean = false
) {
    val context = LocalContext.current
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()

    val unitSystem by repository.unitSystem.collectAsState(initial = "km")
    val appLanguage by repository.appLanguage.collectAsState(initial = "system")
    val mapStyle by repository.mapStyle.collectAsState(initial = "standard")
    val notificationsEnabled by repository.notificationsEnabled.collectAsState(initial = true)
    val notificationUpdateSeconds by repository.notificationUpdateSeconds.collectAsState(initial = 10)
    val focusLockEnabled by repository.focusLockEnabled.collectAsState(initial = false)
    val focusLockAllowedPackages by repository.focusLockAllowedApps.collectAsState(initial = emptySet())
    val screenOrientationMode by repository.screenOrientationMode.collectAsState(initial = "auto")
    val isScreenOrientationLocked = restrictInFlightSettings
    val lifecycleOwner = LocalLifecycleOwner.current
    val launchableApps = remember(context) { FocusLockUtils.getLaunchableApps(context) }
    var hasUsageAccess by remember { mutableStateOf(FocusLockUtils.hasUsageAccess(context)) }
    var canDrawOverlays by remember { mutableStateOf(FocusLockUtils.canDrawOverlays(context)) }
    var showAllowedAppsDialog by remember { mutableStateOf(false) }
    var allowedAppsSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allowedAppsQuery by remember { mutableStateOf("") }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val titleSettings = stringResource(R.string.settings_title)
    val titleUnitSystem = stringResource(R.string.settings_title_unit_system)
    val titleLanguage = stringResource(R.string.settings_title_language)
    val titleScreenOrientation = stringResource(R.string.settings_title_screen_orientation)
    val titleMapStyle = stringResource(R.string.settings_title_map_style)
    val titleNotifications = stringResource(R.string.settings_title_notifications)
    val titleNotificationInterval = stringResource(R.string.settings_title_notification_interval)
    val titleFocusLock = stringResource(R.string.settings_title_focus_lock)
    val titleAllowedApps = stringResource(R.string.settings_focus_lock_allowed_apps)
    val labelAuto = stringResource(R.string.settings_orientation_auto)
    val labelLanguageSystem = stringResource(R.string.settings_language_system)
    val labelLanguageKorean = stringResource(R.string.settings_language_korean)
    val labelLanguageEnglish = stringResource(R.string.settings_language_english)
    val labelPortrait = stringResource(R.string.settings_orientation_portrait)
    val labelLandscape = stringResource(R.string.settings_orientation_landscape)

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
                    title = { Text(titleSettings, color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
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
                    .padding(horizontal = SettingsHorizontalPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                if (restrictInFlightSettings) {
                    Text(
                        text = stringResource(R.string.settings_inflight_lock_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = FlightGray,
                        modifier = Modifier.padding(top = SettingsNoticeTopPadding, start = SettingsNoticeStartPadding)
                    )
                    Spacer(modifier = Modifier.padding(top = SettingsNoticeBottomSpacing))
                }

                SectionHeader(titleUnitSystem)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = unitSystem == "km",
                        onClick = { scope.launch { repository.setUnitSystem("km") } },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(stringResource(R.string.settings_unit_kilometers)) }
                    SegmentedButton(
                        selected = unitSystem == "mi",
                        onClick = { scope.launch { repository.setUnitSystem("mi") } },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(stringResource(R.string.settings_unit_miles)) }
                }

                Spacer(modifier = Modifier.padding(top = SettingsSectionSpacing))

                SectionHeader(titleLanguage)
                val languageModes = listOf(
                    "system" to labelLanguageSystem,
                    "ko" to labelLanguageKorean,
                    "en" to labelLanguageEnglish
                )
                val selectedLanguageLabel = languageModes.firstOrNull { it.first == appLanguage }?.second
                    ?: labelLanguageSystem
                PermissionSettingItem(
                    title = titleLanguage,
                    description = selectedLanguageLabel,
                    onClick = { showLanguageDialog = true }
                )

                Spacer(modifier = Modifier.padding(top = SettingsSectionSpacing))

                SectionHeader(titleScreenOrientation)
                val orientationModes = listOf(
                    "auto" to labelAuto,
                    "portrait" to labelPortrait,
                    "landscape" to labelLandscape
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
                    text = stringResource(R.string.settings_screen_orientation_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightGray,
                    modifier = Modifier.padding(top = SettingsNoticeTopPadding, start = SettingsNoticeStartPadding)
                )

                Spacer(modifier = Modifier.padding(top = SettingsSectionSpacing))

                SectionHeader(titleMapStyle)
                val styles = listOf(
                    "standard" to stringResource(R.string.settings_map_style_standard),
                    "satellite" to stringResource(R.string.settings_map_style_satellite),
                    "hybrid" to stringResource(R.string.settings_map_style_hybrid)
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

                Spacer(modifier = Modifier.padding(top = SettingsSectionSpacing))

                ToggleSettingItem(
                    title = titleNotifications,
                    description = stringResource(R.string.settings_notifications_description),
                    checked = notificationsEnabled,
                    onCheckedChange = { scope.launch { repository.setNotificationsEnabled(it) } }
                )

                if (notificationsEnabled) {
                    SectionHeader(titleNotificationInterval)
                    Column(verticalArrangement = Arrangement.spacedBy(SettingsNotificationRowSpacing)) {
                        SettingsNotificationRows.forEach { row ->
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
                        text = stringResource(R.string.settings_notification_interval_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = FlightGray,
                        modifier = Modifier.padding(top = SettingsNoticeTopPadding, start = SettingsNoticeStartPadding)
                    )
                }

                BatteryOptimizationItem()

                Spacer(modifier = Modifier.padding(top = SettingsSectionSpacing))

                SectionHeader(titleFocusLock)
                ToggleSettingItem(
                    title = titleFocusLock,
                    description = stringResource(R.string.settings_focus_lock_description),
                    checked = focusLockEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch { repository.setFocusLockEnabled(enabled) }
                    }
                )
                Text(
                    text = stringResource(R.string.settings_focus_lock_requirements),
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightGray,
                    modifier = Modifier.padding(top = SettingsNoticeTopPadding, start = SettingsNoticeStartPadding)
                )
                if (focusLockEnabled) {
                    PermissionSettingItem(
                        title = stringResource(R.string.settings_usage_access),
                        description = if (hasUsageAccess) {
                            stringResource(R.string.settings_permission_allowed)
                        } else {
                            stringResource(R.string.settings_permission_required_usage)
                        },
                        onClick = { FocusLockUtils.openUsageAccessSettings(context) }
                    )
                    PermissionSettingItem(
                        title = stringResource(R.string.settings_overlay_permission),
                        description = if (canDrawOverlays) {
                            stringResource(R.string.settings_permission_allowed)
                        } else {
                            stringResource(R.string.settings_permission_required_overlay)
                        },
                        onClick = { FocusLockUtils.openOverlaySettings(context) }
                    )
                    PermissionSettingItem(
                        title = titleAllowedApps,
                        description = if (focusLockAllowedPackages.isEmpty()) {
                            stringResource(R.string.settings_focus_lock_allowed_apps_empty)
                        } else {
                            stringResource(
                                R.string.settings_focus_lock_allowed_apps_count,
                                focusLockAllowedPackages.size
                            )
                        },
                        onClick = {
                            allowedAppsSelection = focusLockAllowedPackages
                            allowedAppsQuery = ""
                            showAllowedAppsDialog = true
                        }
                    )
                }

                Spacer(modifier = Modifier.padding(top = SettingsFooterTopSpacing))

                Text(
                    text = stringResource(R.string.label_version_format, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = FlightGray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.padding(top = SettingsFooterBottomSpacing))
            }
        }
    }

    if (showAllowedAppsDialog) {
        val filteredLaunchableApps = remember(launchableApps, allowedAppsQuery) {
            val normalizedQuery = allowedAppsQuery.trim()
            if (normalizedQuery.isEmpty()) {
                launchableApps
            } else {
                launchableApps.filter { app ->
                    app.label.contains(normalizedQuery, ignoreCase = true)
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showAllowedAppsDialog = false },
            title = { Text(stringResource(R.string.settings_focus_lock_allowed_apps_dialog_title)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.settings_focus_lock_allowed_apps_dialog_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = FlightGray
                    )
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    OutlinedTextField(
                        value = allowedAppsQuery,
                        onValueChange = { allowedAppsQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(stringResource(R.string.settings_focus_lock_allowed_apps_dialog_search_placeholder))
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = FlightGray,
                            unfocusedPlaceholderColor = FlightGray,
                            focusedLeadingIconColor = FlightGray,
                            unfocusedLeadingIconColor = FlightGray,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.padding(top = 12.dp))
                    if (launchableApps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_focus_lock_allowed_apps_dialog_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = FlightGray
                        )
                    } else if (filteredLaunchableApps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_focus_lock_allowed_apps_dialog_search_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = FlightGray
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 88.dp),
                            modifier = Modifier.heightIn(max = 360.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredLaunchableApps, key = { it.packageName }) { app ->
                                val checked = allowedAppsSelection.contains(app.packageName)
                                AllowedAppItem(
                                    app = app,
                                    checked = checked,
                                    onToggle = { isChecked ->
                                        allowedAppsSelection =
                                            if (isChecked) {
                                                allowedAppsSelection + app.packageName
                                            } else {
                                                allowedAppsSelection - app.packageName
                                            }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.setFocusLockAllowedApps(allowedAppsSelection)
                        }
                        showAllowedAppsDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_focus_lock_allowed_apps_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAllowedAppsDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showLanguageDialog) {
        val languageModes = listOf(
            "system" to labelLanguageSystem,
            "ko" to labelLanguageKorean,
            "en" to labelLanguageEnglish
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(titleLanguage) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    languageModes.forEach { (id, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { repository.setAppLanguage(id) }
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLanguage == id,
                                onClick = {
                                    scope.launch { repository.setAppLanguage(id) }
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (appLanguage == id) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AllowedAppItem(
    app: LaunchableApp,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onToggle(!checked) },
                onLongClick = {
                    Toast.makeText(context, app.packageName, Toast.LENGTH_SHORT).show()
                }
            ),
        shape = RoundedCornerShape(22.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        } else {
            Color.White.copy(alpha = 0.05f)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.size(64.dp)
                ) {
                    AndroidView(
                        factory = { context ->
                            android.widget.ImageView(context).apply {
                                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                                setPadding(10, 10, 10, 10)
                            }
                        },
                        update = { imageView ->
                            imageView.setImageDrawable(app.icon)
                        }
                    )
                }

                if (checked) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.size(8.dp))

            Text(
                text = app.label,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
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
            .padding(vertical = SettingsItemVerticalPadding),
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
            .padding(vertical = SettingsItemVerticalPadding)
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
        Spacer(modifier = Modifier.width(SettingsChevronSpacing))
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    var isIgnoring by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    DisposableEffect(lifecycleOwner, context, powerManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SettingsItemVerticalPadding)
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
                    text = stringResource(R.string.settings_title_battery_optimization),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(SettingsBatteryBadgeSpacing))
                Text(
                    text = if (isIgnoring) {
                        stringResource(R.string.settings_permission_allowed)
                    } else {
                        stringResource(R.string.settings_battery_optimization_off)
                    },
                    color = if (isIgnoring) MaterialTheme.colorScheme.primary else FlightGray,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(
                            color = if (isIgnoring) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            } else {
                                Color.White.copy(alpha = 0.08f)
                            },
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(
                            horizontal = SettingsBatteryBadgeHorizontalPadding,
                            vertical = SettingsBatteryBadgeVerticalPadding
                        )
                )
            }
            Text(
                text = if (isIgnoring) {
                    stringResource(R.string.settings_battery_optimization_allowed_description)
                } else {
                    stringResource(R.string.settings_battery_optimization_off_description)
                },
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

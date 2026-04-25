package com.example.openflight4and.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.openflight4and.BuildConfig
import com.example.openflight4and.MainActivity
import com.example.openflight4and.R
import com.example.openflight4and.focus.LaunchableApp
import com.example.openflight4and.focus.FocusLockUtils
import com.example.openflight4and.model.FlightBackgroundSound
import com.example.openflight4and.model.FlightTimeDisplayMode
import com.example.openflight4and.ui.LocalAppRepository
import com.example.openflight4and.ui.components.FlightMapBackground
import com.example.openflight4and.ui.theme.FlightGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.Collator
import java.util.Locale

private val SettingsHorizontalPadding = 24.dp
private val SettingsSectionSpacing = 24.dp
private val SettingsCardInnerSpacing = 16.dp
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
private const val Mp3PickerFocusLockBypassMillis = 2 * 60 * 1000L
private const val FocusLockPinMinLength = 4
private const val FocusLockPinMaxLength = 6
private const val FocusLockPinSessionDurationMillis = 3 * 60 * 1000L

private fun resolveDocumentDisplayName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "custom.mp3"
}

private enum class FocusLockPinEntryStep {
    CURRENT,
    NEW,
    CONFIRM
}

private enum class SettingsDetailSection {
    GENERAL,
    NOTIFICATIONS,
    BACKGROUND,
    FOCUS_LOCK,
    ADVANCED_LOCK,
    RESET
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    restrictInFlightSettings: Boolean = false
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val repository = LocalAppRepository.current
    val scope = rememberCoroutineScope()

    val unitSystem by repository.unitSystem.collectAsState(initial = "km")
    val appLanguage by repository.appLanguage.collectAsState(initial = "system")
    val mapStyle by repository.mapStyle.collectAsState(initial = "standard")
    val notificationsEnabled by repository.notificationsEnabled.collectAsState(initial = true)
    val notificationUpdateSeconds by repository.notificationUpdateSeconds.collectAsState(initial = 10)
    val flightBackgroundSound by repository.flightBackgroundSound.collectAsState(initial = FlightBackgroundSound.AIRPLANE_WHITE_NOISE)
    val flightBackgroundSoundCustomUri by repository.flightBackgroundSoundCustomUri.collectAsState(initial = null)
    val flightBackgroundSoundCustomName by repository.flightBackgroundSoundCustomName.collectAsState(initial = null)
    val flightTimeDisplayMode by repository.flightTimeDisplayMode.collectAsState(initial = FlightTimeDisplayMode.REMAINING)
    val focusLockEnabled by repository.focusLockEnabled.collectAsState(initial = false)
    val advancedLockEnabled by repository.advancedLockEnabled.collectAsState(initial = false)
    val focusLockPinEnabled by repository.focusLockPinEnabled.collectAsState(initial = false)
    val focusLockAllowedPackages by repository.focusLockAllowedApps.collectAsState(initial = emptySet())
    val screenOrientationMode by repository.screenOrientationMode.collectAsState(initial = "auto")
    val isScreenOrientationLocked = restrictInFlightSettings
    val lifecycleOwner = LocalLifecycleOwner.current
    val selfPackageName = context.packageName
    val appDisplayName = stringResource(R.string.app_name)
    var launchableApps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var isLoadingLaunchableApps by remember { mutableStateOf(false) }
    var hasUsageAccess by remember { mutableStateOf(FocusLockUtils.hasUsageAccess(context)) }
    var canDrawOverlays by remember { mutableStateOf(FocusLockUtils.canDrawOverlays(context)) }
    var showAllowedAppsDialog by remember { mutableStateOf(false) }
    var allowedAppsSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allowedAppsQuery by remember { mutableStateOf("") }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFocusLockUnlockDialog by remember { mutableStateOf(false) }
    var showFocusLockSetPinDialog by remember { mutableStateOf(false) }
    var showFocusLockChangePinDialog by remember { mutableStateOf(false) }
    var showFocusLockRemovePinDialog by remember { mutableStateOf(false) }
    var showFocusLockForgotPinDialog by remember { mutableStateOf(false) }
    var showAdvancedLockEnableConfirmDialog by remember { mutableStateOf(false) }
    var protectedAllowedAppPendingRemoval by remember { mutableStateOf<LaunchableApp?>(null) }
    var protectedAllowedAppRemovalConfirmCount by remember { mutableStateOf(0) }
    var showRestartAppDialog by remember { mutableStateOf(false) }
    var showResetAppDialog by remember { mutableStateOf(false) }
    var showFullResetDialog by remember { mutableStateOf(false) }
    var fullResetConfirmationInput by remember { mutableStateOf("") }
    var activeSettingsSection by remember { mutableStateOf<SettingsDetailSection?>(null) }
    var focusLockAuthenticatedAtMillis by remember { mutableStateOf<Long?>(null) }
    var pendingProtectedFocusLockAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingEnableAdvancedLockAfterPinSetup by remember { mutableStateOf(false) }
    val titleSettings = stringResource(R.string.settings_title)
    val titleUnitSystem = stringResource(R.string.settings_title_unit_system)
    val titleLanguage = stringResource(R.string.settings_title_language)
    val titleScreenOrientation = stringResource(R.string.settings_title_screen_orientation)
    val titleMapStyle = stringResource(R.string.settings_title_map_style)
    val titleNotifications = stringResource(R.string.settings_title_notifications)
    val titleNotificationInterval = stringResource(R.string.settings_title_notification_interval)
    val titleFocusLock = stringResource(R.string.settings_title_focus_lock)
    val titleAdvancedLock = stringResource(R.string.settings_title_advanced_lock)
    val titleAllowedApps = stringResource(R.string.settings_focus_lock_allowed_apps)
    val todayResetConfirmationDate = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    }
    val todayResetConfirmationText = stringResource(
        R.string.settings_full_reset_app_confirmation_text,
        todayResetConfirmationDate
    )
    val protectedAllowedPackages = remember(context) {
        FocusLockUtils.getDefaultAllowedPackages(context)
    }
    val visibleFocusLockAllowedPackages = remember(focusLockAllowedPackages, selfPackageName) {
        focusLockAllowedPackages.filterNot { packageName ->
            FocusLockUtils.shouldHideAllowedAppInUi(
                packageName = packageName,
                selectedPackages = focusLockAllowedPackages,
                selfPackageName = selfPackageName,
                applicationId = BuildConfig.APPLICATION_ID
            )
        }.toSet()
    }
    val titleGeneralSection = stringResource(R.string.settings_section_general)
    val titleNotificationSection = stringResource(R.string.settings_section_notifications)
    val titleBackgroundSection = stringResource(R.string.settings_section_background)
    val titleFocusLockSection = stringResource(R.string.settings_section_focus_lock)
    val titleAdvancedLockSection = stringResource(R.string.settings_section_advanced_lock)
    val descriptionGeneralSection = stringResource(R.string.settings_section_general_description)
    val descriptionNotificationSection = stringResource(R.string.settings_section_notifications_description)
    val descriptionBackgroundSection = stringResource(R.string.settings_section_background_description)
    val descriptionFocusLockSection = stringResource(R.string.settings_section_focus_lock_description)
    val descriptionAdvancedLockSection = stringResource(R.string.settings_section_advanced_lock_description)
    val labelAuto = stringResource(R.string.settings_orientation_auto)
    val labelLanguageSystem = stringResource(R.string.settings_language_system)
    val labelLanguageKorean = stringResource(R.string.settings_language_korean)
    val labelLanguageEnglish = stringResource(R.string.settings_language_english)
    val labelPortrait = stringResource(R.string.settings_orientation_portrait)
    val labelLandscape = stringResource(R.string.settings_orientation_landscape)
    val titleResetSection = stringResource(R.string.settings_section_reset)
    val descriptionResetSection = stringResource(R.string.settings_section_reset_description)
    val currentSettingsTitle = when (activeSettingsSection) {
        SettingsDetailSection.GENERAL -> titleGeneralSection
        SettingsDetailSection.NOTIFICATIONS -> titleNotificationSection
        SettingsDetailSection.BACKGROUND -> titleBackgroundSection
        SettingsDetailSection.FOCUS_LOCK -> titleFocusLockSection
        SettingsDetailSection.ADVANCED_LOCK -> titleAdvancedLockSection
        SettingsDetailSection.RESET -> titleResetSection
        null -> titleSettings
    }
    val customMp3MissingMessage = stringResource(R.string.settings_flight_background_sound_custom_missing)
    val customMp3PermissionErrorMessage = stringResource(R.string.settings_flight_background_sound_permission_error)
    val mp3PickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@rememberLauncherForActivityResult
        }
        val permissionResult = runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        if (permissionResult.isFailure) {
            Toast.makeText(context, customMp3PermissionErrorMessage, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val displayName = resolveDocumentDisplayName(context, uri)
        scope.launch {
            repository.setFlightBackgroundSoundCustomFile(uri.toString(), displayName)
            repository.setFlightBackgroundSound(FlightBackgroundSound.CUSTOM_MP3)
            repository.setFlightBackgroundSoundEnabled(true)
        }
    }

    fun launchMp3PickerWithFocusLockBypass() {
        scope.launch {
            repository.allowTemporaryFocusLockPackages(
                packages = FocusLockUtils.getDocumentPickerPackages(context),
                activeUntilMillis = System.currentTimeMillis() + Mp3PickerFocusLockBypassMillis
            )
            mp3PickerLauncher.launch(arrayOf("audio/mpeg", "audio/mp3"))
        }
    }

    BackHandler(enabled = activeSettingsSection != null) {
        activeSettingsSection = null
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = FocusLockUtils.hasUsageAccess(context)
                canDrawOverlays = FocusLockUtils.canDrawOverlays(context)
            } else if (event == Lifecycle.Event.ON_STOP) {
                focusLockAuthenticatedAtMillis = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(showAllowedAppsDialog) {
        if (showAllowedAppsDialog && launchableApps.isEmpty() && !isLoadingLaunchableApps) {
            isLoadingLaunchableApps = true
            launchableApps = withContext(Dispatchers.Default) {
                FocusLockUtils.getLaunchableApps(context)
            }
            isLoadingLaunchableApps = false
        }
    }

    fun hasValidFocusLockSession(): Boolean {
        val authenticatedAt = focusLockAuthenticatedAtMillis ?: return false
        return SystemClock.elapsedRealtime() - authenticatedAt <= FocusLockPinSessionDurationMillis
    }

    fun restartApp() {
        val restartIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(restartIntent)
        (context as? Activity)?.finishAffinity()
    }

    fun runProtectedFocusLockAction(
        forceFreshPin: Boolean = false,
        action: () -> Unit
    ) {
        if (!advancedLockEnabled || !focusLockPinEnabled || (!forceFreshPin && hasValidFocusLockSession())) {
            action()
        } else {
            pendingProtectedFocusLockAction = action
            showFocusLockUnlockDialog = true
        }
    }

    FlightMapBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(currentSettingsTitle, color = Color.White) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (activeSettingsSection == null) {
                                    onNavigateBack()
                                } else {
                                    activeSettingsSection = null
                                }
                            }
                        ) {
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

                val languageModes = listOf(
                    "system" to labelLanguageSystem,
                    "ko" to labelLanguageKorean,
                    "en" to labelLanguageEnglish
                )
                val selectedLanguageLabel = languageModes.firstOrNull { it.first == appLanguage }?.second
                    ?: labelLanguageSystem
                val orientationModes = listOf(
                    "auto" to labelAuto,
                    "portrait" to labelPortrait,
                    "landscape" to labelLandscape
                )
                val styles = listOf(
                    "standard" to stringResource(R.string.settings_map_style_standard),
                    "satellite" to stringResource(R.string.settings_map_style_satellite),
                    "hybrid" to stringResource(R.string.settings_map_style_hybrid)
                )

                if (activeSettingsSection == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(SettingsCardInnerSpacing)) {
                        SettingsMenuCard(
                            title = titleGeneralSection,
                            description = descriptionGeneralSection,
                            onClick = { activeSettingsSection = SettingsDetailSection.GENERAL }
                        )
                        SettingsMenuCard(
                            title = titleNotificationSection,
                            description = descriptionNotificationSection,
                            onClick = { activeSettingsSection = SettingsDetailSection.NOTIFICATIONS }
                        )
                        SettingsMenuCard(
                            title = titleBackgroundSection,
                            description = descriptionBackgroundSection,
                            onClick = { activeSettingsSection = SettingsDetailSection.BACKGROUND }
                        )
                        SettingsMenuCard(
                            title = titleFocusLockSection,
                            description = descriptionFocusLockSection,
                            onClick = { activeSettingsSection = SettingsDetailSection.FOCUS_LOCK }
                        )
                        SettingsMenuCard(
                            title = titleAdvancedLockSection,
                            description = descriptionAdvancedLockSection,
                            onClick = { activeSettingsSection = SettingsDetailSection.ADVANCED_LOCK }
                        )
                        SettingsMenuCard(
                            title = titleResetSection,
                            description = descriptionResetSection,
                            onClick = { activeSettingsSection = SettingsDetailSection.RESET }
                        )
                    }
                } else {

                if (activeSettingsSection == SettingsDetailSection.GENERAL) {
                SettingsCategoryCard(
                    title = titleGeneralSection,
                    description = descriptionGeneralSection
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SettingsCardInnerSpacing)) {
                        Column {
                            Text(
                                text = titleUnitSystem,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = unitSystem == "km",
                                    onClick = { scope.launch { repository.setUnitSystem("km") } },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) { Text(stringResource(R.string.settings_unit_kilometers)) }
                                SegmentedButton(
                                    selected = unitSystem == "mi",
                                    onClick = {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.settings_unit_miles_in_progress),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) { Text(stringResource(R.string.settings_unit_miles)) }
                            }
                        }

                        PermissionSettingItem(
                            title = titleLanguage,
                            description = selectedLanguageLabel,
                            onClick = { showLanguageDialog = true }
                        )

                        Column {
                            Text(
                                text = titleScreenOrientation,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.size(10.dp))
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
                        }

                        Column {
                            Text(
                                text = titleMapStyle,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                styles.forEachIndexed { index, (id, label) ->
                                    SegmentedButton(
                                        selected = mapStyle == id,
                                        onClick = { scope.launch { repository.setMapStyle(id) } },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = styles.size)
                                    ) { Text(label) }
                                }
                            }
                        }
                    }
                }
                }

                if (activeSettingsSection == SettingsDetailSection.NOTIFICATIONS) {
                SettingsCategoryCard(
                    title = titleNotificationSection,
                    description = descriptionNotificationSection
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SettingsCardInnerSpacing)) {
                        ToggleSettingItem(
                            title = titleNotifications,
                            description = stringResource(R.string.settings_notifications_description),
                            checked = notificationsEnabled,
                            onCheckedChange = { scope.launch { repository.setNotificationsEnabled(it) } }
                        )

                        if (notificationsEnabled) {
                            Column(verticalArrangement = Arrangement.spacedBy(SettingsNotificationRowSpacing)) {
                                Text(
                                    text = titleNotificationInterval,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
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
                                modifier = Modifier.padding(start = SettingsNoticeStartPadding)
                            )
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.settings_title_flight_time_display),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = flightTimeDisplayMode == FlightTimeDisplayMode.REMAINING,
                                    onClick = {
                                        scope.launch {
                                            repository.setFlightTimeDisplayMode(FlightTimeDisplayMode.REMAINING)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                ) { Text(stringResource(R.string.settings_flight_time_remaining)) }
                                SegmentedButton(
                                    selected = flightTimeDisplayMode == FlightTimeDisplayMode.ELAPSED,
                                    onClick = {
                                        scope.launch {
                                            repository.setFlightTimeDisplayMode(FlightTimeDisplayMode.ELAPSED)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                ) { Text(stringResource(R.string.settings_flight_time_elapsed)) }
                            }
                            Text(
                                text = stringResource(R.string.settings_flight_time_display_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = FlightGray,
                                modifier = Modifier.padding(top = SettingsNoticeTopPadding, start = SettingsNoticeStartPadding)
                            )
                        }
                    }
                }
                }

                if (activeSettingsSection == SettingsDetailSection.BACKGROUND) {
                SettingsCategoryCard(
                    title = titleBackgroundSection,
                    description = descriptionBackgroundSection
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_title_flight_background_sound),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = flightBackgroundSound == FlightBackgroundSound.AIRPLANE_WHITE_NOISE,
                                onClick = {
                                    scope.launch {
                                        repository.setFlightBackgroundSound(FlightBackgroundSound.AIRPLANE_WHITE_NOISE)
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text(stringResource(R.string.settings_flight_background_sound_default))
                            }
                            SegmentedButton(
                                selected = flightBackgroundSound == FlightBackgroundSound.CUSTOM_MP3,
                                onClick = {
                                    if (flightBackgroundSoundCustomUri.isNullOrBlank()) {
                                        Toast.makeText(context, customMp3MissingMessage, Toast.LENGTH_SHORT).show()
                                        launchMp3PickerWithFocusLockBypass()
                                    } else {
                                        scope.launch {
                                            repository.setFlightBackgroundSound(FlightBackgroundSound.CUSTOM_MP3)
                                        }
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text(stringResource(R.string.settings_flight_background_sound_custom))
                            }
                        }
                        Text(
                            text = stringResource(R.string.settings_flight_background_sound_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = FlightGray,
                            modifier = Modifier.padding(top = SettingsNoticeTopPadding, start = SettingsNoticeStartPadding)
                        )
                        Text(
                            text = if (flightBackgroundSoundCustomUri.isNullOrBlank()) {
                                stringResource(R.string.settings_flight_background_sound_no_custom_file)
                            } else {
                                stringResource(
                                    R.string.settings_flight_background_sound_selected_file_format,
                                    flightBackgroundSoundCustomName ?: stringResource(R.string.settings_flight_background_sound_custom)
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = FlightGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = SettingsNoticeTopPadding, start = SettingsNoticeStartPadding)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { launchMp3PickerWithFocusLockBypass() }
                            ) {
                                Text(stringResource(R.string.settings_flight_background_sound_select_mp3))
                            }
                            if (!flightBackgroundSoundCustomUri.isNullOrBlank()) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            repository.clearFlightBackgroundSoundCustomFile()
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.settings_flight_background_sound_remove_mp3))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.padding(top = SettingsSectionSpacing))
                    BatteryOptimizationItem()
                }
                }

                if (activeSettingsSection == SettingsDetailSection.FOCUS_LOCK) {
                SettingsCategoryCard(
                    title = titleFocusLockSection,
                    description = descriptionFocusLockSection
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SettingsCardInnerSpacing)) {
                        ToggleSettingItem(
                            title = titleFocusLock,
                            description = stringResource(R.string.settings_focus_lock_description),
                            checked = focusLockEnabled,
                            onCheckedChange = { enabled ->
                                runProtectedFocusLockAction(forceFreshPin = !enabled) {
                                    scope.launch { repository.setFocusLockEnabled(enabled) }
                                }
                            }
                        )
                        Text(
                            text = stringResource(R.string.settings_focus_lock_requirements),
                            style = MaterialTheme.typography.bodySmall,
                            color = FlightGray,
                            modifier = Modifier.padding(start = SettingsNoticeStartPadding)
                        )
                        if (focusLockEnabled) {
                            PermissionSettingItem(
                                title = stringResource(R.string.settings_usage_access),
                                description = if (hasUsageAccess) {
                                    stringResource(R.string.settings_permission_allowed)
                                } else {
                                    stringResource(R.string.settings_permission_required_usage)
                                },
                                onClick = {
                                    runProtectedFocusLockAction {
                                        FocusLockUtils.openUsageAccessSettings(context)
                                    }
                                }
                            )
                            PermissionSettingItem(
                                title = stringResource(R.string.settings_overlay_permission),
                                description = if (canDrawOverlays) {
                                    stringResource(R.string.settings_permission_allowed)
                                } else {
                                    stringResource(R.string.settings_permission_required_overlay)
                                },
                                onClick = {
                                    runProtectedFocusLockAction {
                                        FocusLockUtils.openOverlaySettings(context)
                                    }
                                }
                            )
                        PermissionSettingItem(
                            title = titleAllowedApps,
                            description = if (restrictInFlightSettings) {
                                stringResource(R.string.settings_focus_lock_allowed_apps_locked_inflight)
                            } else if (visibleFocusLockAllowedPackages.isEmpty()) {
                                stringResource(R.string.settings_focus_lock_allowed_apps_empty)
                            } else {
                                stringResource(
                                    R.string.settings_focus_lock_allowed_apps_count,
                                    visibleFocusLockAllowedPackages.size
                                )
                            },
                            enabled = !restrictInFlightSettings,
                            onClick = {
                                runProtectedFocusLockAction {
                                    allowedAppsSelection = visibleFocusLockAllowedPackages
                                    allowedAppsQuery = ""
                                    showAllowedAppsDialog = true
                                    }
                                }
                            )
                        }
                    }
                }
                }

                if (activeSettingsSection == SettingsDetailSection.ADVANCED_LOCK) {
                SettingsCategoryCard(
                    title = titleAdvancedLockSection,
                    description = descriptionAdvancedLockSection
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SettingsCardInnerSpacing)) {
                        ToggleSettingItem(
                            title = titleAdvancedLock,
                            description = if (advancedLockEnabled) {
                                stringResource(R.string.settings_advanced_lock_description_enabled)
                            } else {
                                stringResource(R.string.settings_advanced_lock_description_disabled)
                            },
                            checked = advancedLockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showAdvancedLockEnableConfirmDialog = true
                                } else {
                                    runProtectedFocusLockAction(forceFreshPin = true) {
                                        scope.launch {
                                            repository.setAdvancedLockEnabled(false)
                                            focusLockAuthenticatedAtMillis = null
                                        }
                                    }
                                }
                            }
                        )
                        if (focusLockPinEnabled) {
                            PermissionSettingItem(
                                title = stringResource(R.string.settings_focus_lock_pin_change),
                                description = stringResource(R.string.settings_focus_lock_pin_description_enabled),
                                onClick = { showFocusLockChangePinDialog = true }
                            )
                            PermissionSettingItem(
                                title = stringResource(R.string.settings_focus_lock_pin_remove),
                                description = stringResource(R.string.settings_focus_lock_pin_remove_description),
                                onClick = { showFocusLockRemovePinDialog = true }
                            )
                            PermissionSettingItem(
                                title = stringResource(R.string.settings_focus_lock_pin_forgot),
                                description = stringResource(R.string.settings_focus_lock_pin_forgot_title),
                                onClick = { showFocusLockForgotPinDialog = true }
                            )
                        } else {
                            PermissionSettingItem(
                                title = stringResource(R.string.settings_focus_lock_pin_set),
                                description = stringResource(R.string.settings_focus_lock_pin_description_disabled),
                                onClick = {
                                    pendingEnableAdvancedLockAfterPinSetup = false
                                    showFocusLockSetPinDialog = true
                                }
                            )
                        }
                    }
                }
                }

                if (activeSettingsSection == SettingsDetailSection.RESET) {
                SettingsCategoryCard(
                    title = titleResetSection,
                    description = descriptionResetSection
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(SettingsCardInnerSpacing)) {
                        PermissionSettingItem(
                            title = stringResource(R.string.settings_restart_app_title),
                            description = stringResource(R.string.settings_restart_app_description),
                            onClick = { showRestartAppDialog = true }
                        )
                        PermissionSettingItem(
                            title = stringResource(R.string.settings_reset_app_title),
                            description = stringResource(R.string.settings_reset_app_description),
                            onClick = { showResetAppDialog = true }
                        )
                        PermissionSettingItem(
                            title = stringResource(R.string.settings_full_reset_app_title),
                            description = stringResource(R.string.settings_full_reset_app_description),
                            onClick = {
                                fullResetConfirmationInput = ""
                                showFullResetDialog = true
                            }
                        )
                    }
                }
                }
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
        val appLabelCollator = remember {
            Collator.getInstance(Locale.KOREAN).apply {
                strength = Collator.PRIMARY
            }
        }
        val allowedAppsGridColumns = remember(configuration.screenWidthDp) {
            when {
                configuration.screenWidthDp >= 1200 -> 6
                configuration.screenWidthDp >= 960 -> 5
                configuration.screenWidthDp >= 720 -> 4
                configuration.screenWidthDp >= 520 -> 3
                else -> 2
            }
        }
        val visibleLaunchableApps = remember(launchableApps, allowedAppsSelection, selfPackageName, appDisplayName) {
            launchableApps.filter { app ->
                !FocusLockUtils.shouldHideAllowedAppInUi(
                    packageName = app.packageName,
                    selectedPackages = allowedAppsSelection,
                    selfPackageName = selfPackageName,
                    applicationId = BuildConfig.APPLICATION_ID
                ) &&
                    !app.label.equals(appDisplayName, ignoreCase = true)
            }
        }
        val filteredLaunchableApps = remember(visibleLaunchableApps, allowedAppsQuery) {
            val normalizedQuery = allowedAppsQuery.trim()
            val apps = if (normalizedQuery.isEmpty()) {
                visibleLaunchableApps
            } else {
                visibleLaunchableApps.filter { app ->
                    app.label.contains(normalizedQuery, ignoreCase = true)
                }
            }
            apps.sortedWith { left, right ->
                val leftSelected = allowedAppsSelection.contains(left.packageName)
                val rightSelected = allowedAppsSelection.contains(right.packageName)
                when {
                    leftSelected && !rightSelected -> -1
                    !leftSelected && rightSelected -> 1
                    else -> appLabelCollator.compare(left.label, right.label)
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showAllowedAppsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(stringResource(R.string.settings_focus_lock_allowed_apps_dialog_title)) },
            text = {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                ) {
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
                        if (isLoadingLaunchableApps) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Loading apps...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = FlightGray
                                )
                            }
                        } else if (visibleLaunchableApps.isEmpty()) {
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
                                columns = GridCells.Fixed(allowedAppsGridColumns),
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
                                            if (!isChecked && app.packageName in protectedAllowedPackages) {
                                                protectedAllowedAppPendingRemoval = app
                                                protectedAllowedAppRemovalConfirmCount = 0
                                            } else {
                                                allowedAppsSelection =
                                                    if (isChecked) {
                                                        val nextSelection = allowedAppsSelection + app.packageName
                                                        if (app.packageName == FocusLockUtils.GeminiPackageName) {
                                                            nextSelection + FocusLockUtils.GoogleAppPackageName
                                                        } else {
                                                            nextSelection
                                                        }
                                                    } else {
                                                        val nextSelection = allowedAppsSelection - app.packageName
                                                        if (app.packageName == FocusLockUtils.GeminiPackageName) {
                                                            nextSelection - FocusLockUtils.GoogleAppPackageName
                                                        } else {
                                                            nextSelection
                                                        }
                                                    }
                                            }
                                        }
                                    )
                                }
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

    if (showFocusLockUnlockDialog) {
        FocusLockPinUnlockDialog(
            onDismiss = {
                showFocusLockUnlockDialog = false
                pendingProtectedFocusLockAction = null
            },
            onVerified = { pin, onFailure ->
                scope.launch {
                    if (repository.verifyFocusLockPin(pin)) {
                        focusLockAuthenticatedAtMillis = SystemClock.elapsedRealtime()
                        showFocusLockUnlockDialog = false
                        pendingProtectedFocusLockAction?.invoke()
                        pendingProtectedFocusLockAction = null
                    } else {
                        onFailure()
                    }
                }
            }
        )
    }

    if (showFocusLockSetPinDialog) {
        FocusLockSetPinDialog(
            onDismiss = {
                showFocusLockSetPinDialog = false
                pendingEnableAdvancedLockAfterPinSetup = false
            },
            onSave = { pin ->
                scope.launch {
                    repository.setFocusLockPin(pin)
                    if (pendingEnableAdvancedLockAfterPinSetup) {
                        repository.setAdvancedLockEnabled(true)
                    }
                    focusLockAuthenticatedAtMillis = SystemClock.elapsedRealtime()
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_focus_lock_pin_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                    pendingEnableAdvancedLockAfterPinSetup = false
                    showFocusLockSetPinDialog = false
                }
            }
        )
    }

    if (showFocusLockChangePinDialog) {
        FocusLockChangePinDialog(
            onDismiss = { showFocusLockChangePinDialog = false },
            onChange = { currentPin, newPin, onSuccess, onFailure ->
                scope.launch {
                    if (repository.changeFocusLockPin(currentPin, newPin)) {
                        focusLockAuthenticatedAtMillis = SystemClock.elapsedRealtime()
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_focus_lock_pin_changed),
                            Toast.LENGTH_SHORT
                        ).show()
                        onSuccess()
                        showFocusLockChangePinDialog = false
                    } else {
                        onFailure()
                    }
                }
            }
        )
    }

    if (showFocusLockRemovePinDialog) {
        FocusLockRemovePinDialog(
            onDismiss = { showFocusLockRemovePinDialog = false },
            onRemove = { currentPin, onSuccess, onFailure ->
                scope.launch {
                    if (repository.clearFocusLockPin(currentPin)) {
                        repository.setAdvancedLockEnabled(false)
                        focusLockAuthenticatedAtMillis = null
                        pendingProtectedFocusLockAction = null
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_focus_lock_pin_removed),
                            Toast.LENGTH_SHORT
                        ).show()
                        onSuccess()
                        showFocusLockRemovePinDialog = false
                    } else {
                        onFailure()
                    }
                }
            }
        )
    }

    if (showFocusLockForgotPinDialog) {
        AlertDialog(
            onDismissRequest = { showFocusLockForgotPinDialog = false },
            title = { Text(stringResource(R.string.settings_focus_lock_pin_forgot_title)) },
            text = { Text(stringResource(R.string.settings_focus_lock_pin_forgot_message)) },
            confirmButton = {
                TextButton(onClick = { showFocusLockForgotPinDialog = false }) {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        )
    }

    if (showAdvancedLockEnableConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showAdvancedLockEnableConfirmDialog = false },
            title = { Text(stringResource(R.string.settings_advanced_lock_enable_confirm_title)) },
            text = { Text(stringResource(R.string.settings_advanced_lock_enable_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAdvancedLockEnableConfirmDialog = false
                        if (!focusLockPinEnabled) {
                            pendingEnableAdvancedLockAfterPinSetup = true
                            showFocusLockSetPinDialog = true
                        } else {
                            scope.launch {
                                repository.setAdvancedLockEnabled(true)
                                focusLockAuthenticatedAtMillis = null
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvancedLockEnableConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showRestartAppDialog) {
        AlertDialog(
            onDismissRequest = { showRestartAppDialog = false },
            title = { Text(stringResource(R.string.settings_restart_app_title)) },
            text = { Text(stringResource(R.string.settings_restart_app_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartAppDialog = false
                        restartApp()
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestartAppDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showResetAppDialog) {
        AlertDialog(
            onDismissRequest = { showResetAppDialog = false },
            title = { Text(stringResource(R.string.settings_reset_app_title)) },
            text = { Text(stringResource(R.string.settings_reset_app_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetAppDialog = false
                        scope.launch {
                            repository.resetAppSettings()
                            restartApp()
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAppDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showFullResetDialog) {
        AlertDialog(
            onDismissRequest = {
                showFullResetDialog = false
                fullResetConfirmationInput = ""
            },
            title = { Text(stringResource(R.string.settings_full_reset_app_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.settings_full_reset_app_confirm_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = FlightGray
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = todayResetConfirmationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    OutlinedTextField(
                        value = fullResetConfirmationInput,
                        onValueChange = { fullResetConfirmationInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_full_reset_app_input_label)) }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = fullResetConfirmationInput == todayResetConfirmationText,
                    onClick = {
                        showFullResetDialog = false
                        scope.launch {
                            repository.resetAllAppData()
                            restartApp()
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showFullResetDialog = false
                        fullResetConfirmationInput = ""
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (protectedAllowedAppPendingRemoval != null) {
        AlertDialog(
            onDismissRequest = {
                protectedAllowedAppPendingRemoval = null
                protectedAllowedAppRemovalConfirmCount = 0
            },
            title = { Text(stringResource(R.string.settings_focus_lock_protected_apps_remove_title)) },
            text = {
                Text(stringResource(R.string.settings_focus_lock_protected_apps_remove_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        protectedAllowedAppRemovalConfirmCount += 1
                        if (protectedAllowedAppRemovalConfirmCount >= 3) {
                            protectedAllowedAppPendingRemoval?.packageName?.let { packageName ->
                                allowedAppsSelection = allowedAppsSelection - packageName
                            }
                            protectedAllowedAppPendingRemoval = null
                            protectedAllowedAppRemovalConfirmCount = 0
                        }
                    }
                ) {
                    Text(
                        stringResource(
                            R.string.settings_focus_lock_protected_apps_remove_confirm_count,
                            protectedAllowedAppRemovalConfirmCount + 1
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        protectedAllowedAppPendingRemoval = null
                        protectedAllowedAppRemovalConfirmCount = 0
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun FocusLockPinUnlockDialog(
    onDismiss: () -> Unit,
    onVerified: (String, onFailure: () -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val incorrectPinText = stringResource(R.string.settings_focus_lock_pin_error_incorrect)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_focus_lock_pin_unlock_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_focus_lock_pin_unlock_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightGray
                )
                Spacer(modifier = Modifier.size(12.dp))
                PinTextField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        errorMessage = null
                    }
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pin.length in FocusLockPinMinLength..FocusLockPinMaxLength,
                onClick = {
                    onVerified(pin) {
                        errorMessage = incorrectPinText
                        pin = ""
                    }
                }
            ) {
                Text(stringResource(R.string.settings_focus_lock_pin_unlock_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun FocusLockSetPinDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var step by remember { mutableStateOf(FocusLockPinEntryStep.NEW) }
    var firstPin by remember { mutableStateOf("") }
    var currentInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.settings_focus_lock_pin_new_title)
    val mismatchText = stringResource(R.string.settings_focus_lock_pin_error_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = if (step == FocusLockPinEntryStep.NEW) {
                        stringResource(R.string.settings_focus_lock_pin_new_description)
                    } else {
                        stringResource(R.string.settings_focus_lock_pin_step_confirm)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightGray
                )
                Spacer(modifier = Modifier.size(12.dp))
                PinTextField(
                    value = currentInput,
                    onValueChange = {
                        currentInput = it
                        errorMessage = null
                    },
                    label = if (step == FocusLockPinEntryStep.NEW) {
                        stringResource(R.string.settings_focus_lock_pin_step_new)
                    } else {
                        stringResource(R.string.settings_focus_lock_pin_step_confirm)
                    }
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = currentInput.length in FocusLockPinMinLength..FocusLockPinMaxLength,
                onClick = {
                    if (step == FocusLockPinEntryStep.NEW) {
                        firstPin = currentInput
                        currentInput = ""
                        step = FocusLockPinEntryStep.CONFIRM
                    } else if (currentInput == firstPin) {
                        onSave(firstPin)
                    } else {
                        errorMessage = mismatchText
                        firstPin = ""
                        currentInput = ""
                        step = FocusLockPinEntryStep.NEW
                    }
                }
            ) {
                Text(
                    stringResource(
                        if (step == FocusLockPinEntryStep.NEW) {
                            R.string.settings_focus_lock_pin_continue
                        } else {
                            R.string.settings_focus_lock_pin_save
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun FocusLockChangePinDialog(
    onDismiss: () -> Unit,
    onChange: (
        currentPin: String,
        newPin: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) -> Unit
) {
    var step by remember { mutableStateOf(FocusLockPinEntryStep.CURRENT) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var currentInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val mismatchText = stringResource(R.string.settings_focus_lock_pin_error_mismatch)
    val incorrectPinText = stringResource(R.string.settings_focus_lock_pin_error_incorrect)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_focus_lock_pin_change_title)) },
        text = {
            Column {
                Text(
                    text = when (step) {
                        FocusLockPinEntryStep.CURRENT -> stringResource(R.string.settings_focus_lock_pin_step_current)
                        FocusLockPinEntryStep.NEW -> stringResource(R.string.settings_focus_lock_pin_step_new)
                        FocusLockPinEntryStep.CONFIRM -> stringResource(R.string.settings_focus_lock_pin_step_confirm)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = FlightGray
                )
                Spacer(modifier = Modifier.size(12.dp))
                PinTextField(
                    value = currentInput,
                    onValueChange = {
                        currentInput = it
                        errorMessage = null
                    },
                    label = stringResource(R.string.settings_focus_lock_pin_input_label)
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = currentInput.length in FocusLockPinMinLength..FocusLockPinMaxLength,
                onClick = {
                    when (step) {
                        FocusLockPinEntryStep.CURRENT -> {
                            currentPin = currentInput
                            currentInput = ""
                            step = FocusLockPinEntryStep.NEW
                        }
                        FocusLockPinEntryStep.NEW -> {
                            newPin = currentInput
                            currentInput = ""
                            step = FocusLockPinEntryStep.CONFIRM
                        }
                        FocusLockPinEntryStep.CONFIRM -> {
                            if (currentInput != newPin) {
                                errorMessage = mismatchText
                                newPin = ""
                                currentInput = ""
                                step = FocusLockPinEntryStep.NEW
                            } else {
                                onChange(
                                    currentPin,
                                    newPin,
                                    onDismiss,
                                    {
                                        errorMessage = incorrectPinText
                                        currentPin = ""
                                        newPin = ""
                                        currentInput = ""
                                        step = FocusLockPinEntryStep.CURRENT
                                    }
                                )
                            }
                        }
                    }
                }
            ) {
                Text(
                    stringResource(
                        if (step == FocusLockPinEntryStep.CONFIRM) {
                            R.string.settings_focus_lock_pin_save
                        } else {
                            R.string.settings_focus_lock_pin_continue
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun FocusLockRemovePinDialog(
    onDismiss: () -> Unit,
    onRemove: (currentPin: String, onSuccess: () -> Unit, onFailure: () -> Unit) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var awaitingConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val incorrectPinText = stringResource(R.string.settings_focus_lock_pin_error_incorrect)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (awaitingConfirm) {
                        R.string.settings_focus_lock_pin_remove_confirm_title
                    } else {
                        R.string.settings_focus_lock_pin_remove_title
                    }
                )
            )
        },
        text = {
            Column {
                if (!awaitingConfirm) {
                    Text(
                        text = stringResource(R.string.settings_focus_lock_pin_remove_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = FlightGray
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    PinTextField(
                        value = currentPin,
                        onValueChange = {
                            currentPin = it
                            errorMessage = null
                        }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_focus_lock_pin_remove_confirm_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = FlightGray
                    )
                }
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = awaitingConfirm || currentPin.length in FocusLockPinMinLength..FocusLockPinMaxLength,
                onClick = {
                    if (!awaitingConfirm) {
                        awaitingConfirm = true
                    } else {
                        onRemove(
                            currentPin,
                            onDismiss,
                            {
                                awaitingConfirm = false
                                currentPin = ""
                                errorMessage = incorrectPinText
                            }
                        )
                    }
                }
            ) {
                Text(
                    stringResource(
                        if (awaitingConfirm) {
                            R.string.settings_focus_lock_pin_confirm_remove
                        } else {
                            R.string.settings_focus_lock_pin_continue
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun PinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null
) {
    val resolvedLabel = label ?: stringResource(R.string.settings_focus_lock_pin_input_label)
    OutlinedTextField(
        value = value,
        onValueChange = {
            onValueChange(it.filter(Char::isDigit).take(FocusLockPinMaxLength))
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(resolvedLabel) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedLabelColor = FlightGray,
            unfocusedLabelColor = FlightGray,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.06f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = FlightGray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))
            content()
        }
    }
}

@Composable
private fun SettingsMenuCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.06f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SettingsChevronSpacing)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = FlightGray,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = FlightGray
            )
        }
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
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SettingsItemVerticalPadding)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (enabled) Color.White else FlightGray,
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
            tint = if (enabled) Color.White else FlightGray
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

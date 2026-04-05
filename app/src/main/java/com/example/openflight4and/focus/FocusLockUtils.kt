package com.example.openflight4and.focus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

data class LaunchableApp(
    val packageName: String,
    val label: String
)

object FocusLockUtils {

    @Suppress("DEPRECATION")
    fun hasUsageAccess(context: Context): Boolean {
        val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOpsManager.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun getForegroundPackage(context: Context): String? {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10_000L
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var lastForegroundPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundPackage = event.packageName
            }
        }

        return lastForegroundPackage
    }

    fun getLaunchableApps(context: Context): List<LaunchableApp> {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty()
                if (label.isBlank()) return@mapNotNull null
                LaunchableApp(packageName = packageName, label = label)
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}

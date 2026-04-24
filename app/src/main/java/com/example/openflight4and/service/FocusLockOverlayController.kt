package com.example.openflight4and.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.openflight4and.MainActivity
import com.example.openflight4and.R
import com.example.openflight4and.focus.FocusLockUtils
import com.example.openflight4and.utils.FlightUtils

private const val FocusLockPanelCornerRadius = 42f
private const val FocusLockPanelStrokeWidth = 3
private const val FocusLockPanelHorizontalPadding = 56
private const val FocusLockPanelVerticalPadding = 72
private const val FocusLockMessageTopPadding = 28
private const val FocusLockMessageBottomPadding = 44
private const val FocusLockSummaryBottomPadding = 40
private const val FocusLockTitleTextSize = 24f
private const val FocusLockMessageTextSize = 17f
private const val FocusLockSummaryTextSize = 16f
private const val FocusLockPanelHorizontalMargin = 28
private const val FocusLockPanelVerticalMargin = 52
private const val FocusLockAllowedAppsColumns = 4
private const val FocusLockAllowedAppCellPadding = 12
private const val FocusLockAllowedAppIconSize = 96
private const val FocusLockAllowedAppLabelTopPadding = 10
private const val FocusLockAllowedAppsGridTopPadding = 8
private const val FocusLockAllowedAppsGridBottomPadding = 12
private const val FocusLockAllowedAppLabelTextSize = 12f

class FocusLockOverlayController(
    private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var summaryTextView: TextView? = null

    fun show(
        originIata: String,
        destinationIata: String,
        durationMinutes: Int,
        remainingSeconds: Long,
        allowedPackages: Collection<String> = emptyList(),
        allowAllowedAppsLaunch: Boolean = true
    ) {
        if (overlayView != null) {
            summaryTextView?.text = buildFlightSummary(originIata, destinationIata, remainingSeconds)
            return
        }

        val root = FrameLayout(context).apply {
            setBackgroundColor(0x00000000)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                // Keep the overlay visible and consume background taps.
            }
        }

        val blocker = View(context).apply {
            setBackgroundColor(0x00000000)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                // Consume taps outside the board.
            }
        }

        val panelBackground = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = FocusLockPanelCornerRadius
            setColor(0xF2131313.toInt())
            setStroke(FocusLockPanelStrokeWidth, 0xFF565656.toInt())
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = panelBackground
            setPadding(
                FocusLockPanelHorizontalPadding,
                FocusLockPanelVerticalPadding,
                FocusLockPanelHorizontalPadding,
                FocusLockPanelVerticalPadding
            )
            setOnClickListener {
                // Prevent clicks on the panel from falling through.
            }
        }

        val panelScroll = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(0x00000000)
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.focus_lock_overlay_title)
            textSize = FocusLockTitleTextSize
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }

        val message = TextView(context).apply {
            text = context.getString(R.string.focus_lock_overlay_message)
            textSize = FocusLockMessageTextSize
            gravity = Gravity.CENTER
            setTextColor(0xFFE2E2E2.toInt())
            setPadding(0, FocusLockMessageTopPadding, 0, FocusLockMessageBottomPadding)
        }

        val summary = TextView(context).apply {
            text = buildFlightSummary(originIata, destinationIata, remainingSeconds)
            textSize = FocusLockSummaryTextSize
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, FocusLockSummaryBottomPadding)
        }

        val button = Button(context).apply {
            text = context.getString(R.string.focus_lock_overlay_button)
            setOnClickListener {
                val intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN_INFLIGHT, true)
                    putExtra(MainActivity.EXTRA_ORIGIN_IATA, originIata)
                    putExtra(MainActivity.EXTRA_DESTINATION_IATA, destinationIata)
                    putExtra(MainActivity.EXTRA_DURATION_MINUTES, durationMinutes)
                }
                context.startActivity(intent)
            }
        }

        val allowedApps = allowedPackages
            .filter { packageName -> packageName != context.packageName }
            .filter { packageName ->
                !(FocusLockUtils.GeminiPackageName in allowedPackages &&
                    packageName == FocusLockUtils.GoogleAppPackageName)
            }
            .mapNotNull { packageName ->
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return@mapNotNull null
                val icon = runCatching {
                    context.packageManager.getApplicationIcon(packageName)
                }.getOrNull() ?: return@mapNotNull null
                val label = runCatching {
                    context.packageManager.getApplicationLabel(
                        context.packageManager.getApplicationInfo(packageName, 0)
                    ).toString()
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                FocusLockAllowedApp(packageName, label, icon, launchIntent)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

        val allowedAppsTitle = TextView(context).apply {
            text = context.getString(R.string.focus_lock_overlay_allowed_apps_title)
            textSize = 18f
            setTextColor(0xFFE2E2E2.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 36, 0, 20)
        }

        val allowedAppsGrid = GridLayout(context).apply {
            columnCount = FocusLockAllowedAppsColumns
            rowCount = ((allowedApps.size + FocusLockAllowedAppsColumns - 1) / FocusLockAllowedAppsColumns).coerceAtLeast(1)
            useDefaultMargins = true
            alignmentMode = GridLayout.ALIGN_BOUNDS
            setPadding(0, FocusLockAllowedAppsGridTopPadding, 0, FocusLockAllowedAppsGridBottomPadding)
        }

        fun createAllowedAppCell(app: FocusLockAllowedApp): View {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(
                    FocusLockAllowedAppCellPadding,
                    FocusLockAllowedAppCellPadding,
                    FocusLockAllowedAppCellPadding,
                    FocusLockAllowedAppCellPadding
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 28f
                    setColor(0x1FFFFFFF)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    hide()
                    context.startActivity(app.launchIntent.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                }

                addView(
                    ImageView(context).apply {
                        setImageDrawable(app.icon)
                    },
                    LinearLayout.LayoutParams(
                        FocusLockAllowedAppIconSize,
                        FocusLockAllowedAppIconSize
                    )
                )

                addView(
                    TextView(context).apply {
                        text = app.label
                        maxLines = 2
                        gravity = Gravity.CENTER
                        textSize = FocusLockAllowedAppLabelTextSize
                        setTextColor(0xFFE2E2E2.toInt())
                        setPadding(0, FocusLockAllowedAppLabelTopPadding, 0, 0)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        }

        panel.addView(title)
        panel.addView(message)
        panel.addView(summary)
        panel.addView(button)
        if (allowAllowedAppsLaunch && allowedApps.isNotEmpty()) {
            panel.addView(allowedAppsTitle)
            allowedApps.forEachIndexed { index, app ->
                allowedAppsGrid.addView(
                    createAllowedAppCell(app),
                    GridLayout.LayoutParams(
                        GridLayout.spec(index / FocusLockAllowedAppsColumns, 1f),
                        GridLayout.spec(index % FocusLockAllowedAppsColumns, 1f)
                    ).apply {
                        width = 0
                        setGravity(Gravity.FILL_HORIZONTAL)
                    }
                )
            }
            panel.addView(
                allowedAppsGrid,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        panelScroll.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            blocker,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ).apply {
                // background blocker
            }
        )

        root.addView(
            panelScroll,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            ).apply {
                marginStart = FocusLockPanelHorizontalMargin
                marginEnd = FocusLockPanelHorizontalMargin
                topMargin = FocusLockPanelVerticalMargin
                bottomMargin = FocusLockPanelVerticalMargin
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(root, params)
        overlayView = root
        summaryTextView = summary
    }

    private fun buildFlightSummary(
        originIata: String,
        destinationIata: String,
        remainingSeconds: Long
    ): String {
        return context.getString(
            R.string.focus_lock_overlay_flight_summary_format,
            originIata,
            destinationIata,
            FlightUtils.formatTimer(remainingSeconds.coerceAtLeast(0L))
        )
    }

    fun hide() {
        val view = overlayView ?: return
        windowManager.removeView(view)
        overlayView = null
        summaryTextView = null
    }

    fun isShowing(): Boolean = overlayView != null

    fun destroy() {
        hide()
    }
}

private data class FocusLockAllowedApp(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable,
    val launchIntent: Intent
)

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
import android.widget.LinearLayout
import android.widget.TextView
import com.example.openflight4and.MainActivity
import com.example.openflight4and.R

private const val FocusLockPanelCornerRadius = 42f
private const val FocusLockPanelStrokeWidth = 3
private const val FocusLockPanelHorizontalPadding = 56
private const val FocusLockPanelVerticalPadding = 72
private const val FocusLockMessageTopPadding = 28
private const val FocusLockMessageBottomPadding = 44
private const val FocusLockTitleTextSize = 24f
private const val FocusLockMessageTextSize = 17f
private const val FocusLockPanelHorizontalMargin = 28
private const val FocusLockPanelVerticalMargin = 52

class FocusLockOverlayController(
    private val context: Context
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun show(
        originIata: String,
        destinationIata: String,
        durationMinutes: Int
    ) {
        if (overlayView != null) return

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

        panel.addView(title)
        panel.addView(message)
        panel.addView(button)

        root.addView(
            blocker,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        root.addView(
            panel,
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
    }

    fun hide() {
        val view = overlayView ?: return
        windowManager.removeView(view)
        overlayView = null
    }

    fun isShowing(): Boolean = overlayView != null

    fun destroy() {
        hide()
    }
}

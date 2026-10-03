package dev.bubblespend.bt003proof

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

private const val OVERLAY_LOG = "BT003Proof"

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var expanded = false
    private var saved = false
    private var params: WindowManager.LayoutParams? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> removeOverlay("screen off")
                Intent.ACTION_USER_PRESENT -> if (Settings.canDrawOverlays(this@OverlayService)) showCollapsed()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        Log.i(OVERLAY_LOG, "service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW && Settings.canDrawOverlays(this)) showCollapsed()
        return START_NOT_STICKY
    }

    private fun showCollapsed() {
        removeOverlay("replace")
        expanded = false
        val pill = TextView(this).apply {
            text = "BubbleSpend"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(28, 18, 28, 18)
            setBackgroundColor(0xFF386A20.toInt())
            contentDescription = "Floating shortcut, collapsed. Double tap to open synthetic entry. Long press to open the app."
            setOnClickListener { showExpanded() }
            setOnLongClickListener {
                Log.i(OVERLAY_LOG, "main app launch requested")
                val launch = Intent(this@OverlayService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(launch)
                true
            }
        }
        install(pill, 0)
        Log.i(OVERLAY_LOG, "overlay collapsed")
    }

    private fun showExpanded() {
        expanded = true
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 24, 28, 24)
            setBackgroundColor(0xFFFAF9F6.toInt())
            addView(TextView(this@OverlayService).apply { text = "Synthetic entry"; textSize = 20f; setTextColor(0xFF1A1C18.toInt()) })
            addView(TextView(this@OverlayService).apply { text = "Fixed proof fixture · no personal data"; textSize = 16f; setTextColor(0xFF1A1C18.toInt()) })
            addView(Button(this@OverlayService).apply {
                text = if (saved) "Saved once" else "Save synthetic entry"
                isEnabled = !saved
                setOnClickListener {
                    if (!saved) {
                        saved = true
                        if (markSavedOnce(this@OverlayService, "overlay")) {
                            Log.i(OVERLAY_LOG, "synthetic save completed")
                            showSaved()
                        }
                    }
                }
            })
            addView(Button(this@OverlayService).apply { text = "Close without saving"; setOnClickListener { showCollapsed() } })
        }
        panel.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                showCollapsed()
                true
            } else false
        }
        install(panel, WindowManager.LayoutParams.WRAP_CONTENT)
        Log.i(OVERLAY_LOG, "overlay expanded")
    }

    private fun showSaved() {
        val message = TextView(this).apply {
            text = "Saved once"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(28, 18, 28, 18)
            setBackgroundColor(0xFF386A20.toInt())
            contentDescription = "Synthetic entry saved once"
            setOnClickListener { showCollapsed() }
        }
        install(message, WindowManager.LayoutParams.WRAP_CONTENT)
        Log.i(OVERLAY_LOG, "save confirmation shown")
    }

    private fun install(view: View, width: Int) {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        val layout = WindowManager.LayoutParams(
            if (width == 0) WindowManager.LayoutParams.WRAP_CONTENT else width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 180 }
        if (!expanded) attachDrag(view, layout)
        try {
            windowManager.addView(view, layout)
            overlay = view
            params = layout
        } catch (_: SecurityException) {
            Log.i(OVERLAY_LOG, "overlay permission revoked")
            stopSelf()
        }
    }

    private fun attachDrag(view: View, layout: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var downAt = 0L
        var dragging = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = layout.x; startY = layout.y; downAt = event.eventTime; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 12) dragging = true
                    if (dragging) {
                        val bounds = android.graphics.Rect()
                        windowManager.defaultDisplay.getRectSize(bounds)
                        layout.x = (startX + dx).coerceIn(0, (bounds.width() - view.width).coerceAtLeast(0))
                        layout.y = (startY + dy).coerceIn(0, (bounds.height() - view.height).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(view, layout) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        val bounds = android.graphics.Rect()
                        windowManager.defaultDisplay.getRectSize(bounds)
                        layout.x = if (layout.x + view.width / 2 < bounds.width() / 2) 0 else (bounds.width() - view.width).coerceAtLeast(0)
                        layout.y = layout.y.coerceIn(0, (bounds.height() - view.height).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(view, layout) }
                        Log.i(OVERLAY_LOG, "overlay drag ended and snapped")
                    } else if (event.eventTime - downAt >= 500L) {
                        view.performLongClick()
                    } else {
                        view.performClick()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun removeOverlay(reason: String) {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        params = null
        if (reason != "replace") Log.i(OVERLAY_LOG, "overlay removed: $reason")
    }

    override fun onDestroy() {
        removeOverlay("service stopped")
        unregisterReceiver(screenReceiver)
        Log.i(OVERLAY_LOG, "service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val current = overlay ?: return
        val layout = params ?: return
        val bounds = android.graphics.Rect()
        windowManager.defaultDisplay.getRectSize(bounds)
        layout.x = layout.x.coerceIn(0, (bounds.width() - current.width).coerceAtLeast(0))
        layout.y = layout.y.coerceIn(0, (bounds.height() - current.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(current, layout) }
        Log.i(OVERLAY_LOG, "overlay repositioned after display change")
    }

    companion object {
        const val ACTION_SHOW = "dev.bubblespend.bt003proof.SHOW"
        private const val PREFS = "bt003_synthetic_proof"
        private const val KEY_SAVED = "save_count"
        fun savedCount(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SAVED, 0)

        @Synchronized
        fun markSavedOnce(context: Context, path: String): Boolean {
            val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = "saved_$path"
            if (preferences.getBoolean(key, false)) return true
            return preferences.edit().putBoolean(key, true).putInt(KEY_SAVED, savedCount(context) + 1).commit()
        }
    }
}

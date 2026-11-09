package com.myapp.autoscroll

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import kotlin.math.abs
import kotlin.random.Random

class ScrollService : AccessibilityService() {

    companion object {
        var instance: ScrollService? = null
        val TIKTOK = setOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go",
            "com.google.android.youtube",
            "com.instagram.android"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("prefs", Context.MODE_PRIVATE) }
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var running = false
    private var count = 0
    private var stopAt = 0L
    private var currentPkg = ""

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { stopScroll() }
    }

    private val loop = Runnable { tick() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BATTERY_LOW)
        }
        registerReceiver(receiver, f)
        showBubble()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui" || pkg.contains("inputmethod")) return
        currentPkg = pkg
    }

    override fun onInterrupt() { stopScroll() }

    override fun onUnbind(intent: Intent?): Boolean { cleanup(); return super.onUnbind(intent) }
    override fun onDestroy() { cleanup(); super.onDestroy() }

    private fun cleanup() {
        stopScroll()
        hideBubble()
        try { unregisterReceiver(receiver) } catch (e: Exception) {}
        instance = null
    }

    // ---------- scrolling ----------
    private fun startScroll() {
        if (running) return
        running = true
        count = 0
        val t = prefs.getInt("timerMin", 0)
        stopAt = if (t > 0) SystemClock.elapsedRealtime() + t * 60_000L else 0L
        updateBubble()
        handler.postDelayed(loop, 1500)
    }

    private fun stopScroll() {
        running = false
        handler.removeCallbacks(loop)
        updateBubble()
    }

    private fun tick() {
        if (!running) return
        if (stopAt > 0 && SystemClock.elapsedRealtime() >= stopAt) { stopScroll(); return }
        val onlyTikTok = prefs.getBoolean("tiktokOnly", true)
        if (!onlyTikTok || currentPkg in TIKTOK) {
            swipeUp()
            count++
            updateBubble()
        }
        handler.postDelayed(loop, nextDelay())
    }

    private fun nextDelay(): Long {
        val min = prefs.getInt("minSec", 8).coerceAtLeast(1)
        val max = prefs.getInt("maxSec", 15).coerceAtLeast(min)
        return ((min + Random.nextFloat() * (max - min)) * 1000).toLong()
    }

    private fun swipeUp() {
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f + Random.nextInt(-40, 40)
        val y1 = dm.heightPixels * 0.75f + Random.nextInt(-30, 30)
        val y2 = dm.heightPixels * 0.25f + Random.nextInt(-30, 30)
        val path = Path().apply {
            moveTo(x, y1)
            lineTo(x + Random.nextInt(-25, 25), y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, Random.nextLong(220, 380))
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    // ---------- floating button ----------
    fun toggleBubble() { if (bubble == null) showBubble() else hideBubble() }

    private fun updateBubble() {
        bubble?.text = if (running) "⏸\n$count" else "▶"
    }

    private fun showBubble() {
        if (bubble != null) return
        val density = resources.displayMetrics.density
        val size = (58 * density).toInt()
        val tv = TextView(this).apply {
            text = "▶"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xDD6200EA.toInt())
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }
        tv.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = lp.x; sy = lp.y; tx = e.rawX; ty = e.rawY; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - tx).toInt()
                        val dy = (e.rawY - ty).toInt()
                        if (abs(dx) > 15 || abs(dy) > 15) moved = true
                        if (moved) { lp.x = sx + dx; lp.y = sy + dy; wm.updateViewLayout(v, lp) }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) { if (running) stopScroll() else startScroll() }
                }
                return true
            }
        })
        wm.addView(tv, lp)
        bubble = tv
    }

    private fun hideBubble() {
        bubble?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        bubble = null
    }
}

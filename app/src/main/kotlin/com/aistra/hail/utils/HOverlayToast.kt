package io.spasum.hailshizuku.utils

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.spasum.hailshizuku.HailApp.Companion.app

object HOverlayToast {
    private var windowManager: WindowManager? = null
    private var currentView: ScrollView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { removeCurrentView() }

    fun canDraw(): Boolean = Settings.canDrawOverlays(app)

    fun show(text: String) {
        handler.post {
            removeCurrentView()
            if (!canDraw()) {
                Toast.makeText(app, text, Toast.LENGTH_LONG).show()
                return@post
            }
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm
            val dm = app.resources.displayMetrics
            val d = dm.density
            val pad = (14 * d).toInt()
            val maxW = (minOf(dm.widthPixels, dm.heightPixels) * 0.85f).toInt()
            val maxH = (dm.heightPixels * 0.4f).toInt()

            val bg = GradientDrawable().apply {
                setColor(0xE0212121.toInt())
                cornerRadius = 18 * d
            }
            val tv = TextView(app).apply {
                this.text = text
                textSize = 14f
                setTextColor(0xFFEEEEEE.toInt())
                setPadding(pad, pad, pad, pad)
            }
            val sv = ScrollView(app).apply {
                setBackground(bg)
                addView(tv)
                isClickable = true
                setOnClickListener { removeCurrentView() }
            }
            currentView = sv

            val params = WindowManager.LayoutParams(
                maxW,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (72 * d).toInt()
            }

            try {
                wm.addView(sv, params)
                sv.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        sv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        if (sv.height > maxH) {
                            params.height = maxH
                            runCatching { wm.updateViewLayout(sv, params) }
                        }
                    }
                })
                handler.postDelayed(dismissRunnable, 5000L)
            } catch (_: Exception) {
                currentView = null
                Toast.makeText(app, text, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun removeCurrentView() {
        handler.removeCallbacks(dismissRunnable)
        currentView?.let { v ->
            runCatching { windowManager?.removeViewImmediate(v) }
            currentView = null
        }
    }
}

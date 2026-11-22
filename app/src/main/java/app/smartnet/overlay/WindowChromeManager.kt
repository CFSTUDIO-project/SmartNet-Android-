package app.smartnet.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import app.smartnet.R
import app.smartnet.SmartNetPrefs
import app.smartnet.bridge.ShizukuBridge
import app.smartnet.ui.UiKit
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/** Adds a thin Smart .Net control bar above each real Android freeform task. */
class WindowChromeManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val remote = Executors.newSingleThreadExecutor()
    private val entries = LinkedHashMap<Int, Entry>()

    private data class Entry(
        val taskId: Int,
        val label: String,
        val bar: LinearLayout,
        val grip: TextView,
        val barParams: WindowManager.LayoutParams,
        val gripParams: WindowManager.LayoutParams,
        var left: Int,
        var top: Int,
        var right: Int,
        var bottom: Int,
        var restoreBounds: Rect? = null,
        var resizeRunnable: Runnable? = null
    )

    fun addWindow(taskId: Int, label: String, left: Int, top: Int, right: Int, bottom: Int) {
        if (entries.containsKey(taskId)) removeEntry(taskId)
        val titleBarHeight = UiKit.dp(context, 42f)
        val gripSize = UiKit.dp(context, 29f)
        val width = max(UiKit.dp(context, 220f), right - left)
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(context, 9f), 0, UiKit.dp(context, 4f), 0)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(
                Color.argb(244, 27, 34, 47), Color.argb(236, 20, 24, 34)
            )).apply {
                cornerRadius = UiKit.dp(context, 13f).toFloat()
                setStroke(UiKit.dp(context, 1f), Color.argb(68, 255, 255, 255))
            }
            elevation = UiKit.dp(context, 12f).toFloat()
            isClickable = true
        }
        val logo = ImageView(context).apply { setImageResource(R.drawable.smartnet_logo) }
        bar.addView(logo, LinearLayout.LayoutParams(UiKit.dp(context, 22f), UiKit.dp(context, 22f)))
        val title = UiKit.text(context, "Smart .net  |  $label", 11f, UiKit.text, true).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(UiKit.dp(context, 8f), 0, UiKit.dp(context, 5f), 0)
        }
        bar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val minimize = controlButton("—", UiKit.text)
        val maximize = controlButton("□", UiKit.cyan)
        val close = controlButton("×", UiKit.red)
        bar.addView(minimize, controlParams())
        bar.addView(maximize, controlParams())
        bar.addView(close, controlParams())

        val grip = TextView(context).apply {
            text = "⋱"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = UiKit.dp(context, 8f).toFloat()
                setColor(Color.argb(205, 29, 38, 54))
                setStroke(UiKit.dp(context, 1f), Color.argb(65, 255, 255, 255))
            }
            elevation = UiKit.dp(context, 10f).toFloat()
        }
        val barParams = WindowManager.LayoutParams(
            width, titleBarHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT; x = left; y = top }
        val gripParams = WindowManager.LayoutParams(
            gripSize, gripSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT; x = right - gripSize; y = bottom - gripSize }
        val entry = Entry(taskId, label, bar, grip, barParams, gripParams, left, top, right, bottom)

        minimize.setOnClickListener { minimize(entry) }
        maximize.setOnClickListener { toggleMaximize(entry) }
        close.setOnClickListener { close(entry) }
        attachDrag(entry, title)
        attachResize(entry)

        runCatching {
            windowManager.addView(bar, barParams)
            windowManager.addView(grip, gripParams)
            entries[taskId] = entry
            bar.alpha = 0f
            grip.alpha = 0f
            if (SmartNetPrefs.getBoolean(context, "animations", true)) {
                bar.animate().alpha(1f).translationY(0f).setDuration(250).start()
                grip.animate().alpha(1f).setDuration(250).start()
            } else {
                bar.alpha = 1f
                grip.alpha = 1f
            }
        }.onFailure {
            runCatching { windowManager.removeView(bar) }
            runCatching { windowManager.removeView(grip) }
            Toast.makeText(context, "Window controls could not be attached: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun controlButton(symbol: String, color: Int): TextView = UiKit.text(context, symbol, 17f, color, true).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
    }

    private fun controlParams() = LinearLayout.LayoutParams(UiKit.dp(context, 32f), UiKit.dp(context, 36f))

    private fun attachDrag(entry: Entry, touchTarget: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        touchTarget.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = entry.left
                    startY = entry.top
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    entry.left = startX + (event.rawX - downRawX).toInt()
                    entry.top = startY + (event.rawY - downRawY).toInt()
                    clamp(entry)
                    updateLocal(entry)
                    scheduleRemoteResize(entry)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    scheduleRemoteResize(entry, immediate = true)
                    true
                }
                else -> true
            }
        }
    }

    private fun attachResize(entry: Entry) {
        var downRawX = 0f
        var downRawY = 0f
        var startRight = 0
        var startBottom = 0
        entry.grip.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startRight = entry.right
                    startBottom = entry.bottom
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val minW = UiKit.dp(context, 220f)
                    val minH = UiKit.dp(context, 190f)
                    entry.right = max(entry.left + minW, startRight + (event.rawX - downRawX).toInt())
                    entry.bottom = max(entry.top + minH, startBottom + (event.rawY - downRawY).toInt())
                    clamp(entry)
                    updateLocal(entry)
                    scheduleRemoteResize(entry)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    scheduleRemoteResize(entry, immediate = true)
                    true
                }
                else -> true
            }
        }
    }

    private fun clamp(entry: Entry) {
        val bounds = if (android.os.Build.VERSION.SDK_INT >= 30) windowManager.currentWindowMetrics.bounds else android.graphics.Rect(0, 0, context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
        val minWidth = UiKit.dp(context, 220f)
        val minHeight = UiKit.dp(context, 190f)
        val width = (entry.right - entry.left).coerceAtLeast(minWidth).coerceAtMost(bounds.width())
        val height = (entry.bottom - entry.top).coerceAtLeast(minHeight).coerceAtMost(bounds.height())
        entry.left = entry.left.coerceIn(0, max(0, bounds.width() - width))
        entry.top = entry.top.coerceIn(0, max(0, bounds.height() - height))
        entry.right = entry.left + width
        entry.bottom = entry.top + height
    }

    private fun updateLocal(entry: Entry) {
        entry.barParams.x = entry.left
        entry.barParams.y = entry.top
        entry.barParams.width = entry.right - entry.left
        entry.gripParams.x = entry.right - entry.gripParams.width
        entry.gripParams.y = entry.bottom - entry.gripParams.height
        runCatching { windowManager.updateViewLayout(entry.bar, entry.barParams) }
        runCatching { windowManager.updateViewLayout(entry.grip, entry.gripParams) }
    }

    private fun scheduleRemoteResize(entry: Entry, immediate: Boolean = false) {
        entry.resizeRunnable?.let { handler.removeCallbacks(it) }
        val request = Runnable {
            val left = entry.left
            val top = entry.top
            val right = entry.right
            val bottom = entry.bottom
            remote.execute {
                runCatching { ShizukuBridge.controller?.resizeTask(entry.taskId, left, top, right, bottom) }
            }
        }
        entry.resizeRunnable = request
        handler.postDelayed(request, if (immediate) 0L else 75L)
    }

    private fun minimize(entry: Entry) {
        remote.execute {
            val ok = runCatching { ShizukuBridge.controller?.minimizeTask(entry.taskId) == true }.getOrDefault(false)
            handler.post {
                if (ok) removeEntry(entry.taskId) else Toast.makeText(context, "This Android build did not allow minimizing this task.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun close(entry: Entry) {
        remote.execute {
            val ok = runCatching { ShizukuBridge.controller?.closeTask(entry.taskId) == true }.getOrDefault(false)
            handler.post {
                if (ok) removeEntry(entry.taskId) else Toast.makeText(context, "Android refused to close this task.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleMaximize(entry: Entry) {
        val bounds = if (android.os.Build.VERSION.SDK_INT >= 30) windowManager.currentWindowMetrics.bounds else android.graphics.Rect(0, 0, context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
        if (entry.restoreBounds == null) {
            entry.restoreBounds = Rect(entry.left, entry.top, entry.right, entry.bottom)
            entry.left = 0
            entry.top = 0
            entry.right = bounds.width()
            entry.bottom = bounds.height()
        } else {
            val old = entry.restoreBounds!!
            entry.left = old.left
            entry.top = old.top
            entry.right = old.right
            entry.bottom = old.bottom
            entry.restoreBounds = null
        }
        clamp(entry)
        updateLocal(entry)
        scheduleRemoteResize(entry, immediate = true)
    }

    private fun removeEntry(taskId: Int) {
        val entry = entries.remove(taskId) ?: return
        entry.resizeRunnable?.let { handler.removeCallbacks(it) }
        runCatching { windowManager.removeView(entry.bar) }
        runCatching { windowManager.removeView(entry.grip) }
    }

    fun destroy() {
        entries.keys.toList().forEach(::removeEntry)
        remote.shutdownNow()
    }
}

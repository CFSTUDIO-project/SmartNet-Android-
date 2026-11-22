package app.smartnet.overlay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import app.smartnet.AppPickerActivity
import app.smartnet.SmartNetPrefs
import app.smartnet.bridge.ShizukuBridge
import app.smartnet.ui.UiKit
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

internal data class SmartApp(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: android.graphics.drawable.Drawable
)

class SmartOverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val chrome = WindowChromeManager(context)
    private var handle: TextView? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var sidebar: FrameLayout? = null
    private var sidebarPanel: LinearLayout? = null
    private var editMode = false
    private var destroyed = false

    fun showHandle() {
        if (destroyed || handle != null) return
        val metrics = screenSize()
        val width = UiKit.dp(context, 32f)
        val height = UiKit.dp(context, 70f)
        val view = TextView(context).apply {
            text = "|"
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                Color.argb(218, 48, 58, 76), Color.argb(218, 20, 24, 34)
            )).apply {
                cornerRadius = UiKit.dp(context, 18f).toFloat()
                setStroke(UiKit.dp(context, 1f), Color.argb(50, 255, 255, 255))
            }
            elevation = UiKit.dp(context, 10f).toFloat()
        }
        val side = SmartNetPrefs.getString(context, "handle_side", "right")
        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = if (side == "left") 0 else metrics.first - width
            y = ((metrics.second - height) / 2).coerceAtLeast(0)
        }
        attachHandleGesture(view, params)
        val handleAdded = runCatching { windowManager.addView(view, params) }.onFailure {
            Toast.makeText(context, "Smart .Net overlay could not be displayed: ${it.message}", Toast.LENGTH_LONG).show()
        }.isSuccess
        if (!handleAdded) return
        handle = view
        handleParams = params
        if (SmartNetPrefs.getBoolean(context, "animations", true)) {
            view.alpha = 0f
            view.translationX = UiKit.dp(context, 15f).toFloat()
            view.animate().alpha(1f).translationX(0f).setDuration(360).start()
            val pulse = android.animation.ObjectAnimator.ofFloat(view, "alpha", 1f, 0.83f, 1f)
            pulse.duration = 1700
            pulse.repeatCount = android.animation.ValueAnimator.INFINITE
            pulse.start()
            view.tag = pulse
        }
    }

    private fun attachHandleGesture(view: TextView, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var originX = 0
        var originY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originX = params.x
                    originY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > UiKit.dp(context, 5f)) moved = true
                    if (moved) {
                        val (screenW, screenH) = screenSize()
                        params.x = (originX + dx).coerceIn(0, screenW - view.width)
                        params.y = (originY + dy).coerceIn(0, screenH - view.height)
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (SmartNetPrefs.getBoolean(context, "haptics", true)) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                        openSidebar()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun openSidebar(resetEdit: Boolean = true) {
        if (sidebar != null || destroyed) return
        if (resetEdit) editMode = false
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(72, 0, 0, 0))
            setOnClickListener { closeSidebar() }
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(context, 18f), UiKit.dp(context, 18f), UiKit.dp(context, 18f), UiKit.dp(context, 15f))
            val opacity = (SmartNetPrefs.getInt(context, "panel_opacity", 88) * 2.55f).toInt().coerceIn(0, 255)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                Color.argb(opacity, 28, 32, 43), Color.argb((opacity * 0.92f).toInt(), 15, 19, 28)
            )).apply {
                cornerRadius = UiKit.dp(context, 27f).toFloat()
                setStroke(UiKit.dp(context, 1f), Color.argb(44, 255, 255, 255))
            }
            elevation = UiKit.dp(context, 20f).toFloat()
            isClickable = true
            isFocusable = true
        }
        val width = min(UiKit.dp(context, 318f), (screenSize().first * 0.9f).toInt())
        val side = SmartNetPrefs.getString(context, "handle_side", "right")
        val panelParams = FrameLayout.LayoutParams(width, -2, (if (side == "left") Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL).apply {
            if (side == "left") leftMargin = UiKit.dp(context, 7f) else rightMargin = UiKit.dp(context, 7f)
            topMargin = UiKit.dp(context, 16f)
            bottomMargin = UiKit.dp(context, 16f)
        }
        root.addView(panel, panelParams)
        buildSidebarContents(panel)

        val params = WindowManager.LayoutParams(
            -1, -1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (Build.VERSION.SDK_INT >= 31 && SmartNetPrefs.getBoolean(context, "blur", true)) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT }
        if (Build.VERSION.SDK_INT >= 31 && SmartNetPrefs.getBoolean(context, "blur", true)) {
            runCatching { params.setBlurBehindRadius(UiKit.dp(context, 20f)) }
        }
        val sidebarAdded = runCatching { windowManager.addView(root, params) }.onFailure {
            Toast.makeText(context, "Could not open sidebar: ${it.message}", Toast.LENGTH_SHORT).show()
        }.isSuccess
        if (!sidebarAdded) return
        sidebar = root
        sidebarPanel = panel
        if (SmartNetPrefs.getBoolean(context, "animations", true)) {
            val slideFrom = if (side == "left") -width.toFloat() else width.toFloat()
            panel.translationX = slideFrom
            panel.alpha = 0.72f
            panel.animate().translationX(0f).alpha(1f).setDuration(420)
                .setInterpolator(android.view.animation.OvershootInterpolator(0.78f)).start()
            panel.scaleX = 0.965f
            panel.scaleY = 0.965f
            panel.animate().scaleX(1f).scaleY(1f).setDuration(430).start()
        }
    }

    private fun buildSidebarContents(panel: LinearLayout) {
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(context).apply { setImageResource(app.smartnet.R.drawable.smartnet_logo) }
        header.addView(logo, LinearLayout.LayoutParams(UiKit.dp(context, 28f), UiKit.dp(context, 28f)))
        header.addView(UiKit.text(context, "Smart .net", 15f, UiKit.text, true).apply {
            setPadding(UiKit.dp(context, 9f), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(UiKit.text(context, "•", 18f, UiKit.green, true))
        panel.addView(header)
        panel.addView(UiKit.divider(context))

        val appsGrid = GridLayout(context).apply {
            columnCount = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 5 else 3
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        val apps = favoriteApps()
        apps.forEach { app ->
            appsGrid.addView(createAppCell(appsGrid, app), gridParams(appsGrid.columnCount))
        }
        val addCell = createAddCell()
        appsGrid.addView(addCell, gridParams(appsGrid.columnCount))
        val appScroll = android.widget.ScrollView(context).apply {
            isFillViewport = false
            clipToPadding = false
            addView(appsGrid)
        }
        val scrollHeight = min(UiKit.dp(context, 455f), (screenSize().second * 0.58f).toInt())
        panel.addView(appScroll, LinearLayout.LayoutParams(-1, scrollHeight.coerceAtLeast(UiKit.dp(context, 160f))))

        panel.addView(UiKit.button(context, if (editMode) "Done" else "+ Edit").apply {
            setPadding(0, UiKit.dp(context, 12f), 0, UiKit.dp(context, 12f))
            setOnClickListener {
                editMode = !editMode
                closeSidebar(animated = false)
                openSidebar(resetEdit = false)
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiKit.dp(context, 14f) })
    }

    private fun createAppCell(grid: GridLayout, app: SmartApp): LinearLayout {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(context, 5f), UiKit.dp(context, 9f), UiKit.dp(context, 5f), UiKit.dp(context, 8f))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply { cornerRadius = UiKit.dp(context, 17f).toFloat(); setColor(Color.TRANSPARENT) }
        }
        val icon = ImageView(context).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        cell.addView(icon, LinearLayout.LayoutParams(UiKit.dp(context, 43f), UiKit.dp(context, 43f)))
        val label = UiKit.text(context, app.label, 10f, Color.rgb(225, 230, 238)).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, UiKit.dp(context, 6f), 0, 0)
        }
        cell.addView(label, LinearLayout.LayoutParams(-1, -2))
        cell.setOnClickListener {
            if (editMode) {
                val favorites = SmartNetPrefs.getFavorites(context)
                favorites.removeAll { componentPackage(it) == app.packageName }
                SmartNetPrefs.saveFavorites(context, favorites)
                closeSidebar(animated = false)
                openSidebar(resetEdit = false)
            } else launchRealWindow(app)
        }
        cell.setOnLongClickListener {
            if (!editMode) {
                Toast.makeText(context, "Tap + Edit to remove an app shortcut", Toast.LENGTH_SHORT).show()
                true
            } else false
        }
        cell.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (SmartNetPrefs.getBoolean(context, "haptics", true)) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    if (SmartNetPrefs.getBoolean(context, "animations", true)) view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
        return cell
    }

    private fun createAddCell(): LinearLayout {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(context, 5f), UiKit.dp(context, 9f), UiKit.dp(context, 5f), UiKit.dp(context, 8f))
            isClickable = true
            isFocusable = true
        }
        val plus = TextView(context).apply {
            text = "+"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(UiKit.text)
            background = UiKit.glass(context, 15f, UiKit.surfaceRaised)
        }
        cell.addView(plus, LinearLayout.LayoutParams(UiKit.dp(context, 43f), UiKit.dp(context, 43f)))
        cell.addView(UiKit.text(context, "Add app", 10f, Color.rgb(225, 230, 238)).apply {
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(context, 6f), 0, 0)
        })
        cell.setOnClickListener {
            closeSidebar()
            context.startActivity(Intent(context, AppPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return cell
    }

    private fun gridParams(columns: Int): GridLayout.LayoutParams = GridLayout.LayoutParams().apply {
        width = 0
        height = UiKit.dp(context, 82f)
        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        setMargins(UiKit.dp(context, 2f), UiKit.dp(context, 2f), UiKit.dp(context, 2f), UiKit.dp(context, 2f))
    }

    private fun favoriteApps(): List<SmartApp> {
        val pm = context.packageManager
        val packages = SmartNetPrefs.getFavorites(context)
        val result = mutableListOf<SmartApp>()
        packages.forEach { saved ->
            val packageName = componentPackage(saved)
            val launchIntent = runCatching { pm.getLaunchIntentForPackage(packageName) }.getOrNull() ?: return@forEach
            val component = launchIntent.component ?: return@forEach
            val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull() ?: return@forEach
            result.add(SmartApp(
                pm.getApplicationLabel(appInfo).toString(), packageName, component,
                runCatching { pm.getApplicationIcon(appInfo) }.getOrElse { context.getDrawable(android.R.drawable.sym_def_app_icon)!! }
            ))
        }
        return result.distinctBy { it.packageName }
    }

    private fun componentPackage(component: String): String = component.substringBefore('/')

    private fun launchRealWindow(app: SmartApp) {
        closeSidebar()
        val controller = ShizukuBridge.controller
        if (controller == null) {
            Toast.makeText(context, "Shizuku system service is disconnected.", Toast.LENGTH_LONG).show()
            return
        }
        val (screenW, screenH) = screenSize()
        val landscape = screenW > screenH
        val density = context.resources.displayMetrics.density
        val minWidth = (320f * density).toInt()
        val widthPercent = SmartNetPrefs.getInt(context, "window_width_pct", 75).coerceIn(60, 95)
        val width = if (landscape) max((screenW * 0.50f).toInt(), UiKit.dp(context, 240f)).coerceAtMost(screenW)
            else max((screenW * widthPercent / 100f).toInt(), minWidth).coerceAtMost(screenW)
        val height = (screenH * 0.55f).toInt().coerceAtLeast(UiKit.dp(context, 250f)).coerceAtMost(screenH)
        val left = if (landscape) screenW - width - UiKit.dp(context, 14f) else (screenW - width) / 2
        val top = ((screenH - height) * 0.22f).toInt().coerceAtLeast(UiKit.dp(context, 24f))
        val loading = showLaunchLoading(app.label)
        Thread {
            val result = runCatching {
                controller.launchApp(app.component.flattenToString(), left, top, left + width, top + height)
            }.getOrElse { "ERROR|${it.message}" }
            handler.post {
                dismissLaunchLoading(loading)
                if (result.startsWith("OK|")) {
                    val parts = result.split('|')
                    if (parts.size >= 6) {
                        val taskId = parts[1].toIntOrNull()
                        val l = parts[2].toIntOrNull() ?: left
                        val t = parts[3].toIntOrNull() ?: top
                        val r = parts[4].toIntOrNull() ?: (left + width)
                        val b = parts[5].toIntOrNull() ?: (top + height)
                        if (taskId != null) chrome.addWindow(taskId, app.label, l, t, r, b)
                    }
                } else {
                    Toast.makeText(context, "Freeform not available: ${result.substringAfter('|')}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun showLaunchLoading(appName: String): FrameLayout {
        val root = FrameLayout(context).apply { setBackgroundColor(Color.argb(112, 0, 0, 0)) }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(UiKit.dp(context, 22f), UiKit.dp(context, 20f), UiKit.dp(context, 22f), UiKit.dp(context, 19f))
            background = UiKit.glass(context, 22f, Color.argb(236, 20, 24, 34))
            elevation = UiKit.dp(context, 18f).toFloat()
        }
        val logo = ImageView(context).apply { setImageResource(app.smartnet.R.drawable.smartnet_logo) }
        card.addView(logo, LinearLayout.LayoutParams(UiKit.dp(context, 58f), UiKit.dp(context, 58f)))
        card.addView(UiKit.text(context, "CONNECTING", 10f, UiKit.cyan, true).apply {
            letterSpacing = 0.16f
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(context, 12f), 0, UiKit.dp(context, 5f))
        })
        card.addView(UiKit.text(context, appName, 13f, UiKit.text, true).apply { gravity = Gravity.CENTER })
        root.addView(card, FrameLayout.LayoutParams(UiKit.dp(context, 220f), -2, Gravity.CENTER))
        val params = WindowManager.LayoutParams(
            -1, -1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT }
        runCatching { windowManager.addView(root, params) }
        if (SmartNetPrefs.getBoolean(context, "animations", true)) {
            card.alpha = 0f
            card.scaleX = 0.88f
            card.scaleY = 0.88f
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(android.view.animation.OvershootInterpolator(0.8f)).start()
            android.animation.ObjectAnimator.ofFloat(logo, "rotation", 0f, 360f).apply {
                duration = 850
                repeatCount = android.animation.ValueAnimator.INFINITE
                start()
                root.tag = this
            }
        }
        return root
    }

    private fun dismissLaunchLoading(view: FrameLayout) {
        (view.tag as? android.animation.Animator)?.cancel()
        val card = view.getChildAt(0)
        if (SmartNetPrefs.getBoolean(context, "animations", true)) {
            card?.animate()?.alpha(0f)?.scaleX(0.92f)?.scaleY(0.92f)?.setDuration(160)?.withEndAction {
                runCatching { windowManager.removeView(view) }
            }?.start()
        } else runCatching { windowManager.removeView(view) }
    }

    private fun closeSidebar(animated: Boolean = true) {
        val current = sidebar ?: return
        val panel = sidebarPanel
        if (!animated || !SmartNetPrefs.getBoolean(context, "animations", true) || panel == null) {
            runCatching { windowManager.removeView(current) }
            sidebar = null
            sidebarPanel = null
            return
        }
        val side = SmartNetPrefs.getString(context, "handle_side", "right")
        val slideTo = if (side == "left") -panel.width.toFloat() else panel.width.toFloat()
        panel.animate().translationX(slideTo).alpha(0.4f).setDuration(220)
            .withEndAction {
                runCatching { windowManager.removeView(current) }
                if (sidebar === current) sidebar = null
                sidebarPanel = null
            }.start()
    }

    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = context.resources.displayMetrics
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    fun destroy() {
        destroyed = true
        handle?.let { view ->
            (view.tag as? android.animation.Animator)?.cancel()
            runCatching { windowManager.removeView(view) }
        }
        handle = null
        handleParams = null
        sidebar?.let { runCatching { windowManager.removeView(it) } }
        sidebar = null
        sidebarPanel = null
        chrome.destroy()
    }
}

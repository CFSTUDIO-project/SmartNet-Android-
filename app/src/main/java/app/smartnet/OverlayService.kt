package app.smartnet

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var handle: View? = null
    private var sidebar: View? = null
    private lateinit var handleParams: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showHandle()
    }

    private fun showHandle() {
        if (handle != null) return
        val pill = TextView(this).apply {
            text = "│"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(0xEE222A38.toInt())
            setOnClickListener { toggleSidebar() }
            setOnTouchListener(DragTouch())
        }
        val type = if (android.os.Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        handleParams = WindowManager.LayoutParams(dp(34), dp(76), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            x = 0; y = 0
        }
        handle = pill
        wm.addView(pill, handleParams)
    }

    private fun toggleSidebar() {
        if (sidebar != null) { removeSidebar(); return }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(20))
            setBackgroundColor(0xF01A202B.toInt())
            addView(TextView(this@OverlayService).apply {
                text = "SMART .NET"; textSize = 18f; setTextColor(0xFF65D2ED.toInt())
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(TextView(this@OverlayService).apply {
                text = "App launcher integration is coming in the next phase."
                textSize = 13f; setTextColor(Color.WHITE); setPadding(0, dp(14), 0, 0)
            })
            addView(TextView(this@OverlayService).apply {
                text = "×"; textSize = 24f; gravity = Gravity.END; setTextColor(Color.WHITE)
                setOnClickListener { removeSidebar() }
            })
        }
        val type = if (android.os.Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams(dp(250), dp(240), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            x = dp(42)
        }
        sidebar = panel
        wm.addView(panel, p)
    }

    private fun removeSidebar() {
        sidebar?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        sidebar = null
    }

    private inner class DragTouch : View.OnTouchListener {
        private var sx = 0f; private var sy = 0f; private var ox = 0; private var oy = 0; private var moved = false
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=handleParams.x; oy=handleParams.y; moved=false; return true }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(e.rawY-sy) > dp(5) || kotlin.math.abs(e.rawX-sx) > dp(5)) moved=true
                    handleParams.x = (ox - (e.rawX-sx).toInt()).coerceAtLeast(0)
                    handleParams.y = oy + (e.rawY-sy).toInt()
                    try { wm.updateViewLayout(handle, handleParams) } catch (_: Exception) {}
                    return true
                }
                MotionEvent.ACTION_UP -> { if (!moved) v.performClick(); return true }
            }
            return false
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onDestroy() { removeSidebar(); handle?.let { try { wm.removeView(it) } catch (_: Exception) {} }; handle=null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}

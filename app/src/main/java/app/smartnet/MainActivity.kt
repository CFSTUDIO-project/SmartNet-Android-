package app.smartnet

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = 0xFF0B0D12.toInt()
    private val panel = 0xFF171B24.toInt()
    private val accent = 0xFF55C8E8.toInt()
    private lateinit var root: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showWelcome()
    }

    private fun base(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(24), dp(28), dp(24), dp(24))
        setBackgroundColor(bg)
    }

    private fun showWelcome() {
        root = base()
        val logo = TextView(this).apply {
            text = "S"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(accent)
        }
        root.addView(logo, LinearLayout.LayoutParams(-1, dp(90)))
        root.addView(label("SMART .NET", 25f, 0xFFFFFFFF.toInt(), true))
        root.addView(label("Floating workspace", 14f, 0xFF9AA5B5.toInt()))
        root.addView(space(34))
        root.addView(card("PERMISSIONS", "Smart .Net needs display-over-other-apps permission to show its edge handle."))
        root.addView(space(16))
        status = label(permissionText(), 13f, 0xFFFFC56B.toInt())
        root.addView(status)
        root.addView(space(14))
        root.addView(action("Grant overlay permission") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(space(10))
        root.addView(action("Continue to workspace") {
            if (Settings.canDrawOverlays(this)) {
                startActivity(Intent(this, OverlayService::class.java))
                showHome()
            } else {
                status.text = "Permission is still required before the edge handle can appear."
            }
        })
        setContentView(root)
    }

    private fun showHome() {
        root = base()
        root.addView(label("Smart .Net", 26f, -1, true))
        root.addView(label("Your floating workspace", 14f, 0xFF9AA5B5.toInt()))
        root.addView(space(24))
        root.addView(card("EDGE HANDLE", "A slim handle is now available at the right edge of your screen. Tap it to open the app launcher."))
        root.addView(space(14))
        root.addView(card("PHASE 1 · FOUNDATION", "This build establishes the permission flow and floating launcher. Native multi-window support is a separate device-dependent integration and is not claimed as ready yet."))
        root.addView(space(18))
        root.addView(action("Show edge handle") { startService(Intent(this, OverlayService::class.java)) })
        root.addView(space(10))
        root.addView(action("Stop floating service") { stopService(Intent(this, OverlayService::class.java)) })
        root.addView(space(10))
        root.addView(action("About Smart .Net") {
            root.addView(card("ABOUT", "Smart .Net is an experimental Android floating-workspace project. Features will be added in tested stages."))
        })
        setContentView(root)
    }

    private fun permissionText() = if (Settings.canDrawOverlays(this)) "Overlay permission: granted" else "Overlay permission: not granted"

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        gravity = Gravity.CENTER
    }

    private fun card(title: String, body: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
        setBackgroundColor(panel)
        addView(TextView(this@MainActivity).apply { text = title; textSize = 12f; setTextColor(accent); setTypeface(typeface, 1) })
        addView(TextView(this@MainActivity).apply { text = body; textSize = 14f; setTextColor(0xFFE1E6EF.toInt()); setPadding(0, dp(8), 0, 0) })
    }

    private fun action(text: String, click: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; setTextColor(-1); setBackgroundColor(0xFF263746.toInt())
        setOnClickListener { click() }
    }

    private fun space(h: Int) = View(this).apply { layoutParams = LinearParams(-1, dp(h)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private class LinearParams(w: Int, h: Int) : LinearLayout.LayoutParams(w, h)
}

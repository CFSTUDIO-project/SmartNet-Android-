package app.smartnet

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import app.smartnet.bridge.ShizukuBridge
import app.smartnet.overlay.OverlayService
import app.smartnet.ui.UiKit

class SettingsActivity : Activity() {
    private lateinit var page: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiKit.background
        window.navigationBarColor = UiKit.background
        val scroll = android.widget.ScrollView(this)
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@SettingsActivity, 20f), UiKit.dp(this@SettingsActivity, 20f), UiKit.dp(this@SettingsActivity, 20f), UiKit.dp(this@SettingsActivity, 28f))
            setBackgroundColor(UiKit.background)
        }
        scroll.addView(page)
        setContentView(scroll)
        build()
    }

    private fun build() {
        page.removeAllViews()
        page.addView(UiKit.text(this, "Settings", 26f, UiKit.text, true))
        page.addView(UiKit.text(this, "Configure the parts of Smart .Net that are active in this build.", 12f, UiKit.muted).apply {
            setPadding(0, UiKit.dp(this@SettingsActivity, 6f), 0, UiKit.dp(this@SettingsActivity, 18f))
        })
        val appearance = UiKit.card(this)
        appearance.addView(sectionTitle("Appearance & motion"))
        addSwitch(appearance, "Smooth animations", "Sidebar and handle transitions", "animations", true)
        addSwitch(appearance, "Backdrop blur", "Uses Android's blur-behind effect where supported", "blur", true)
        addSwitch(appearance, "Haptic feedback", "Small tactile confirmation on controls", "haptics", true)
        addSeek(appearance, "Window width", "Default freeform width: %d%%", "window_width_pct", 75, 60, 95)
        addSeek(appearance, "Glass opacity", "Panel surface opacity: %d%%", "panel_opacity", 88, 65, 100)
        page.addView(appearance, bottomMargin())

        val behavior = UiKit.card(this)
        behavior.addView(sectionTitle("Workspace behavior"))
        behavior.addView(UiKit.text(this, "Handle position", 13f, UiKit.text, true))
        val choices = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            setPadding(0, UiKit.dp(this@SettingsActivity, 9f), 0, UiKit.dp(this@SettingsActivity, 12f))
        }
        val right = android.widget.RadioButton(this).apply { text = "Right"; buttonTintList = android.content.res.ColorStateList.valueOf(UiKit.cyan); id = 1 }
        val left = android.widget.RadioButton(this).apply { text = "Left"; buttonTintList = android.content.res.ColorStateList.valueOf(UiKit.cyan); id = 2 }
        choices.addView(right); choices.addView(left)
        choices.check(if (SmartNetPrefs.getString(this, "handle_side", "right") == "left") 2 else 1)
        choices.setOnCheckedChangeListener { _, checked ->
            SmartNetPrefs.putString(this, "handle_side", if (checked == 2) "left" else "right")
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_REFRESH))
        }
        behavior.addView(choices)
        behavior.addView(UiKit.text(this, "Sidebar columns are automatic: 3 in portrait and 5 in landscape.", 11f, UiKit.muted))
        page.addView(behavior, bottomMargin())

        val compatibility = UiKit.card(this)
        compatibility.addView(sectionTitle("Android compatibility"))
        compatibility.addView(UiKit.text(this, "Freeform support is controlled by Android and the device manufacturer. Smart .Net can request the standard global settings through Shizuku, but an OEM may ignore or block them.", 12f, UiKit.muted))
        compatibility.addView(UiKit.button(this, "Enable / check freeform settings", true).apply {
            setOnClickListener { enableFreeform() }
        }, buttonMargin())
        compatibility.addView(UiKit.button(this, "Restore previous system values").apply {
            setOnClickListener { restoreFreeform() }
        }, buttonMargin())
        compatibility.addView(UiKit.button(this, "Open battery and background settings").apply {
            setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    .onFailure { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            }
        }, buttonMargin())
        compatibility.addView(UiKit.button(this, "Show live task diagnostics").apply {
            setOnClickListener { showDiagnostics() }
        }, buttonMargin())
        page.addView(compatibility, bottomMargin())

        page.addView(UiKit.button(this, "Stop Smart .Net overlay").apply {
            setOnClickListener {
                stopService(Intent(this@SettingsActivity, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
                Toast.makeText(this@SettingsActivity, "Overlay stopped", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun enableFreeform() {
        val controller = ShizukuBridge.controller
        if (controller == null) {
            Toast.makeText(this, "Connect Shizuku first.", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Enable Android freeform?")
            .setMessage("This changes enable_freeform_support and force_resizable_activities in Android global settings. Current values are saved in Smart .Net and can be restored. Some manufacturers may ignore these switches.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Enable") { _, _ ->
                Thread {
                    val result = runCatching { controller.enableFreeform() }.getOrElse { "ERROR|${it.message}" }
                    runOnUiThread {
                        if (result.startsWith("OK|") || result.startsWith("PARTIAL|")) {
                            if (SmartNetPrefs.getString(this, "freeform_snapshot").isBlank()) {
                                SmartNetPrefs.putString(this, "freeform_snapshot", result.substringAfter('|'))
                            }
                            Toast.makeText(this, "Freeform compatibility request completed. Reboot may be needed.", Toast.LENGTH_LONG).show()
                        } else Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                    }
                }.start()
            }.show()
    }

    private fun restoreFreeform() {
        val snapshot = SmartNetPrefs.getString(this, "freeform_snapshot")
        val controller = ShizukuBridge.controller
        if (snapshot.isBlank() || controller == null) {
            Toast.makeText(this, "No saved settings snapshot or Shizuku connection.", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Restore previous values?")
            .setMessage("Smart .Net will restore the two Android global settings to the values recorded before its first freeform setup.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore") { _, _ ->
                Thread {
                    val ok = runCatching { controller.restoreFreeform(snapshot) }.getOrDefault(false)
                    runOnUiThread {
                        if (ok) {
                            SmartNetPrefs.putString(this, "freeform_snapshot", "")
                            Toast.makeText(this, "Previous values restored.", Toast.LENGTH_LONG).show()
                        } else Toast.makeText(this, "Could not restore all values.", Toast.LENGTH_LONG).show()
                    }
                }.start()
            }.show()
    }

    private fun showDiagnostics() {
        val controller = ShizukuBridge.controller
        if (controller == null) {
            Toast.makeText(this, "Shizuku is not connected.", Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val data = runCatching { controller.dumpVisibleTasks() }.getOrElse { "ERROR|${it.message}" }
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Visible Android tasks")
                    .setMessage(data.ifBlank { "No task information returned." })
                    .setPositiveButton("Close", null)
                    .show()
            }
        }.start()
    }

    private fun addSwitch(parent: LinearLayout, title: String, subtitle: String, key: String, default: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(UiKit.text(this, title, 13f, UiKit.text, true))
        textColumn.addView(UiKit.text(this, subtitle, 10f, UiKit.muted).apply { setPadding(0, UiKit.dp(this@SettingsActivity, 4f), 0, 0) })
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        val switch = Switch(this).apply {
            isChecked = SmartNetPrefs.getBoolean(this@SettingsActivity, key, default)
            setOnCheckedChangeListener { _, checked -> SmartNetPrefs.putBoolean(this@SettingsActivity, key, checked) }
        }
        row.addView(switch)
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = UiKit.dp(this@SettingsActivity, 10f)
            bottomMargin = UiKit.dp(this@SettingsActivity, 10f)
        })
    }

    private fun addSeek(parent: LinearLayout, title: String, subtitle: String, key: String, default: Int, min: Int, max: Int) {
        val current = SmartNetPrefs.getInt(this, key, default)
        val titleView = UiKit.text(this, title, 13f, UiKit.text, true)
        val valueView = UiKit.text(this, subtitle.format(current), 10f, UiKit.muted)
        val seek = SeekBar(this).apply {
            this.max = max - min
            progress = current - min
            progressTintList = android.content.res.ColorStateList.valueOf(UiKit.cyan)
            thumbTintList = android.content.res.ColorStateList.valueOf(UiKit.cyan)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val selected = min + progress
                    valueView.text = subtitle.format(selected)
                    if (fromUser) SmartNetPrefs.putInt(this@SettingsActivity, key, selected)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        parent.addView(titleView, topMargin())
        parent.addView(valueView, topMargin(4f))
        parent.addView(seek, topMargin(2f))
    }

    private fun sectionTitle(value: String) = UiKit.text(this, value.uppercase(), 10f, UiKit.cyan, true).apply { letterSpacing = 0.11f; setPadding(0, 0, 0, UiKit.dp(this@SettingsActivity, 14f)) }
    private fun topMargin(dp: Float = 10f) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiKit.dp(this@SettingsActivity, dp) }
    private fun bottomMargin() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiKit.dp(this@SettingsActivity, 14f) }
    private fun buttonMargin() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiKit.dp(this@SettingsActivity, 10f) }
}

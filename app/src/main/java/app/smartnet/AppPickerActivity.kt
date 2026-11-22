package app.smartnet

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.smartnet.ui.UiKit

class AppPickerActivity : Activity() {
    private val checks = linkedMapOf<String, CheckBox>()
    private val labels = linkedMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiKit.background
        window.navigationBarColor = UiKit.background

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@AppPickerActivity, 18f), UiKit.dp(this@AppPickerActivity, 20f), UiKit.dp(this@AppPickerActivity, 18f), UiKit.dp(this@AppPickerActivity, 18f))
            setBackgroundColor(UiKit.background)
        }
        page.addView(UiKit.text(this, "Add applications", 24f, UiKit.text, true))
        page.addView(UiKit.text(this, "Choose launchable apps to pin in the Smart .Net sidebar.", 12f, UiKit.muted).apply {
            setPadding(0, UiKit.dp(this@AppPickerActivity, 7f), 0, UiKit.dp(this@AppPickerActivity, 16f))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities: List<ResolveInfo> = packageManager.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val current = SmartNetPrefs.getFavorites(this).map { it.substringBefore('/') }.toSet()

        activities.forEach { info ->
            val component = info.activityInfo.let { "${it.packageName}/${it.name}" }
            val packageName = info.activityInfo.packageName
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = UiKit.glass(this@AppPickerActivity, 15f)
                setPadding(UiKit.dp(this@AppPickerActivity, 12f), UiKit.dp(this@AppPickerActivity, 9f), UiKit.dp(this@AppPickerActivity, 10f), UiKit.dp(this@AppPickerActivity, 9f))
            }
            val icon = ImageView(this).apply { setImageDrawable(info.loadIcon(packageManager)) }
            row.addView(icon, LinearLayout.LayoutParams(UiKit.dp(this, 38f), UiKit.dp(this, 38f)))
            val label = UiKit.text(this, info.loadLabel(packageManager).toString(), 13f, UiKit.text, true).apply {
                setPadding(UiKit.dp(this@AppPickerActivity, 11f), 0, 0, 0)
            }
            row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            val check = CheckBox(this).apply {
                buttonTintList = android.content.res.ColorStateList.valueOf(UiKit.cyan)
                isChecked = packageName in current
            }
            row.addView(check)
            checks[component] = check
            labels[component] = packageName
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiKit.dp(this@AppPickerActivity, 7f) })
            row.setOnClickListener { check.isChecked = !check.isChecked }
        }

        val save = UiKit.button(this, "Save app selection", true).apply {
            setOnClickListener {
                val selected = checks.filterValues { it.isChecked }.keys
                SmartNetPrefs.saveFavorites(this@AppPickerActivity, selected)
                Toast.makeText(this@AppPickerActivity, "${selected.size} shortcuts saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        page.addView(save, LinearLayout.LayoutParams(-1, UiKit.dp(this, 54f)).apply { topMargin = UiKit.dp(this@AppPickerActivity, 14f) })
        setContentView(page)
    }
}

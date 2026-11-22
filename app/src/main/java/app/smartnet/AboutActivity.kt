package app.smartnet

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import app.smartnet.ui.UiKit

class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiKit.background
        window.navigationBarColor = UiKit.background
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(UiKit.dp(this@AboutActivity, 24f), UiKit.dp(this@AboutActivity, 32f), UiKit.dp(this@AboutActivity, 24f), UiKit.dp(this@AboutActivity, 32f))
            setBackgroundColor(UiKit.background)
        }
        val logo = ImageView(this).apply { setImageResource(R.drawable.smartnet_logo) }
        content.addView(logo, LinearLayout.LayoutParams(UiKit.dp(this, 110f), UiKit.dp(this, 110f)))
        content.addView(UiKit.text(this, "Smart .Net", 25f, UiKit.text, true).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, UiKit.dp(this@AboutActivity, 16f), 0, 0)
        })
        content.addView(UiKit.text(this, "Version 0.1.0-alpha01", 12f, UiKit.cyan, true).apply {
            gravity = android.view.Gravity.CENTER
            setPadding(0, UiKit.dp(this@AboutActivity, 6f), 0, UiKit.dp(this@AboutActivity, 22f))
        })
        val card = UiKit.card(this)
        card.addView(UiKit.text(this, "ABOUT THE PROJECT", 10f, UiKit.cyan, true))
        card.addView(UiKit.text(this, "Smart .Net is an Android multitasking companion. It provides an edge trigger, a glass application sidebar, and controls around Android freeform tasks.", 13f, UiKit.text).apply {
            setPadding(0, UiKit.dp(this@AboutActivity, 10f), 0, UiKit.dp(this@AboutActivity, 14f))
        })
        card.addView(UiKit.text(this, "This alpha uses Shizuku and Android task-manager APIs for real external app windows. Availability depends on the OS build and device manufacturer. An app is never represented by a screenshot or a simulated window.", 12f, UiKit.muted))
        content.addView(card, LinearLayout.LayoutParams(-1, -2))
        val credits = UiKit.card(this).apply { setPadding(UiKit.dp(this@AboutActivity, 16f), UiKit.dp(this@AboutActivity, 16f), UiKit.dp(this@AboutActivity, 16f), UiKit.dp(this@AboutActivity, 16f)) }
        credits.addView(UiKit.text(this, "TECHNICAL NOTES", 10f, UiKit.cyan, true))
        credits.addView(UiKit.text(this, "• Shizuku provides a user-authorized shell service.\n• Android freeform mode is requested for each external app.\n• Smart .Net stores sidebar shortcuts and appearance settings locally.\n• The project is not affiliated with Infinix, Xiaomi, or Shizuku.", 12f, UiKit.muted).apply {
            setPadding(0, UiKit.dp(this@AboutActivity, 10f), 0, 0)
        })
        content.addView(credits, LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiKit.dp(this@AboutActivity, 14f) })
        setContentView(ScrollView(this).apply { addView(content) })
    }
}

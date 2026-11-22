package app.smartnet.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import app.smartnet.R

object UiKit {
    val background = Color.rgb(9, 12, 19)
    val surface = Color.rgb(20, 23, 31)
    val surfaceRaised = Color.rgb(27, 31, 41)
    val text = Color.rgb(242, 246, 252)
    val muted = Color.rgb(147, 158, 177)
    val cyan = Color.rgb(30, 205, 220)
    val blue = Color.rgb(35, 139, 244)
    val purple = Color.rgb(124, 91, 220)
    val green = Color.rgb(77, 209, 151)
    val yellow = Color.rgb(246, 194, 91)
    val red = Color.rgb(238, 91, 112)

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun glass(context: Context, radius: Float = 22f, fill: Int = surface): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, radius).toFloat()
            setColor(fill)
            setStroke(dp(context, 1f), Color.argb(38, 255, 255, 255))
        }

    fun text(
        context: Context,
        value: String,
        size: Float = 14f,
        color: Int = text,
        bold: Boolean = false
    ): TextView = TextView(context).apply {
        this.text = value
        this.textSize = size
        setTextColor(color)
        typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        includeFontPadding = false
    }

    fun button(context: Context, value: String, primary: Boolean = false): TextView =
        TextView(context).apply {
            this.text = value
            this.textSize = 14f
            setTextColor(if (primary) Color.rgb(5, 18, 28) else UiKit.text)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(context, 18f), dp(context, 13f), dp(context, 18f), dp(context, 13f))
            this.background = if (primary) {
                GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cyan, blue)).apply {
                    cornerRadius = dp(context, 16f).toFloat()
                }
            } else glass(context, 16f, surfaceRaised)
            isClickable = true
            isFocusable = true
            stateListAnimator = null
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.985f).scaleY(0.985f).setDuration(85).start()
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(125).start()
                }
                false
            }
        }

    fun card(context: Context, padding: Int = 16): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding.toFloat()), dp(context, padding.toFloat()), dp(context, padding.toFloat()), dp(context, padding.toFloat()))
        this.background = glass(context, 20f)
        elevation = dp(context, 8f).toFloat()
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(Color.argb(22, 255, 255, 255))
        layoutParams = LinearLayout.LayoutParams(-1, dp(context, 1f)).apply {
            topMargin = dp(context, 12f)
            bottomMargin = dp(context, 12f)
        }
    }

    fun setEnabledStyle(view: TextView, enabled: Boolean) {
        view.alpha = if (enabled) 1f else 0.42f
        view.isEnabled = enabled
    }
}

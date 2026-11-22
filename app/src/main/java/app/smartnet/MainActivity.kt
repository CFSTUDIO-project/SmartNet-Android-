package app.smartnet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.smartnet.bridge.ShizukuBridge
import app.smartnet.overlay.OverlayService
import app.smartnet.ui.UiKit
import rikka.shizuku.Shizuku

class MainActivity : Activity(), Shizuku.OnRequestPermissionResultListener, Shizuku.OnBinderReceivedListener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private var screen = "splash"
    private var snapshotTimer: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UiKit.background
        window.navigationBarColor = UiKit.background
        window.decorView.systemUiVisibility = 0
        Shizuku.addRequestPermissionResultListener(this)
        Shizuku.addBinderReceivedListenerSticky(this)
        root = FrameLayout(this)
        setContentView(root)
        showSplash()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized && screen == "setup") renderSetup()
    }

    private fun showSplash() {
        screen = "splash"
        root.removeAllViews()
        root.setBackgroundColor(UiKit.background)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val halo = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                Color.argb(45, 0, 190, 255), Color.argb(18, 105, 78, 211), Color.TRANSPARENT
            )).apply { shape = GradientDrawable.OVAL }
        }
        val haloParams = FrameLayout.LayoutParams(UiKit.dp(this, 250f), UiKit.dp(this, 250f), Gravity.CENTER)
        root.addView(halo, haloParams)
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.smartnet_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        content.addView(logo, LinearLayout.LayoutParams(UiKit.dp(this, 112f), UiKit.dp(this, 112f)))
        val title = UiKit.text(this, "SMART .NET", 25f, UiKit.text, true).apply {
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(this@MainActivity, 22f), 0, 0)
        }
        content.addView(title)
        content.addView(UiKit.text(this, "MULTITASKING LAYER", 10f, UiKit.muted, true).apply {
            letterSpacing = 0.22f
            gravity = Gravity.CENTER
            setPadding(0, UiKit.dp(this@MainActivity, 10f), 0, 0)
        })
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        logo.alpha = 0f
        logo.scaleX = 0.72f
        logo.scaleY = 0.72f
        content.alpha = 0f
        content.translationY = UiKit.dp(this, 14f).toFloat()
        content.animate().alpha(1f).translationY(0f).setDuration(620).setInterpolator(OvershootInterpolator(0.7f)).start()
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(800).setInterpolator(OvershootInterpolator(1.1f)).start()
        logo.animate().rotationBy(360f).setDuration(1500).start()
        halo.animate().alpha(0.65f).scaleX(1.22f).scaleY(1.22f).setDuration(1100).withEndAction {
            halo.animate().alpha(0.25f).scaleX(0.94f).scaleY(0.94f).setDuration(950).start()
        }.start()

        handler.postDelayed({
            val ready = SmartNetPrefs.getBoolean(this, "onboarding_done", false) &&
                Settings.canDrawOverlays(this) && ShizukuBridge.controller != null
            if (ready) renderHome() else renderSetup()
        }, 1850L)
    }

    private fun basePage(titleText: String, subtitle: String): LinearLayout {
        root.removeAllViews()
        root.setBackgroundColor(UiKit.background)
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@MainActivity, 22f), UiKit.dp(this@MainActivity, 24f), UiKit.dp(this@MainActivity, 22f), UiKit.dp(this@MainActivity, 34f))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.smartnet_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        page.addView(logo, LinearLayout.LayoutParams(UiKit.dp(this, 42f), UiKit.dp(this, 42f)))
        page.addView(UiKit.text(this, titleText, 26f, UiKit.text, true).apply {
            setPadding(0, UiKit.dp(this@MainActivity, 16f), 0, 0)
        })
        page.addView(UiKit.text(this, subtitle, 13f, UiKit.muted).apply {
            setPadding(0, UiKit.dp(this@MainActivity, 7f), 0, UiKit.dp(this@MainActivity, 22f))
        })
        scroll.addView(page)
        root.addView(scroll)
        return page
    }

    private fun renderSetup() {
        screen = "setup"
        val page = basePage("Authorize Smart .Net", "Hubungkan izin yang benar-benar dibutuhkan agar jendela aplikasi dapat berjalan.")
        val systemCard = UiKit.card(this)
        systemCard.addView(UiKit.text(this, "SYSTEM CONNECTION", 10f, UiKit.cyan, true))
        systemCard.addView(UiKit.text(this, "Shizuku", 16f, UiKit.text, true).apply { setPadding(0, UiKit.dp(this@MainActivity, 9f), 0, UiKit.dp(this@MainActivity, 4f)) })
        systemCard.addView(UiKit.text(this, "Diperlukan untuk meminta Android mengelola task dan freeform window. Shizuku harus dijalankan terlebih dahulu.", 12f, UiKit.muted))
        val shizukuStatus = UiKit.text(this, shizukuStatusText(), 12f, if (ShizukuBridge.controller != null) UiKit.green else UiKit.yellow, true).apply {
            setPadding(0, UiKit.dp(this@MainActivity, 12f), 0, UiKit.dp(this@MainActivity, 10f))
        }
        systemCard.addView(shizukuStatus)
        val shizukuButton = UiKit.button(this, if (!ShizukuBridge.isBinderRunning()) "Buka panduan Shizuku" else if (!ShizukuBridge.hasPermission()) "Izinkan Shizuku" else "Hubungkan service", true)
        shizukuButton.setOnClickListener {
            when {
                !ShizukuBridge.isBinderRunning() -> openShizukuGuide()
                !ShizukuBridge.hasPermission() -> ShizukuBridge.requestPermission()
                else -> {
                    ShizukuBridge.bindIfAuthorized()
                    Toast.makeText(this, "Menghubungkan service sistem…", Toast.LENGTH_SHORT).show()
                    renderSetup()
                    scheduleSetupRefresh()
                }
            }
        }
        systemCard.addView(shizukuButton)
        page.addView(systemCard, marginBottom())

        val overlayCard = UiKit.card(this)
        overlayCard.addView(UiKit.text(this, "DISPLAY PERMISSION", 10f, UiKit.cyan, true))
        overlayCard.addView(UiKit.text(this, "Tampil di atas aplikasi", 16f, UiKit.text, true).apply { setPadding(0, UiKit.dp(this@MainActivity, 9f), 0, UiKit.dp(this@MainActivity, 4f)) })
        overlayCard.addView(UiKit.text(this, "Dibutuhkan untuk handle |, sidebar, dan kontrol jendela Smart .Net.", 12f, UiKit.muted))
        val overlayStatus = UiKit.text(this, if (Settings.canDrawOverlays(this)) "AUTHORIZED · Ready" else "REQUIRED · Belum diizinkan", 12f, if (Settings.canDrawOverlays(this)) UiKit.green else UiKit.yellow, true).apply {
            setPadding(0, UiKit.dp(this@MainActivity, 12f), 0, UiKit.dp(this@MainActivity, 10f))
        }
        overlayCard.addView(overlayStatus)
        overlayCard.addView(UiKit.button(this, if (Settings.canDrawOverlays(this)) "Izin sudah diberikan" else "Izinkan overlay", true).apply {
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })
        page.addView(overlayCard, marginBottom())

        val optionalCard = UiKit.card(this)
        optionalCard.addView(UiKit.text(this, "BACKGROUND & NOTIFICATIONS", 10f, UiKit.cyan, true))
        optionalCard.addView(UiKit.text(this, "Notifikasi layanan", 16f, UiKit.text, true).apply { setPadding(0, UiKit.dp(this@MainActivity, 9f), 0, UiKit.dp(this@MainActivity, 4f)) })
        optionalCard.addView(UiKit.text(this, "Digunakan untuk indikator bahwa Smart .Net sedang aktif. Penolakan notifikasi tidak menghalangi fungsi jendela.", 12f, UiKit.muted))
        val notifGranted = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        optionalCard.addView(UiKit.text(this, if (notifGranted) "AUTHORIZED · Optional" else "OPTIONAL · Belum diberikan", 12f, if (notifGranted) UiKit.green else UiKit.yellow, true).apply {
            setPadding(0, UiKit.dp(this@MainActivity, 12f), 0, UiKit.dp(this@MainActivity, 10f))
        })
        optionalCard.addView(UiKit.button(this, if (notifGranted) "Notifikasi siap" else "Izinkan notifikasi").apply {
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4108)
            }
        })
        optionalCard.addView(UiKit.divider(this))
        optionalCard.addView(UiKit.text(this, "Optimasi baterai", 16f, UiKit.text, true))
        optionalCard.addView(UiKit.text(this, "Android tidak memiliki satu izin universal bernama izin latar belakang. Pada XOS, autostart dan pembatasan baterai mungkin perlu diatur manual.", 12f, UiKit.muted).apply {
            setPadding(0, UiKit.dp(this@MainActivity, 5f), 0, UiKit.dp(this@MainActivity, 11f))
        })
        optionalCard.addView(UiKit.button(this, "Buka pengaturan baterai").apply {
            setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    .onFailure { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            }
        })
        page.addView(optionalCard, marginBottom())

        page.addView(UiKit.text(this, "Smart .Net hanya meminta akses yang dipakai untuk fungsi utamanya. Pengaturan freeform sistem akan ditawarkan terpisah dan dapat dipulihkan.", 11f, UiKit.muted).apply {
            setPadding(UiKit.dp(this@MainActivity, 3f), UiKit.dp(this@MainActivity, 2f), UiKit.dp(this@MainActivity, 3f), UiKit.dp(this@MainActivity, 16f))
        })
        val next = UiKit.button(this, "Next  >>", true)
        val authorized = Settings.canDrawOverlays(this) && ShizukuBridge.controller != null && runCatching { ShizukuBridge.controller?.ping() == true }.getOrDefault(false)
        UiKit.setEnabledStyle(next, authorized)
        next.setOnClickListener {
            if (Settings.canDrawOverlays(this) && ShizukuBridge.controller != null) {
                SmartNetPrefs.putBoolean(this, "onboarding_done", true)
                renderHome()
            } else {
                Toast.makeText(this, "Selesaikan izin overlay dan koneksi Shizuku terlebih dahulu.", Toast.LENGTH_LONG).show()
            }
        }
        page.addView(next, LinearLayout.LayoutParams(-1, UiKit.dp(this, 54f)))
        scheduleSetupRefresh()
    }

    private fun shizukuStatusText(): String = when {
        !ShizukuBridge.isBinderRunning() -> "NOT RUNNING · Mulai Shizuku dari aplikasinya"
        !ShizukuBridge.hasPermission() -> "WAITING · Berikan izin saat diminta Shizuku"
        ShizukuBridge.controller == null -> "CONNECTING · Menghubungkan UserService"
        else -> "CONNECTED · System service ready"
    }

    private fun scheduleSetupRefresh() {
        snapshotTimer?.let { handler.removeCallbacks(it) }
        val started = System.currentTimeMillis()
        val r = object : Runnable {
            override fun run() {
                if (screen != "setup") return
                if (ShizukuBridge.isBinderRunning() && ShizukuBridge.hasPermission() && ShizukuBridge.controller == null) {
                    ShizukuBridge.bindIfAuthorized()
                }
                renderSetupOnceIfStateChanged()
                if (System.currentTimeMillis() - started < 12_000) handler.postDelayed(this, 850)
            }
        }
        snapshotTimer = r
        handler.postDelayed(r, 500)
    }

    private var lastSetupSignature = ""
    private fun renderSetupOnceIfStateChanged() {
        val signature = "${ShizukuBridge.isBinderRunning()}-${ShizukuBridge.hasPermission()}-${ShizukuBridge.controller != null}-${Settings.canDrawOverlays(this)}"
        if (signature != lastSetupSignature) {
            lastSetupSignature = signature
            renderSetup()
        }
    }

    private fun openShizukuGuide() {
        val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (launch != null) startActivity(launch) else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
    }

    private fun renderHome() {
        snapshotTimer?.let { handler.removeCallbacks(it) }
        screen = "home"
        val page = basePage("Your screen. Your workspace.", "Smart .Net · Android multitasking companion")
        val top = UiKit.card(this, 18)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(this).apply { setImageResource(R.drawable.smartnet_logo) }
        top.addView(logo, LinearLayout.LayoutParams(UiKit.dp(this, 60f), UiKit.dp(this, 60f)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@MainActivity, 14f), 0, 0, 0)
        }
        brand.addView(UiKit.text(this, "SMART .NET", 15f, UiKit.text, true))
        brand.addView(UiKit.text(this, "SYSTEM CONNECTION READY", 9f, UiKit.green, true).apply { setPadding(0, UiKit.dp(this@MainActivity, 5f), 0, 0) })
        top.addView(brand)
        page.addView(top, marginBottom())

        val startCard = UiKit.card(this, 20)
        startCard.addView(UiKit.text(this, "FLOATING WORKSPACE", 10f, UiKit.cyan, true))
        startCard.addView(UiKit.text(this, "Keep your apps within reach.", 20f, UiKit.text, true).apply {
            setPadding(0, UiKit.dp(this@MainActivity, 10f), 0, UiKit.dp(this@MainActivity, 8f))
        })
        startCard.addView(UiKit.text(this, "Start the edge handle and open supported apps as real Android freeform tasks. Device support is checked when each app launches.", 12f, UiKit.muted))
        val start = UiKit.button(this, "START  →", true).apply {
            setOnClickListener { startWorkspace() }
            setPadding(0, UiKit.dp(this@MainActivity, 17f), 0, UiKit.dp(this@MainActivity, 17f))
        }
        startCard.addView(start, LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiKit.dp(this@MainActivity, 20f) })
        page.addView(startCard, marginBottom())

        page.addView(UiKit.text(this, "PRODUCT", 10f, UiKit.muted, true).apply {
            setPadding(UiKit.dp(this@MainActivity, 3f), UiKit.dp(this@MainActivity, 8f), 0, UiKit.dp(this@MainActivity, 10f))
        })
        val settings = UiKit.button(this, "Settings   ·   Appearance, behavior, compatibility")
        settings.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        settings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        page.addView(settings, LinearLayout.LayoutParams(-1, UiKit.dp(this, 56f)).apply { bottomMargin = UiKit.dp(this@MainActivity, 10f) })
        val about = UiKit.button(this, "About Smart .Net   ·   Version & project information")
        about.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        about.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        page.addView(about, LinearLayout.LayoutParams(-1, UiKit.dp(this, 56f)))

        page.addView(UiKit.text(this, "ALPHA BUILD  ·  REAL WINDOW MODE  ·  DEVICE COMPATIBILITY VARIES", 9f, UiKit.muted, true).apply {
            letterSpacing = 0.08f
            setPadding(0, UiKit.dp(this@MainActivity, 28f), 0, 0)
        })
    }

    private fun startWorkspace() {
        val controller = ShizukuBridge.controller
        if (controller == null || !Settings.canDrawOverlays(this)) {
            renderSetup()
            return
        }
        val stored = SmartNetPrefs.getString(this, "freeform_snapshot")
        if (stored.isBlank()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Enable freeform compatibility?")
                .setMessage("Smart .Net can enable two Android global settings used by freeform windows. This may not be supported by every XOS build. Original values are saved so you can restore them later.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Enable") { _, _ ->
                    Thread {
                        val result = runCatching { controller.enableFreeform() }.getOrElse { "ERROR|${it.message}" }
                        runOnUiThread {
                            if (result.startsWith("OK|") || result.startsWith("PARTIAL|")) {
                                SmartNetPrefs.putString(this, "freeform_snapshot", result.substringAfter('|'))
                                Toast.makeText(this, if (result.startsWith("OK|")) "Freeform settings enabled" else "Some settings could not be changed", Toast.LENGTH_LONG).show()
                                launchOverlayService()
                            } else Toast.makeText(this, result, Toast.LENGTH_LONG).show()
                        }
                    }.start()
                }.show()
        } else launchOverlayService()
    }

    private fun launchOverlayService() {
        if (!Settings.canDrawOverlays(this)) return
        val intent = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_START)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "Smart .Net is starting…", Toast.LENGTH_SHORT).show()
    }

    private fun marginBottom(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiKit.dp(this@MainActivity, 14f) }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode == ShizukuBridge.REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                ShizukuBridge.bindIfAuthorized()
                scheduleSetupRefresh()
            } else {
                Toast.makeText(this, "Shizuku permission was not granted.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBinderReceived() {
        if (ShizukuBridge.hasPermission()) ShizukuBridge.bindIfAuthorized()
        if (screen == "setup") renderSetup()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(this)
        Shizuku.removeBinderReceivedListener(this)
        snapshotTimer?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    @Deprecated("Use system back dispatcher")
    override fun onBackPressed() {
        if (screen != "home" && screen != "setup") renderHome() else super.onBackPressed()
    }
}

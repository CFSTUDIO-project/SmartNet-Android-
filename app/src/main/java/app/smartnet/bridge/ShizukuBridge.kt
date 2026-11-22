package app.smartnet.bridge

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuBridge {
    private const val TAG = "SmartNet-Shizuku"
    const val REQUEST_CODE = 4107

    @Volatile var controller: ISmartNetController? = null
        private set
    @Volatile var lastError: String = ""
        private set

    private var bound = false
    private lateinit var appContext: Context

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            controller = ISmartNetController.Stub.asInterface(service)
            lastError = ""
            Log.i(TAG, "Smart .Net system service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            controller = null
            bound = false
            lastError = "Shizuku service disconnected"
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        controller = null
        bound = false
        lastError = "Shizuku stopped"
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) bindIfAuthorized()
    }

    fun isBinderRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure { lastError = it.message ?: "Permission request failed" }
    }

    fun bindIfAuthorized(): Boolean {
        if (!isBinderRunning()) {
            lastError = "Start Shizuku first"
            return false
        }
        if (!hasPermission()) {
            lastError = "Shizuku permission has not been granted"
            return false
        }
        if (controller != null) return true
        if (bound) return true
        return runCatching {
            val args = Shizuku.UserServiceArgs(
                ComponentName(appContext, SmartNetUserService::class.java)
            ).daemon(false).processNameSuffix("system").version(1)
            Shizuku.bindUserService(args, connection)
            bound = true
            true
        }.onFailure {
            lastError = it.message ?: "Unable to bind Shizuku UserService"
            Log.e(TAG, "bindUserService failed", it)
        }.getOrDefault(false)
    }

    fun unbind() {
        if (!bound) return
        runCatching {
            val args = Shizuku.UserServiceArgs(
                ComponentName(appContext, SmartNetUserService::class.java)
            ).daemon(false).processNameSuffix("system").version(1)
            Shizuku.unbindUserService(args, connection, true)
        }
        controller = null
        bound = false
    }
}

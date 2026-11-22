package app.smartnet.bridge

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku

object ShizukuBridge {
    private const val TAG = "SmartNet-Shizuku"
    const val REQUEST_CODE = 4107
    private const val CONNECT_TIMEOUT_MS = 12000L
    private const val USER_SERVICE_TAG = "app.smartnet.system-service"

    @Volatile var controller: ISmartNetController? = null
        private set
    @Volatile var lastError: String = ""
        private set
    @Volatile private var bindRequested = false

    private val handler: Handler = Handler(Looper.getMainLooper())
    private lateinit var appContext: Context
    @Volatile private var initialized: Boolean = false

    private val serviceComponent: ComponentName by lazy {
        ComponentName(appContext.packageName, SmartNetUserService::class.java.name)
    }

    private val args: Shizuku.UserServiceArgs by lazy {
        Shizuku.UserServiceArgs(serviceComponent)
            .daemon(false)
            .processNameSuffix("system")
            .tag(USER_SERVICE_TAG)
            .version(3)
    }

    // Declared separately because the timeout and ServiceConnection refer to each other.
    private lateinit var connection: ServiceConnection
    private lateinit var connectTimeout: Runnable

    init {
        connectTimeout = Runnable {
            if (bindRequested && controller == null) {
                runCatching { Shizuku.unbindUserService(args, connection, true) }
                    .onFailure { Log.w(TAG, "Timed-out UserService cleanup failed", it) }
                clearConnectionState(
                    "UserService tidak merespons dalam 12 detik. Periksa izin SmartNet di Shizuku lalu coba lagi."
                )
            }
        }

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                handler.removeCallbacks(connectTimeout)
                if (service == null || !service.isBinderAlive) {
                    clearConnectionState("Shizuku mengembalikan Binder kosong atau sudah mati.")
                    return
                }

                val remote: ISmartNetController? = ISmartNetController.Stub.asInterface(service)
                if (remote == null) {
                    clearConnectionState("Interface SmartNet UserService tidak dapat dibaca.")
                    return
                }

                try {
                    if (!remote.ping()) {
                        clearConnectionState("UserService tersambung, tetapi pemeriksaan sistem gagal.")
                        runCatching { Shizuku.unbindUserService(args, connection, true) }
                        return
                    }
                    controller = remote
                    bindRequested = true
                    lastError = ""
                    Log.i(TAG, "UserService connected and ping verified")
                } catch (e: Exception) {
                    clearConnectionState(
                        "UserService hidup tetapi ping gagal: ${e.message ?: e.javaClass.simpleName}"
                    )
                    runCatching { Shizuku.unbindUserService(args, connection, true) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                clearConnectionState("UserService terputus. Tekan Hubungkan service lagi.")
            }

            override fun onBindingDied(name: ComponentName?) {
                clearConnectionState("Binding UserService mati. Coba hubungkan ulang.")
                runCatching { Shizuku.unbindUserService(args, connection, true) }
            }

            override fun onNullBinding(name: ComponentName?) {
                clearConnectionState("Shizuku tidak mengembalikan UserService. Periksa izin aplikasi di Shizuku.")
                runCatching { Shizuku.unbindUserService(args, connection, true) }
            }
        }
    }

    private fun clearConnectionState(message: String) {
        controller = null
        bindRequested = false
        lastError = message
        handler.removeCallbacks(connectTimeout)
        Log.e(TAG, message)
    }

    private val binderReceivedListener: Shizuku.OnBinderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            Log.i(TAG, "Shizuku binder received")
            if (hasPermission()) bindIfAuthorized()
        }

    private val binderDeadListener: Shizuku.OnBinderDeadListener =
        Shizuku.OnBinderDeadListener {
            clearConnectionState("Shizuku berhenti. Jalankan Shizuku lalu hubungkan ulang.")
        }

    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
            .onFailure { Log.e(TAG, "Cannot register binder received listener", it) }
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }
            .onFailure { Log.e(TAG, "Cannot register binder dead listener", it) }
        if (isBinderRunning() && hasPermission()) bindIfAuthorized()
    }

    fun isBinderRunning(): Boolean =
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission() {
        if (!isBinderRunning()) {
            lastError = "Jalankan Shizuku terlebih dahulu."
            return
        }
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
            .onFailure {
                lastError = it.message ?: "Permintaan izin Shizuku gagal."
                Log.e(TAG, lastError, it)
            }
    }

    @Synchronized
    fun bindIfAuthorized(): Boolean {
        if (!isBinderRunning()) {
            lastError = "Shizuku belum berjalan."
            return false
        }
        if (!hasPermission()) {
            lastError = "Izin SmartNet belum diberikan di Shizuku."
            return false
        }
        if (controller?.asBinder()?.isBinderAlive == true) return true
        if (bindRequested) return true

        return try {
            lastError = ""
            bindRequested = true
            Log.i(TAG, "Requesting UserService: $serviceComponent")
            Shizuku.bindUserService(args, connection)
            handler.removeCallbacks(connectTimeout)
            handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
            true
        } catch (e: Exception) {
            clearConnectionState(
                "Gagal meminta UserService: ${e.message ?: e.javaClass.simpleName}"
            )
            false
        }
    }

    @Synchronized
    fun unbind() {
        handler.removeCallbacks(connectTimeout)
        runCatching { Shizuku.unbindUserService(args, connection, true) }
            .onFailure { Log.w(TAG, "UserService unbind failed", it) }
        controller = null
        bindRequested = false
    }

    @Synchronized
    fun retry() {
        unbind()
        bindIfAuthorized()
    }
}

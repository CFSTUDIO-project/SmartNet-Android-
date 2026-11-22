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
    private const val CONNECT_TIMEOUT_MS = 15000L
    private const val USER_SERVICE_TAG = "app.smartnet.system-service"

    @Volatile var controller: ISmartNetController? = null
        private set
    @Volatile var lastError: String = ""
        private set
    @Volatile private var bound = false

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var appContext: Context
    private var initialized = false
    private var bindStartedAt = 0L

    private val args by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(appContext, SmartNetUserService::class.java)
        ).daemon(false)
            .processNameSuffix("system")
            .tag(USER_SERVICE_TAG)
            .version(2)
    }

    private val connectTimeout = Runnable {
        if (bound && controller == null) {
            bound = false
            lastError = "UserService tidak merespons dalam 15 detik. Coba hubungkan ulang."
            Log.e(TAG, lastError)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            handler.removeCallbacks(connectTimeout)
            if (service == null || !service.isBinderAlive) {
                controller = null
                bound = false
                lastError = "Shizuku mengembalikan Binder kosong atau sudah mati."
                Log.e(TAG, lastError)
                return
            }
            val remote = ISmartNetController.Stub.asInterface(service)
            if (remote == null) {
                controller = null
                bound = false
                lastError = "Tidak dapat membaca interface SmartNet UserService."
                return
            }
            controller = remote
            bound = true
            lastError = ""
            Log.i(TAG, "SmartNet UserService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            controller = null
            bound = false
            handler.removeCallbacks(connectTimeout)
            lastError = "UserService terputus. Hubungkan ulang."
            Log.w(TAG, lastError)
        }

        override fun onBindingDied(name: ComponentName?) {
            controller = null
            bound = false
            handler.removeCallbacks(connectTimeout)
            lastError = "Binding UserService mati. Tekan Hubungkan service lagi."
            Log.e(TAG, lastError)
        }

        override fun onNullBinding(name: ComponentName?) {
            controller = null
            bound = false
            handler.removeCallbacks(connectTimeout)
            lastError = "Shizuku tidak mengembalikan service. Periksa izin aplikasi di Shizuku."
            Log.e(TAG, lastError)
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder received")
        if (hasPermission()) bindIfAuthorized()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        controller = null
        bound = false
        handler.removeCallbacks(connectTimeout)
        lastError = "Shizuku berhenti. Jalankan Shizuku lalu hubungkan ulang."
        Log.w(TAG, lastError)
    }

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
        if (controller != null) return true
        if (bound) return true

        return runCatching {
            lastError = ""
            bindStartedAt = System.currentTimeMillis()
            bound = true
            Shizuku.bindUserService(args, connection)
            handler.removeCallbacks(connectTimeout)
            handler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
            Log.i(TAG, "Requested UserService bind at $bindStartedAt")
            true
        }.onFailure {
            bound = false
            lastError = "Gagal memulai UserService: ${it.message ?: it.javaClass.simpleName}"
            Log.e(TAG, lastError, it)
        }.getOrDefault(false)
    }

    @Synchronized
    fun unbind() {
        handler.removeCallbacks(connectTimeout)
        if (bound) runCatching {
            Shizuku.unbindUserService(args, connection, true)
        }.onFailure { Log.w(TAG, "Unbind failed", it) }
        controller = null
        bound = false
    }

    fun retry() {
        unbind()
        bindIfAuthorized()
    }
}

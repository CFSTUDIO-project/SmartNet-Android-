package app.smartnet.bridge

import android.content.ComponentName
import android.graphics.Rect
import android.os.IBinder
import android.util.Log

/**
 * Runs as a Shizuku UserService (shell UID). It uses Android task-manager Binder
 * APIs to request real freeform tasks. It never substitutes a screenshot or a
 * fake app surface when the device refuses the request.
 */
class SmartNetUserService : ISmartNetController.Stub() {
    companion object {
        private const val TAG = "SmartNet-System"
        private const val FREEFORM_MODE = 5
        private const val SETTINGS_ENABLE = "enable_freeform_support"
        private const val SETTINGS_RESIZABLE = "force_resizable_activities"
    }

    @Volatile private var activityTaskManager: Any? = null
    private val taskManagerClass by lazy { Class.forName("android.app.IActivityTaskManager") }

    // AIDL ping only confirms that the Shizuku UserService process and Binder
    // are alive. Do not couple connection state to hidden Android API access:
    // OEM restrictions can block ActivityTaskManager while the Binder is healthy.
    override fun ping(): Boolean = true

    override fun readFreeformSettings(): String {
        val enabled = settingGet(SETTINGS_ENABLE)
        val resizable = settingGet(SETTINGS_RESIZABLE)
        return "$enabled|$resizable"
    }

    /**
     * Enables Android's standard freeform/resizable switches where the OEM
     * exposes them. The caller must ask the user before invoking this method.
     */
    override fun enableFreeform(): String {
        val previous = readFreeformSettings()
        val first = settingPut(SETTINGS_ENABLE, "1")
        val second = settingPut(SETTINGS_RESIZABLE, "1")
        return if (first && second) "OK|$previous" else "PARTIAL|$previous"
    }

    override fun restoreFreeform(snapshot: String): Boolean {
        val parts = snapshot.split('|')
        if (parts.size != 2) return false
        val first = restoreSetting(SETTINGS_ENABLE, parts[0])
        val second = restoreSetting(SETTINGS_RESIZABLE, parts[1])
        return first && second
    }

    override fun launchApp(
        flattenedComponent: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): String {
        val component = ComponentName.unflattenFromString(flattenedComponent)
            ?: return "ERROR|Invalid application component"
        if (left < 0 || top < 0 || right <= left || bottom <= top) {
            return "ERROR|Invalid window bounds"
        }

        val launch = exec(
            "am", "start", "--windowingMode", FREEFORM_MODE.toString(),
            "-n", component.flattenToString()
        )
        if (launch.exitCode != 0) {
            return "ERROR|${launch.output.take(220)}"
        }

        // Some OEM builds need a short interval before the task appears in ATM.
        var task = findTask(component.packageName)
        var attempts = 0
        while (task == null && attempts < 5) {
            Thread.sleep(180)
            task = findTask(component.packageName)
            attempts++
        }
        val found = task ?: return "ERROR|The system accepted the launch, but did not expose a controllable task. No fake window was created."

        // Android versions expose different windowing-mode methods. Prefer the
        // legacy method when present; launch itself already requested mode 5.
        runCatching {
            taskManagerClass.getMethod(
                "setTaskWindowingMode", Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!
            ).invoke(activityTaskManager(), found.id, FREEFORM_MODE, true)
        }
        val resized = resizeTask(found.id, left, top, right, bottom)
        if (!resized) {
            return "ERROR|Task ${found.id} launched, but the device rejected its freeform bounds."
        }
        val verified = runCatching { findTask(component.packageName) }.getOrNull()
        if (verified != null && verified.windowingMode != FREEFORM_MODE) {
            return "ERROR|The OEM launched this app, but did not keep it in Android freeform mode."
        }
        return "OK|${found.id}|$left|$top|$right|$bottom|${found.packageName}"
    }

    override fun resizeTask(taskId: Int, left: Int, top: Int, right: Int, bottom: Int): Boolean {
        if (taskId <= 0 || right <= left || bottom <= top) return false
        val binderResult = runCatching {
            val method = taskManagerClass.getMethod(
                "resizeTask", Int::class.javaPrimitiveType!!, Rect::class.java,
                Int::class.javaPrimitiveType!!
            )
            method.invoke(activityTaskManager(), taskId, Rect(left, top, right, bottom), 0)
            true
        }.onFailure { Log.w(TAG, "Binder resizeTask($taskId) failed; trying shell fallback", it) }.getOrDefault(false)
        if (binderResult) return true
        return runCatching {
            exec("am", "task", "resize", taskId.toString(), left.toString(), top.toString(), right.toString(), bottom.toString()).exitCode == 0
        }.onFailure { Log.e(TAG, "shell resizeTask($taskId) failed", it) }.getOrDefault(false)
    }

    override fun minimizeTask(taskId: Int): Boolean {
        if (taskId <= 0) return false
        val candidates = listOf("android.app.IActivityTaskManager", "android.app.IActivityManager")
        for (className in candidates) {
            val result = runCatching {
                val iface = Class.forName(className)
                val binderName = if (className.endsWith("IActivityTaskManager")) "activity_task" else "activity"
                val service = getSystemServiceBinder(binderName)
                val stub = Class.forName("$className\$Stub")
                val atm = stub.getMethod("asInterface", IBinder::class.java).invoke(null, service)
                val method = iface.methods.firstOrNull {
                    it.name == "moveTaskToBack" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType!!
                } ?: return@runCatching false
                method.invoke(atm, taskId) as? Boolean ?: true
            }.getOrDefault(false)
            if (result) return true
        }
        return false
    }

    override fun closeTask(taskId: Int): Boolean {
        if (taskId <= 0) return false
        val binderResult = runCatching {
            taskManagerClass.getMethod("removeTask", Int::class.javaPrimitiveType!!)
                .invoke(activityTaskManager(), taskId) as? Boolean ?: true
        }.onFailure { Log.w(TAG, "Binder removeTask($taskId) failed; trying shell fallback", it) }.getOrDefault(false)
        if (binderResult) return true
        return runCatching { exec("am", "task", "remove", taskId.toString()).exitCode == 0 }
            .onFailure { Log.e(TAG, "shell removeTask($taskId) failed", it) }.getOrDefault(false)
    }

    override fun dumpVisibleTasks(): String {
        return runCatching {
            val features = exec("pm", "list", "features").output
            val freeformFeature = features.contains("android.software.freeform_window_management")
            val tasks = visibleTasks().joinToString("\n") { task ->
                "${task.id}|${task.packageName}|${task.windowingMode}|${task.bounds.left},${task.bounds.top},${task.bounds.right},${task.bounds.bottom}"
            }
            "FREEFORM_FEATURE=$freeformFeature\n$tasks"
        }.getOrElse { "ERROR|${it.message}" }
    }

    private data class TaskRecord(
        val id: Int,
        val packageName: String,
        val windowingMode: Int,
        val bounds: Rect
    )

    private fun findTask(packageName: String): TaskRecord? =
        visibleTasks().firstOrNull { it.packageName == packageName }

    private fun visibleTasks(): List<TaskRecord> {
        val manager = activityTaskManager()
        val method = taskManagerClass.getMethod(
            "getTasks", Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
        )
        @Suppress("UNCHECKED_CAST")
        val tasks = method.invoke(manager, 100, false, false, 0) as? List<Any> ?: return emptyList()
        return tasks.mapNotNull { task ->
            runCatching {
                val id = task.javaClass.getField("taskId").getInt(task)
                val top = task.javaClass.getField("topActivity").get(task) as? ComponentName
                    ?: return@mapNotNull null
                val config = task.javaClass.getField("configuration").get(task)
                val windowConfig = config.javaClass.getField("windowConfiguration").get(config)
                val bounds = windowConfig.javaClass.getMethod("getBounds").invoke(windowConfig) as Rect
                val mode = windowConfig.javaClass.getMethod("getWindowingMode").invoke(windowConfig) as Int
                val visible = runCatching { task.javaClass.getField("isVisible").getBoolean(task) }.getOrDefault(true)
                if (!visible) null else TaskRecord(id, top.packageName, mode, Rect(bounds))
            }.getOrNull()
        }
    }

    private fun activityTaskManager(): Any {
        activityTaskManager?.let { return it }
        val binder = getSystemServiceBinder("activity_task")
        val stub = Class.forName("android.app.IActivityTaskManager\$Stub")
        val manager = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: error("ActivityTaskManager Binder unavailable")
        activityTaskManager = manager
        return manager
    }

    private fun getSystemServiceBinder(name: String): IBinder {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
        return getService.invoke(null, name) as? IBinder ?: error("System service '$name' unavailable")
    }

    private data class CommandResult(val exitCode: Int, val output: String)

    private fun exec(vararg command: String): CommandResult {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        return CommandResult(code, output)
    }

    private fun settingGet(key: String): String =
        exec("settings", "get", "global", key).output.trim().ifBlank { "__NULL__" }

    private fun settingPut(key: String, value: String): Boolean =
        exec("settings", "put", "global", key, value).exitCode == 0

    private fun restoreSetting(key: String, value: String): Boolean =
        if (value == "__NULL__" || value == "null") {
            exec("settings", "delete", "global", key).exitCode == 0
        } else {
            settingPut(key, value)
        }
}

package app.smartnet

import android.app.Application
import app.smartnet.bridge.ShizukuBridge

class SmartNetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuBridge.initialize(this)
    }
}

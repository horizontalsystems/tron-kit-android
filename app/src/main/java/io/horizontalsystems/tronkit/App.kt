package io.horizontalsystems.tronkit

import android.app.Application
import java.util.logging.Logger

class App : Application() {

    private val logger = Logger.getLogger("App")

    override fun onCreate() {
        super.onCreate()

        TronKit.init()

        instance = this
    }

    companion object {
        lateinit var instance: App
            private set
    }

}

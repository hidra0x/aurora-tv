package ru.auroratv.launcher

import android.app.Application
import ru.auroratv.launcher.core.ChannelControlServer

class AuroraApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ChannelControlServer.start(this)
    }
}

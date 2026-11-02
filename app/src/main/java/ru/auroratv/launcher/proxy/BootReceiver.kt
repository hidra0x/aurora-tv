package ru.auroratv.launcher.proxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.auroratv.launcher.core.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs(context).proxyEnabled) {
            ProxyService.start(context)
        }
    }
}

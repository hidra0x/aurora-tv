package ru.auroratv.launcher.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import ru.auroratv.launcher.MainActivity

/**
 * Двойное нажатие Home: первое оставляет штатное ТВ, второе открывает Aurora.
 * Работает через системный broadcast CLOSE_SYSTEM_DIALOGS (Android 7).
 */
class HomeKeyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) return
        val reason = intent.getStringExtra("reason")
        Log.d(TAG, "close_system_dialogs reason=$reason")
        if (reason != "homekey") return

        val now = SystemClock.elapsedRealtime()
        val delta = now - lastHomeAt
        if (delta in 1..1500L) {
            lastHomeAt = 0L
            val launch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                addCategory(Intent.CATEGORY_HOME)
            }
            try {
                context.startActivity(launch)
            } catch (error: Throwable) {
                Log.w(TAG, "Cannot open Aurora: ${error.message}")
            }
        } else {
            lastHomeAt = now
        }
    }

    private companion object {
        const val TAG = "AuroraHome"
        var lastHomeAt = 0L
    }
}

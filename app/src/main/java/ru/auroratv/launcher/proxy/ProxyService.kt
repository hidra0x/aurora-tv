package ru.auroratv.launcher.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ru.auroratv.launcher.MainActivity
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.Prefs

class ProxyService : Service() {

    private var server: SocksProxyServer? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ProxyStatus.port = DEFAULT_PORT
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs(this).proxyEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        val prefs = Prefs(this)
        prefs.proxyEnabled = true

        if (server == null) {
            ProxyStatus.lastError = null
            val newServer = SocksProxyServer(DEFAULT_PORT)
            try {
                newServer.start()
                server = newServer
                ProxyStatus.running = true
            } catch (error: Throwable) {
                ProxyStatus.running = false
                ProxyStatus.lastError = error.message ?: error.javaClass.simpleName
                prefs.proxyEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ProxyStatus.running = false
        try {
            server?.stop()
        } catch (_: Throwable) {
            // Ignore.
        }
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, ProxyService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_proxy)
            .setContentTitle("Aurora TV · обход YouTube")
            .setContentText("SOCKS5 127.0.0.1:$DEFAULT_PORT")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Остановить", stopPending)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Aurora TV · обход",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Локальный SOCKS5-прокси для SmartTube"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val DEFAULT_PORT = 1081
        private const val CHANNEL_ID = "aurora_proxy"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "ru.auroratv.launcher.proxy.STOP"

        fun start(context: Context) {
            val intent = Intent(context, ProxyService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
                // A foreground service may be restricted while the app is in the
                // background on newer Android; the settings toggle will report it.
            }
        }

        fun stop(context: Context) {
            Prefs(context).proxyEnabled = false
            try {
                context.stopService(Intent(context, ProxyService::class.java))
            } catch (_: Throwable) {
                // Ignore.
            }
        }
    }
}

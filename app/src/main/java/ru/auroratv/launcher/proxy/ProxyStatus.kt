package ru.auroratv.launcher.proxy

object ProxyStatus {
    @Volatile
    var running: Boolean = false

    @Volatile
    var lastError: String? = null

    @Volatile
    var port: Int = ProxyService.DEFAULT_PORT
}

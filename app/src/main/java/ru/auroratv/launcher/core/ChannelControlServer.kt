package ru.auroratv.launcher.core

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * Небольшой HTTP-сервер для управления каналами с телефона/ПК.
 * Работает в процессе Aurora TV, по умолчанию на порту 8765.
 *
 * GET  /                      — веб-панель
 * GET  /api/status            — версия и время сервера
 * GET  /api/channels          — каналы, состояния и расписание
 * POST /api/channels/{n}/maintenance
 * POST /api/channels/{n}/state
 * POST /api/channels/all/state
 * GET  /api/schedule
 * POST /api/schedule
 */
object ChannelControlServer {

    const val PORT = 8765
    private const val TAG = "AuroraControl"

    private var serverSocket: ServerSocket? = null
    private var thread: Thread? = null
    private var appContext: Context? = null
    private var htmlCache: String? = null
    private val pool = Executors.newCachedThreadPool()

    @Synchronized
    fun start(context: Context) {
        if (thread?.isAlive == true) return
        appContext = context.applicationContext
        thread = Thread({
            try {
                val socket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT))
                }
                serverSocket = socket
                Log.i(TAG, "control server started on port $PORT")
                while (!socket.isClosed) {
                    val client = try {
                        socket.accept()
                    } catch (_: Throwable) {
                        break
                    }
                    pool.execute {
                        try {
                            handle(client)
                        } catch (error: Throwable) {
                            Log.w(TAG, "request failed", error)
                        } finally {
                            try {
                                client.close()
                            } catch (_: Throwable) {
                                // Ignore.
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "control server failed", error)
            }
        }, "aurora-control-server").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Throwable) {
            // Ignore.
        }
        serverSocket = null
        thread = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 10000
        val input = client.getInputStream()
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase()] =
                    line.substring(separator + 1).trim()
            }
        }

        val parts = requestLine.split(" ")
        val method = parts.getOrNull(0)?.uppercase() ?: "GET"
        val rawPath = parts.getOrNull(1) ?: "/"
        val path = rawPath.substringBefore('?')
        val query = parseQuery(rawPath.substringAfter('?', ""))
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = readBody(input, length)

        val response = route(method, path, query, body)
        writeResponse(client.getOutputStream(), response)
    }

    private data class Response(
        val status: Int,
        val contentType: String,
        val body: ByteArray
    )

    private fun route(
        method: String,
        path: String,
        query: Map<String, String>,
        body: String
    ): Response {
        val context = appContext ?: return jsonError(503, "server not ready")
        if (method == "OPTIONS") {
            return Response(204, "text/plain", ByteArray(0))
        }

        return try {
            when {
                method == "GET" && (path == "/" || path == "/index.html" || path == "/control") -> {
                    Response(200, "text/html; charset=utf-8", html(context).toByteArray(Charsets.UTF_8))
                }
                method == "GET" && path == "/api/status" -> {
                    json(200, statusJson(context))
                }
                method == "GET" && path == "/api/channels" -> {
                    json(200, channelsJson(context))
                }
                method == "GET" && path == "/api/schedule" -> {
                    json(200, JSONObject().apply {
                        put("ok", true)
                        put("schedule", ChannelControl.schedule(context))
                    })
                }
                method == "POST" && path == "/api/schedule" -> {
                    val parsed = JSONObject(body.ifBlank { "{}" })
                    val schedule = parsed.optJSONObject("schedule") ?: parsed
                    ChannelControl.saveSchedule(context, schedule)
                    json(200, JSONObject().apply {
                        put("ok", true)
                        put("schedule", ChannelControl.schedule(context))
                    })
                }
                method == "POST" && path == "/api/channels/all/state" -> {
                    val parsed = JSONObject(body.ifBlank { "{}" })
                    val state = parsed.optString("state", ChannelControl.STATE_MAINTENANCE)
                    val reason = parsed.optString("reason", "")
                    val until = resolveUntil(parsed)
                    ChannelControl.setAll(context, normalizeState(state), reason, until)
                    json(200, channelsJson(context))
                }
                method == "POST" && path == "/api/maintenance/start-all" -> {
                    val parsed = JSONObject(body.ifBlank { "{}" })
                    ChannelControl.setAll(
                        context,
                        ChannelControl.STATE_MAINTENANCE,
                        parsed.optString("reason", "Плановые работы"),
                        resolveUntil(parsed)
                    )
                    json(200, channelsJson(context))
                }
                method == "POST" && path == "/api/maintenance/stop-all" -> {
                    ChannelControl.setAll(context, ChannelControl.STATE_LIVE)
                    json(200, channelsJson(context))
                }
                method == "POST" && path.startsWith("/api/channels/") -> {
                    handleChannelAction(context, path, body)
                }
                else -> jsonError(404, "not found")
            }
        } catch (error: Throwable) {
            Log.w(TAG, "route failed: $path", error)
            jsonError(500, error.message ?: "internal error")
        }
    }

    private fun handleChannelAction(context: Context, path: String, body: String): Response {
        val parts = path.trim('/').split('/')
        // api / channels / 20 / maintenance
        if (parts.size < 4 || parts[0] != "api" || parts[1] != "channels") {
            return jsonError(404, "unknown channel action")
        }
        val number = parts[2].toIntOrNull() ?: return jsonError(400, "bad channel number")
        val action = parts[3]
        val parsed = try {
            JSONObject(body.ifBlank { "{}" })
        } catch (_: Throwable) {
            return jsonError(400, "bad json")
        }

        when (action) {
            "maintenance" -> {
                val enabled = parsed.optBoolean("enabled", true)
                if (!enabled) {
                    ChannelControl.setState(context, number, ChannelControl.STATE_LIVE)
                } else {
                    ChannelControl.setState(
                        context,
                        number,
                        ChannelControl.STATE_MAINTENANCE,
                        parsed.optString("reason", "Плановые работы"),
                        resolveUntil(parsed)
                    )
                }
            }
            "state" -> {
                val state = normalizeState(parsed.optString("state", ChannelControl.STATE_LIVE))
                ChannelControl.setState(
                    context,
                    number,
                    state,
                    parsed.optString("reason", ""),
                    resolveUntil(parsed),
                    parsed.optString("version", ""),
                    parsed.optString("notes", "")
                )
            }
            "upgrade" -> {
                ChannelControl.setState(
                    context,
                    number,
                    ChannelControl.STATE_UPGRADING,
                    parsed.optString("reason", "Обновление канала"),
                    resolveUntil(parsed),
                    parsed.optString("version", ""),
                    parsed.optString("notes", "")
                )
            }
            "live" -> ChannelControl.setState(context, number, ChannelControl.STATE_LIVE)
            else -> return jsonError(404, "unknown channel action")
        }
        return json(200, channelsJson(context))
    }

    private fun normalizeState(value: String): String = when (value) {
        ChannelControl.STATE_LIVE,
        ChannelControl.STATE_MAINTENANCE,
        ChannelControl.STATE_UPGRADING -> value
        else -> ChannelControl.STATE_MAINTENANCE
    }

    private fun resolveUntil(body: JSONObject): Long {
        val explicit = body.optLong("until", 0L)
        if (explicit > 0L) return explicit
        val minutes = body.optLong("minutes", 0L)
        if (minutes > 0L) return System.currentTimeMillis() + minutes * 60_000L
        return System.currentTimeMillis() + 60 * 60_000L
    }

    private fun statusJson(context: Context): JSONObject {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Throwable) {
            "1.5"
        }
        return JSONObject().apply {
            put("ok", true)
            put("app", "Aurora TV")
            put("version", version)
            put("port", PORT)
            put("serverTime", System.currentTimeMillis())
            put("maintenance", ChannelControl.channels(context).count { !it.isLive })
        }
    }

    private fun channelsJson(context: Context): JSONObject {
        val list = ChannelControl.channels(context)
        val array = org.json.JSONArray()
        list.forEach { channel ->
            array.put(JSONObject().apply {
                put("number", channel.number)
                put("name", channel.name)
                put("kind", channel.kind)
                put("state", channel.state)
                put("reason", channel.reason)
                put("until", channel.until)
                put("version", channel.version)
                put("notes", channel.notes)
                put("updatedAt", channel.updatedAt)
            })
        }
        return JSONObject().apply {
            put("ok", true)
            put("serverTime", System.currentTimeMillis())
            put("channels", array)
            put("schedule", ChannelControl.schedule(context))
        }
    }

    private fun json(status: Int, value: JSONObject): Response =
        Response(status, "application/json; charset=utf-8", value.toString().toByteArray(Charsets.UTF_8))

    private fun jsonError(status: Int, message: String): Response = json(
        status,
        JSONObject().apply {
            put("ok", false)
            put("error", message)
        }
    )

    private fun html(context: Context): String {
        htmlCache?.let { return it }
        val text = try {
            context.assets.open("control.html")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        } catch (_: Throwable) {
            "<html><body><h1>Aurora TV</h1><p>control.html not found</p></body></html>"
        }
        htmlCache = text
        return text
    }

    private fun writeResponse(output: OutputStream, response: Response) {
        val reason = when (response.status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            404 -> "Not Found"
            500 -> "Internal Server Error"
            503 -> "Service Unavailable"
            else -> "OK"
        }
        val header = buildString {
            append("HTTP/1.1 ${response.status} $reason\r\n")
            append("Content-Type: ${response.contentType}\r\n")
            append("Content-Length: ${response.body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            append("Access-Control-Allow-Headers: Content-Type\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(response.body)
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte == -1) {
                return if (buffer.size() == 0) null else buffer.toString("UTF-8")
            }
            if (byte == '\n'.code) {
                return buffer.toString("UTF-8").trimEnd('\r')
            }
            buffer.write(byte)
        }
    }

    private fun readBody(input: InputStream, length: Int): String {
        if (length <= 0) return ""
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return String(bytes, 0, offset, Charsets.UTF_8)
    }

    private fun parseQuery(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        return value.split('&').mapNotNull { pair ->
            val index = pair.indexOf('=')
            if (index <= 0) null else {
                val key = URLDecoder.decode(pair.substring(0, index), "UTF-8")
                val item = URLDecoder.decode(pair.substring(index + 1), "UTF-8")
                key to item
            }
        }.toMap()
    }
}

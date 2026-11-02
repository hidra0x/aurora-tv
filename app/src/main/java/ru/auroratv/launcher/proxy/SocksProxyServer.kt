package ru.auroratv.launcher.proxy

import android.util.Log
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal SOCKS5 server for SmartTube.
 *
 * It intentionally has no authentication and listens only on loopback. The first
 * TLS ClientHello is split into many small TCP writes before the connection is
 * bridged, which is the part that gets around simple SNI-based DPI.
 */
class SocksProxyServer(private val port: Int) {

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val running = AtomicBoolean(false)

    private val executor = ThreadPoolExecutor(
        4,
        64,
        60L,
        TimeUnit.SECONDS,
        SynchronousQueue(),
        ThreadFactory { runnable ->
            Thread(runnable, "aurora-socks").apply { isDaemon = true }
        }
    )

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
        serverSocket = socket
        acceptThread = Thread({ acceptLoop() }, "aurora-socks-accept").apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "SOCKS5 listening on 127.0.0.1:$port")
    }

    fun stop() {
        running.set(false)
        closeQuietly(serverSocket)
        serverSocket = null
        activeSockets.forEach { closeQuietly(it) }
        activeSockets.clear()
        executor.shutdownNow()
        Log.i(TAG, "SOCKS5 stopped")
    }

    private fun acceptLoop() {
        while (running.get()) {
            val client = try {
                serverSocket?.accept()
            } catch (_: Throwable) {
                null
            } ?: continue
            activeSockets.add(client)
            try {
                executor.execute { handleClient(client) }
            } catch (_: RejectedExecutionException) {
                activeSockets.remove(client)
                closeQuietly(client)
            } catch (_: Throwable) {
                activeSockets.remove(client)
                closeQuietly(client)
            }
        }
    }

    private fun handleClient(client: Socket) {
        var remote: Socket? = null
        try {
            client.tcpNoDelay = true
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            val input = client.getInputStream()
            val output = BufferedOutputStream(client.getOutputStream())

            if (readByte(input) != 0x05) return
            val methodCount = readByte(input)
            if (methodCount <= 0) return
            repeat(methodCount) { readByte(input) }
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            if (readByte(input) != 0x05) return
            val command = readByte(input)
            readByte(input) // reserved
            val addressType = readByte(input)
            val host = when (addressType) {
                0x01 -> readIpv4(input)
                0x03 -> readDomain(input)
                0x04 -> readIpv6(input)
                else -> {
                    sendReply(output, 0x08)
                    return
                }
            } ?: return
            val targetPort = (readByte(input) shl 8) or readByte(input)

            if (command != 0x01) {
                sendReply(output, 0x07)
                return
            }

            remote = connect(host, targetPort)
            if (remote == null) {
                sendReply(output, 0x05)
                return
            }
            activeSockets.add(remote)
            sendReply(output, 0x00)

            client.soTimeout = 0
            remote.soTimeout = 0
            proxy(client, remote)
        } catch (error: Throwable) {
            Log.d(TAG, "connection ended: ${error.javaClass.simpleName}")
        } finally {
            activeSockets.remove(client)
            remote?.let { activeSockets.remove(it) }
            closeQuietly(client)
            closeQuietly(remote)
        }
    }

    private fun proxy(client: Socket, remote: Socket) {
        val initial = readInitialBytes(client)
        val remoteOutput = remote.getOutputStream()
        if (initial.isNotEmpty()) {
            if (initial[0] == 0x16.toByte()) {
                writeFragmented(remoteOutput, initial)
            } else {
                remoteOutput.write(initial)
                remoteOutput.flush()
            }
        }

        val closed = AtomicBoolean(false)
        val latch = CountDownLatch(2)
        val closeBoth = {
            if (closed.compareAndSet(false, true)) {
                closeQuietly(client)
                closeQuietly(remote)
            }
        }

        val up = Thread({
            try {
                copy(client.getInputStream(), remoteOutput)
            } catch (_: Throwable) {
                // Expected on normal connection close.
            } finally {
                latch.countDown()
                closeBoth()
            }
        }, "aurora-socks-up").apply { isDaemon = true; start() }

        val down = Thread({
            try {
                copy(remote.getInputStream(), client.getOutputStream())
            } catch (_: Throwable) {
                // Expected on normal connection close.
            } finally {
                latch.countDown()
                closeBoth()
            }
        }, "aurora-socks-down").apply { isDaemon = true; start() }

        // Let the connection live as long as both sides need it, but don't leak
        // threads forever if a peer disappears without FIN.
        latch.await(30, TimeUnit.MINUTES)
        up.interrupt()
        down.interrupt()
        closeBoth()
    }

    /**
     * Reads the first TLS record if the client sent one. A TLS ClientHello is
     * small (normally under 1 KB), so reading one record gives us the SNI part.
     */
    private fun readInitialBytes(client: Socket): ByteArray {
        val input = client.getInputStream()
        client.soTimeout = INITIAL_READ_TIMEOUT_MS
        try {
            val header = ByteArray(5)
            var read = 0
            while (read < header.size) {
                val count = input.read(header, read, header.size - read)
                if (count < 0) break
                read += count
            }
            if (read == 0) return ByteArray(0)
            if (read < header.size || header[0] != 0x16.toByte()) {
                return header.copyOf(read)
            }

            val length = ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
            if (length <= 0 || length > MAX_TLS_RECORD) {
                return header.copyOf(read)
            }
            val body = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val count = input.read(body, offset, length - offset)
                if (count < 0) break
                offset += count
            }
            val result = ByteArray(header.size + offset)
            System.arraycopy(header, 0, result, 0, header.size)
            System.arraycopy(body, 0, result, header.size, offset)
            return result
        } finally {
            client.soTimeout = 0
        }
    }

    private fun writeFragmented(output: OutputStream, bytes: ByteArray) {
        if (!FRAGMENT_TLS || bytes.size < 12 || bytes[0] != 0x16.toByte()) {
            output.write(bytes)
            output.flush()
            return
        }

        // Re-frame the ClientHello into several valid TLS handshake records.
        // TLS stacks reassemble handshake messages across records, while simple
        // SNI-based DPI boxes usually inspect one record at a time.
        val major = bytes[1]
        val minor = bytes[2]
        val payload = bytes.copyOfRange(5, bytes.size)
        var offset = 0
        var index = 0
        val cuts = intArrayOf(9, 37, 73, 137)
        while (offset < payload.size) {
            val cut = if (index < cuts.size) minOf(cuts[index], payload.size) else payload.size
            val length = cut - offset
            if (length <= 0) {
                index++
                continue
            }
            output.write(0x16)
            output.write(major.toInt())
            output.write(minor.toInt())
            output.write((length shr 8) and 0xFF)
            output.write(length and 0xFF)
            output.write(payload, offset, length)
            output.flush()
            offset = cut
            index++
            sleep(20L)
        }
    }

    private fun connect(host: String, port: Int): Socket? {
        val addresses = try {
            InetAddress.getAllByName(host).sortedBy { it is Inet6Address }
        } catch (_: Throwable) {
            return null
        }
        for (address in addresses.take(6)) {
            val socket = Socket()
            try {
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                return socket
            } catch (_: Throwable) {
                closeQuietly(socket)
            }
        }
        return null
    }

    private fun readDomain(input: InputStream): String? {
        val length = readByte(input)
        if (length <= 0) return null
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(bytes, offset, length - offset)
            if (count < 0) return null
            offset += count
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun readIpv4(input: InputStream): String? {
        val bytes = ByteArray(4)
        var offset = 0
        while (offset < 4) {
            val count = input.read(bytes, offset, 4 - offset)
            if (count < 0) return null
            offset += count
        }
        return bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
    }

    private fun readIpv6(input: InputStream): String? {
        val bytes = ByteArray(16)
        var offset = 0
        while (offset < 16) {
            val count = input.read(bytes, offset, 16 - offset)
            if (count < 0) return null
            offset += count
        }
        return try {
            InetAddress.getByAddress(bytes).hostAddress
        } catch (_: Throwable) {
            null
        }
    }

    private fun sendReply(output: OutputStream, code: Int) {
        try {
            output.write(byteArrayOf(0x05, code.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()
        } catch (_: Throwable) {
            // Client is gone.
        }
    }

    private fun readByte(input: InputStream): Int {
        val value = input.read()
        if (value < 0) throw IllegalStateException("Unexpected end of stream")
        return value and 0xFF
    }

    private fun copy(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            output.flush()
        }
    }

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun closeQuietly(socket: Socket?) {
        try {
            socket?.close()
        } catch (_: Throwable) {
            // Ignore.
        }
    }

    private fun closeQuietly(server: ServerSocket?) {
        try {
            server?.close()
        } catch (_: Throwable) {
            // Ignore.
        }
    }

    private companion object {
        const val TAG = "AuroraProxy"
        const val FRAGMENT_TLS = false
        const val HANDSHAKE_TIMEOUT_MS = 15_000
        const val INITIAL_READ_TIMEOUT_MS = 2_500
        const val CONNECT_TIMEOUT_MS = 8_000
        const val MAX_TLS_RECORD = 18_432
    }
}

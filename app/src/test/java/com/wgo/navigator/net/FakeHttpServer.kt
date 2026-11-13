package com.wgo.navigator.net

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A local HTTP/1.1 endpoint on a plain [ServerSocket], with no test dependency. (The JDK's com.sun.net.httpserver
 * exists at runtime but is not on the Android unit-test compile classpath, which is android.jar.) One connection per
 * request (`Connection: close`). Each request is recorded and answered by [respond]: (status, body, delay in ms).
 */
class FakeHttpServer(@Volatile var respond: (Request) -> Triple<Int, String, Long>) : AutoCloseable {
    data class Request(val method: String, val path: String, val query: String?, val headers: Map<String, String>, val body: String)

    val requests = CopyOnWriteArrayList<Request>()
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))

    init {
        thread(isDaemon = true, name = "fake-http") {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    break
                }
                thread(isDaemon = true) { serve(client) }
            }
        }
    }

    val baseUrl: String get() = "http://127.0.0.1:${socket.localPort}"

    private fun serve(client: Socket) = client.use {
        try {
            val input = BufferedInputStream(it.getInputStream())
            val requestLine = readLine(input) ?: return
            val (method, target) = requestLine.split(' ').let { p -> p[0] to p[1] }
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n < 0) break
                read += n
            }
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "").ifEmpty { null }
            val req = Request(method, path, query, headers, body.toString(Charsets.UTF_8))
            requests += req
            val (code, text, delayMs) = respond(req)
            if (delayMs > 0) Thread.sleep(delayMs)
            val bytes = text.toByteArray(Charsets.UTF_8)
            val head = "HTTP/1.1 $code X\r\nContent-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
            val out = it.getOutputStream()
            out.write(head.toByteArray(Charsets.US_ASCII))
            out.write(bytes)
            out.flush()
        } catch (e: Exception) {
            // the client gave up (timeout tests)
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
            buf.write(b)
        }
    }

    override fun close() = socket.close()

    companion object {
        fun resource(name: String): String =
            FakeHttpServer::class.java.classLoader!!.getResource(name)!!.readText(Charsets.UTF_8)
    }
}

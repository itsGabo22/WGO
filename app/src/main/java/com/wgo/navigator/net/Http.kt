package com.wgo.navigator.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/** A finished HTTP exchange (any status code). */
class HttpResponse(val code: Int, val body: String)

/**
 * Minimal HTTP over [HttpURLConnection] (no client library). One blocking call on [Dispatchers.IO]; cancelling the
 * caller disconnects the socket. Transport problems become failures: a READ timeout (connected, but the server is
 * slow) -> [WgoFailure.Timeout]; a connect timeout or anything else (no route to host, DNS, reset) ->
 * [WgoFailure.Offline]. Every request is counted (debug metrics) by [kind]; neither
 * URLs, bodies nor headers are ever logged.
 */
class HttpClient(private val userAgent: String) {
    suspend fun request(
        kind: WgoMetrics.Net,
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): Outcome<HttpResponse> = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext Outcome.Err(WgoFailure.Offline)
        } catch (e: IllegalArgumentException) {
            return@withContext Outcome.Err(WgoFailure.Unavailable)
        }
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { if (it != null) conn.disconnect() }
        var sent = 0L
        var received = 0L
        var connected = false
        try {
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.requestMethod = method
            conn.instanceFollowRedirects = true
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val bytes = body?.toByteArray(Charsets.UTF_8)
            if (bytes != null) {
                sent = bytes.size.toLong()
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(bytes.size)
            }
            // Connect explicitly, so a timeout while connecting (no usable network) is told apart from a slow answer.
            conn.connect()
            connected = true
            if (bytes != null) conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes() }?.also { received = it.size.toLong() }?.toString(Charsets.UTF_8).orEmpty()
            log(kind, code, started, sent, received, connectTimeoutMs, readTimeoutMs)
            Outcome.Ok(HttpResponse(code, text))
        } catch (e: SocketTimeoutException) {
            log(kind, if (connected) -1 else -3, started, sent, received, connectTimeoutMs, readTimeoutMs)
            Outcome.Err(if (connected) WgoFailure.Timeout else WgoFailure.Offline)
        } catch (e: IOException) {
            log(kind, -2, started, sent, received, connectTimeoutMs, readTimeoutMs)
            Outcome.Err(WgoFailure.Offline)
        } finally {
            cancelHandle?.dispose()
            conn.disconnect()
        }
    }

    private fun log(kind: WgoMetrics.Net, code: Int, started: Long, sent: Long, received: Long, connect: Int, read: Int) {
        WgoMetrics.net(kind, sent + received)
        WgoMetrics.event(
            "net kind=${kind.name} code=$code ms=${(System.nanoTime() - started) / 1_000_000} " +
                "up=${sent}B down=${received}B timeouts=$connect/$read"
        )
    }
}

/** True when the device has a network that claims internet access (cheap, no request). */
fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

package ua.otboi.app.net

import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.net.ssl.SSLException

class HttpResult(val code: Int, val body: String)

object Http {
    fun get(url: String, userAgent: String, accept: String = "*/*"): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.instanceFollowRedirects = true
        conn.useCaches = false
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Accept", accept)
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return HttpResult(code, body)
        } finally {
            conn.disconnect()
        }
    }

    fun shortError(e: Throwable): String = when (e) {
        is UnknownHostException -> ua.otboi.app.i18n.T.eNoInternet
        is SocketTimeoutException -> ua.otboi.app.i18n.T.eTimeout
        is ConnectException -> ua.otboi.app.i18n.T.eNoConnection
        is SSLException -> ua.otboi.app.i18n.T.eSsl
        is org.json.JSONException -> ua.otboi.app.i18n.T.eBadResponse
        else -> ua.otboi.app.i18n.T.eGeneric(e.javaClass.simpleName)
    }
}

internal fun intOf(v: Any?): Int? = when (v) {
    is Int -> v
    is Long -> v.toInt()
    is Number -> v.toInt()
    is String -> v.trim().toIntOrNull()
    else -> null
}

/** ISO-время в миллисекунды; 0 — если не получилось. */
internal fun parseTime(s: String?): Long {
    if (s.isNullOrBlank()) return 0L
    return try {
        Instant.parse(s).toEpochMilli()
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(s).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }
}

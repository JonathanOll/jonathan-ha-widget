package com.lashox.huewidget

import android.content.Context
import java.net.HttpURLConnection
import android.util.Log
import java.net.URL

object Ha {
    private fun prefs(c: Context) = c.getSharedPreferences("ha", Context.MODE_PRIVATE)

    fun save(c: Context, url: String, token: String, entity: String) =
        prefs(c).edit().putString("url", url.trim().trimEnd('/')).putString("token", token.trim())
            .putString("entity", entity.trim()).apply()

    fun url(c: Context) = prefs(c).getString("url", "") ?: ""
    fun token(c: Context) = prefs(c).getString("token", "") ?: ""
    fun entity(c: Context) = prefs(c).getString("entity", "") ?: ""
    fun coverEntity(c: Context) = prefs(c).getString("cover", "") ?: ""
    fun saveCover(c: Context, entity: String) = prefs(c).edit().putString("cover", entity.trim()).apply()

    private fun call(c: Context, method: String, path: String, body: String? = null): String {
        Log.d("HueWidget", method + " " + url(c) + path)
        val conn = URL(url(c) + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("Authorization", "Bearer " + token(c))
            conn.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            Log.d("HueWidget", "HTTP " + code + " for " + path)
            return (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun toggle(c: Context) = service(c, "light/toggle", entity(c))

    /** service = "open_cover" | "close_cover" | "stop_cover" */
    fun cover(c: Context, service: String) = service(c, "cover/$service", coverEntity(c))

    private fun service(c: Context, path: String, entityId: String) {
        Log.d("HueWidget", "service $path $entityId")
        val url = "/api/services/$path"
        val body = "{\"entity_id\":\"" + entityId + "\"}"
        try {
            call(c, "POST", url, body)
        } catch (e: java.net.ConnectException) {
            Log.w("HueWidget", "connect failed, retry once")
            call(c, "POST", url, body)
        } catch (e: java.net.SocketTimeoutException) {
            if (e.message?.startsWith("failed to connect") == true) {
                Log.w("HueWidget", "connect timeout, retry once")
                call(c, "POST", url, body)
            } else throw e
        }
    }

    /** true = on, false = off, null = unknown/error */
    fun isOn(c: Context): Boolean? = try {
        val s = call(c, "GET", "/api/states/" + entity(c))
        when (Regex("\"state\"\\s*:\\s*\"(\\w+)\"").find(s)?.groupValues?.get(1)) {
            "on" -> true
            "off" -> false
            else -> null
        }
    } catch (e: Exception) { Log.e("HueWidget", "isOn failed", e); null }
}

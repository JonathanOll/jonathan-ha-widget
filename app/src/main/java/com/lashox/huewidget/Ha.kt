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
    fun tvEntity(c: Context) = prefs(c).getString("tv", "") ?: ""
    fun ledEntity(c: Context) = prefs(c).getString("led", "") ?: ""
    fun saveTv(c: Context, tv: String, led: String) =
        prefs(c).edit().putString("tv", tv.trim()).putString("led", led.trim()).apply()
    fun pcEntity(c: Context) = prefs(c).getString("pc", "") ?: ""
    fun thermoEntity(c: Context) = prefs(c).getString("thermo", "") ?: ""
    fun humEntity(c: Context) = prefs(c).getString("hum", "") ?: ""
    fun pcOffEntity(c: Context) = prefs(c).getString("pc_off", "") ?: ""
    fun pcStateEntity(c: Context) = prefs(c).getString("pc_state", "") ?: ""
    fun saveMisc(c: Context, pcOn: String, pcOff: String, pcState: String, thermo: String, hum: String) =
        prefs(c).edit().putString("pc", pcOn.trim()).putString("pc_off", pcOff.trim())
            .putString("pc_state", pcState.trim()).putString("thermo", thermo.trim()).putString("hum", hum.trim()).apply()
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

    fun press(c: Context, entityId: String) = service(c, "button/press", entityId)

    /** Fire a "do something" entity: button/script/scene are run, anything else (switch...) is turned on. */
    fun trigger(c: Context, entityId: String) = when (entityId.substringBefore('.')) {
        "button" -> service(c, "button/press", entityId)
        "input_button" -> service(c, "input_button/press", entityId)
        "script" -> service(c, "script/turn_on", entityId)
        "scene" -> service(c, "scene/turn_on", entityId)
        else -> service(c, "homeassistant/turn_on", entityId)
    }

    /** (state, friendly_name) of an entity, or null if unavailable/error. */
    fun info(c: Context, entityId: String): Pair<String, String>? = try {
        val o = org.json.JSONObject(call(c, "GET", "/api/states/" + entityId))
        val st = o.getString("state")
        if (st == "unavailable" || st == "unknown") null
        else st to (o.optJSONObject("attributes")?.optString("friendly_name", "").orEmpty())
    } catch (e: Exception) { Log.w("HueWidget", "info failed " + entityId + ": " + e); null }

    /** Turn any switch/light on or off through the generic homeassistant service. */
    fun power(c: Context, entityId: String, on: Boolean) =
        service(c, "homeassistant/" + (if (on) "turn_on" else "turn_off"), entityId)

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
    fun isOn(c: Context, entityId: String = entity(c)): Boolean? = try {
        val s = call(c, "GET", "/api/states/" + entityId)
        when (Regex("\"state\"\\s*:\\s*\"(\\w+)\"").find(s)?.groupValues?.get(1)) {
            "on", "home" -> true
            "off", "not_home" -> false
            else -> null
        }
    } catch (e: Exception) { Log.e("HueWidget", "isOn failed", e); null }

    /**
     * "Is this device up?" from a sensor that only has a value while the device is running (e.g. PC CPU load).
     * true = has a real value, false = unavailable / unknown / entity missing, null = HA unreachable.
     */
    fun isAlive(c: Context, entityId: String): Boolean? = try {
        val o = org.json.JSONObject(call(c, "GET", "/api/states/" + entityId))
        val st = o.optString("state", "")
        st.isNotEmpty() && st != "unavailable" && st != "unknown"
    } catch (e: org.json.JSONException) {
        false // HA answered but without a state (e.g. 404 entity not found)
    } catch (e: Exception) {
        Log.w("HueWidget", "isAlive failed " + entityId + ": " + e)
        null
    }

    /** All HA entities as (entity_id, friendly_name), for autocomplete. Empty on error. */
    fun fetchEntities(url: String, token: String): List<Pair<String, String>> = try {
        val conn = URL(url.trim().trimEnd('/') + "/api/states").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            conn.setRequestProperty("Authorization", "Bearer " + token.trim())
            val arr = org.json.JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                o.getString("entity_id") to o.optJSONObject("attributes")?.optString("friendly_name", "").orEmpty()
            }.sortedBy { it.first }
        } finally { conn.disconnect() }
    } catch (e: Exception) { Log.w("HueWidget", "fetchEntities failed: " + e); emptyList() }
}

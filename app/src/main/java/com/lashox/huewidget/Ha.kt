package com.lashox.huewidget

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Absolute time limit for a chain of HTTP calls (monotonic clock). */
class Deadline private constructor(private val atMs: Long) {
    fun remainingMs(): Long = atMs - SystemClock.elapsedRealtime()
    val expired: Boolean get() = remainingMs() <= 0

    companion object {
        fun after(ms: Long) = Deadline(SystemClock.elapsedRealtime() + ms)
    }
}

/** Raw HTTP answer from HA. */
class HaResponse(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

object Ha {
    private fun prefs(c: Context) = c.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)

    fun save(c: Context, url: String, token: String, entity: String) {
        val p = prefs(c)
        val e = p.edit().putString(Keys.URL, url.trim().trimEnd('/')).putString(Keys.LIGHT, entity.trim())
        TokenStore.write(p, e, token.trim())
        e.apply()
    }

    fun url(c: Context) = prefs(c).getString(Keys.URL, "") ?: ""
    /** Plaintext token (decrypted from the Keystore-protected pref; "" if missing or undecryptable). */
    fun token(c: Context): String = TokenStore.read(prefs(c))

    /** Refuses to send the token in clear to a non-local host. */
    private fun requireSafeUrl(base: String) {
        if (checkHaUrl(base) == UrlCheck.INSECURE_PUBLIC) throw InsecureUrlException()
    }
    fun entity(c: Context) = prefs(c).getString(Keys.LIGHT, "") ?: ""
    fun coverEntity(c: Context) = prefs(c).getString(Keys.COVER, "") ?: ""
    fun tvEntity(c: Context) = prefs(c).getString(Keys.TV, "") ?: ""
    fun ledEntity(c: Context) = prefs(c).getString(Keys.LED, "") ?: ""
    fun saveTv(c: Context, tv: String, led: String) =
        prefs(c).edit().putString(Keys.TV, tv.trim()).putString(Keys.LED, led.trim()).apply()
    fun pcEntity(c: Context) = prefs(c).getString(Keys.PC_ON, "") ?: ""
    fun thermoEntity(c: Context) = prefs(c).getString(Keys.THERMO, "") ?: ""
    fun humEntity(c: Context) = prefs(c).getString(Keys.HUM, "") ?: ""
    fun pcOffEntity(c: Context) = prefs(c).getString(Keys.PC_OFF, "") ?: ""
    fun pcStateEntity(c: Context) = prefs(c).getString(Keys.PC_STATE, "") ?: ""
    fun saveMisc(c: Context, pcOn: String, pcOff: String, pcState: String, thermo: String, hum: String) =
        prefs(c).edit().putString(Keys.PC_ON, pcOn.trim()).putString(Keys.PC_OFF, pcOff.trim())
            .putString(Keys.PC_STATE, pcState.trim()).putString(Keys.THERMO, thermo.trim())
            .putString(Keys.HUM, hum.trim()).apply()
    fun saveCover(c: Context, entity: String) = prefs(c).edit().putString(Keys.COVER, entity.trim()).apply()

    /** Aborts a connection when its deadline is reached (covers connect + read as a whole). */
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ha-watchdog").apply { isDaemon = true }
    }

    /**
     * One HTTP request bounded by [dl]: connect/read timeouts are clamped to the remaining time
     * and a watchdog disconnects at the deadline. Returns the status and body (any status).
     */
    private fun request(c: Context, method: String, path: String, body: String?, dl: Deadline): HaResponse {
        val rem = dl.remainingMs()
        if (rem < Budget.MIN_REQUEST_MS) throw SocketTimeoutException("time budget exhausted")
        HaLog.d { "$method $path" }
        val base = url(c)
        requireSafeUrl(base)
        val conn = URL(base + path).openConnection() as HttpURLConnection
        val dog = watchdog.schedule({ conn.disconnect() }, rem, TimeUnit.MILLISECONDS)
        try {
            conn.requestMethod = method
            conn.instanceFollowRedirects = false // never carry the token to another (possibly public) host
            conn.connectTimeout = minOf(Budget.CONNECT_MAX_MS, rem).toInt()
            conn.readTimeout = minOf(Budget.READ_MAX_MS, rem).toInt()
            conn.setRequestProperty("Authorization", "Bearer " + token(c))
            conn.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            HaLog.d { "HTTP $code for $path" }
            val text = try {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: IOException) {
                if (code in 200..299) throw e else ""
            }
            return HaResponse(code, text)
        } catch (e: IOException) {
            // killed by the watchdog -> report as a timeout
            if (dl.expired && e !is SocketTimeoutException) throw SocketTimeoutException("deadline reached")
            throw e
        } finally {
            dog.cancel(false)
            conn.disconnect()
        }
    }

    private fun isConnectFailure(e: Exception) =
        e is java.net.ConnectException ||
            (e is SocketTimeoutException && e.message?.startsWith("failed to connect") == true)

    /** POST a service call; throws [HaHttpException] on non-2xx. One connect retry if the budget allows. */
    private fun service(c: Context, path: String, entityId: String, dl: Deadline) {
        HaLog.d { "service $path $entityId" }
        val url = "/api/services/$path"
        val body = JSONObject().put("entity_id", entityId).toString()
        val res = try {
            request(c, "POST", url, body, dl)
        } catch (e: Exception) {
            if (isConnectFailure(e) && dl.remainingMs() >= Budget.RETRY_MIN_MS) {
                HaLog.w("connect failed, retry once")
                request(c, "POST", url, body, dl)
            } else throw e
        }
        if (!res.ok) throw HaHttpException(res.code)
    }

    fun toggle(c: Context, dl: Deadline) = service(c, "light/toggle", entity(c), dl)

    /** Fire a "do something" entity: button/script/scene are run, anything else (switch...) is turned on. */
    fun trigger(c: Context, entityId: String, dl: Deadline) = when (entityId.substringBefore('.')) {
        "button" -> service(c, "button/press", entityId, dl)
        "input_button" -> service(c, "input_button/press", entityId, dl)
        "script" -> service(c, "script/turn_on", entityId, dl)
        "scene" -> service(c, "scene/turn_on", entityId, dl)
        else -> service(c, "homeassistant/turn_on", entityId, dl)
    }

    /** Turn any switch/light on or off through the generic homeassistant service. */
    fun power(c: Context, entityId: String, on: Boolean, dl: Deadline) =
        service(c, "homeassistant/" + (if (on) "turn_on" else "turn_off"), entityId, dl)

    /** service = "open_cover" | "close_cover" | "stop_cover" */
    fun cover(c: Context, service: String, dl: Deadline) = service(c, "cover/$service", coverEntity(c), dl)

    /** GET /api/states/<id>; null on network error. */
    private fun stateResponse(c: Context, entityId: String, dl: Deadline): HaResponse? = try {
        request(c, "GET", "/api/states/" + encodeEntityId(entityId), null, dl)
    } catch (e: Exception) {
        HaLog.w("state read failed", e)
        null
    }

    private fun jsonOf(body: String): JSONObject? = try { JSONObject(body) } catch (e: Exception) { null }

    private fun stateOf(o: JSONObject?): String? = o?.takeIf { it.has("state") }?.optString("state")

    /** (state, friendly_name) of an entity, or null if unavailable/error. */
    fun info(c: Context, entityId: String, dl: Deadline): Pair<String, String>? {
        val res = stateResponse(c, entityId, dl)?.takeIf { it.ok } ?: return null
        val o = jsonOf(res.body) ?: return null
        val st = stateOf(o)
        if (!isRealState(st)) return null
        return st!! to (o.optJSONObject("attributes")?.optString("friendly_name", "").orEmpty())
    }

    /** true = on, false = off, null = unknown/error */
    fun isOn(c: Context, entityId: String, dl: Deadline): Boolean? {
        val res = stateResponse(c, entityId, dl)?.takeIf { it.ok } ?: return null
        return parseOnOff(stateOf(jsonOf(res.body)))
    }

    /**
     * "Is this device up?" from a sensor that only has a value while the device is running (e.g. PC CPU load).
     * true = real value; false = unavailable / unknown / empty / entity missing (404);
     * null = unknown (network error, 401/403, 5xx...) -> keep last known.
     */
    fun isAlive(c: Context, entityId: String, dl: Deadline): Boolean? {
        val res = stateResponse(c, entityId, dl)
        return aliveFrom(res?.code, res?.takeIf { it.ok }?.let { stateOf(jsonOf(it.body)) })
    }

    private val lastToast = AtomicLong(0L)

    /** Short user-visible message for a failed action (e.g. HA unreachable). Safe from any thread. */
    fun toast(c: Context, e: Exception) {
        val msg = when {
            e is InsecureUrlException -> c.getString(R.string.err_insecure_url)
            e is HaHttpException && (e.code == 401 || e.code == 403) -> c.getString(R.string.err_token_refused)
            e is HaHttpException && e.code == 404 -> c.getString(R.string.err_entity_not_found)
            e is HaHttpException -> c.getString(R.string.err_ha_http, e.code)
            e is java.net.ConnectException || e is SocketTimeoutException ||
                e is java.net.UnknownHostException || e is java.net.NoRouteToHostException ->
                c.getString(R.string.err_unreachable)
            else -> c.getString(R.string.err_generic, e.message ?: e.javaClass.simpleName)
        }
        toast(c, msg)
    }

    /** Throttled toast: several parallel failures -> one message. */
    fun toast(c: Context, msg: String) {
        val now = SystemClock.elapsedRealtime()
        val prev = lastToast.get()
        if (now - prev < 2000 || !lastToast.compareAndSet(prev, now)) return
        val app = c.applicationContext
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(app, msg, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    /** All HA entities as (entity_id, friendly_name), for autocomplete. Empty on error (incl. unsafe URL). */
    fun fetchEntities(url: String, token: String): List<Pair<String, String>> = try {
        val base = url.trim().trimEnd('/')
        requireSafeUrl(base)
        val conn = URL(base + "/api/states").openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            conn.setRequestProperty("Authorization", "Bearer " + token.trim())
            val arr = org.json.JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                o.getString("entity_id") to o.optJSONObject("attributes")?.optString("friendly_name", "").orEmpty()
            }.sortedBy { it.first }
        } finally { conn.disconnect() }
    } catch (e: Exception) { HaLog.w("fetchEntities failed", e); emptyList() }
}

/** HA answered with a non-2xx status. */
class HaHttpException(val code: Int) : IOException("HTTP " + code)

/** The saved URL is http:// to a public host: the token is not sent in clear. */
class InsecureUrlException : IOException("cleartext http to a non-local host refused")

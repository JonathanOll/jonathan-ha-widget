package com.lashox.huewidget

import android.content.Context
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * State reads (parallel, bounded by a deadline), cache writes, polling and the action logic
 * shared by every widget, the dashboard, the tiles and the PC confirm dialog.
 * Callers run this on a background thread (see [Actions]).
 */
object HaRepository {
    private val count = AtomicInteger()
    private val pool = Executors.newFixedThreadPool(4) { r ->
        Thread(r, "ha-io-" + count.incrementAndGet()).apply { isDaemon = true }
    }

    private fun prefs(c: Context) = c.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)

    // ---- cache ----

    fun cached(c: Context, key: String): Boolean = prefs(c).getBoolean(key, false)

    private fun cache(c: Context, key: String, v: Boolean?) {
        if (v != null) prefs(c).edit().putBoolean(key, v).apply()
    }

    // ---- parallel / polling ----

    /** Runs [tasks] in parallel, waiting at most until [dl]. A task that failed or timed out yields null. */
    fun <T> parallel(dl: Deadline, tasks: List<() -> T>): List<T?> {
        if (tasks.isEmpty()) return emptyList()
        val rem = dl.remainingMs()
        if (rem <= 0) return tasks.map { null }
        val futures = pool.invokeAll(tasks.map { t -> Callable { t() } }, rem, TimeUnit.MILLISECONDS)
        return futures.map { f -> try { if (f.isCancelled) null else f.get() } catch (e: Exception) { null } }
    }

    /** Polls [read] every 500 ms until [done] or the deadline / max polls. Returns the last value read. */
    fun <T> pollUntil(dl: Deadline, read: () -> T, done: (T) -> Boolean): T? {
        var last: T? = null
        var i = 0
        while (i < Budget.POLL_MAX && dl.remainingMs() > Budget.POLL_INTERVAL_MS + Budget.MIN_REQUEST_MS) {
            Thread.sleep(Budget.POLL_INTERVAL_MS)
            val v = read()
            last = v
            if (done(v)) break
            i++
        }
        return last
    }

    /** On/off of several entities in parallel; blank ids map to null. */
    private fun readOnOff(c: Context, ids: List<String>, dl: Deadline): List<Boolean?> =
        parallel(dl, ids.map { id -> { if (id.isBlank()) null else Ha.isOn(c, id, dl) } })

    // ---- fetch + cache ----

    /** Reads every configured entity in parallel and caches the results; on error the cache is kept. */
    fun fetchAll(c: Context, dl: Deadline) {
        val light = Ha.entity(c)
        val tv = Ha.tvEntity(c)
        val led = Ha.ledEntity(c)
        val pc = Ha.pcStateEntity(c)
        val thermo = Ha.thermoEntity(c)
        val hum = Ha.humEntity(c)
        val r = parallel<Any?>(dl, listOf(
            { light.ifConfigured { Ha.isOn(c, it, dl) } },
            { tv.ifConfigured { Ha.isOn(c, it, dl) } },
            { led.ifConfigured { Ha.isOn(c, it, dl) } },
            { pc.ifConfigured { Ha.isAlive(c, it, dl) } },
            { thermo.ifConfigured { Ha.info(c, it, dl) } },
            { hum.ifConfigured { Ha.info(c, it, dl) } },
        ))
        @Suppress("UNCHECKED_CAST") val t = r[4] as Pair<String, String>?
        @Suppress("UNCHECKED_CAST") val h = r[5] as Pair<String, String>?
        prefs(c).edit().apply {
            (r[0] as Boolean?)?.let { putBoolean(Keys.CACHE_LIGHT, it) }
            (r[1] as Boolean?)?.let { putBoolean(Keys.CACHE_TV, it) }
            (r[2] as Boolean?)?.let { putBoolean(Keys.CACHE_LED, it) }
            (r[3] as Boolean?)?.let { putBoolean(Keys.CACHE_PC, it) }
            if (t != null) { putString(Keys.CACHE_TEMP, t.first); putString(Keys.CACHE_LABEL, t.second) }
            if (h != null) putString(Keys.CACHE_HUM, h.first)
        }.apply()
    }

    private inline fun <T> String.ifConfigured(block: (String) -> T): T? = if (isBlank()) null else block(this)

    // ---- actions (throw nothing: failures are toasted) ----

    private fun fail(c: Context, what: String, e: Exception) {
        HaLog.w("$what failed", e)
        Ha.toast(c, e)
    }

    /** Toggle the light, then poll until HA reports the new state (HA/Hue can lag). */
    fun toggleLight(c: Context, dl: Deadline) {
        val id = Ha.entity(c)
        val before = if (id.isBlank()) null else Ha.isOn(c, id, dl)
        cache(c, Keys.CACHE_LIGHT, before)
        try { Ha.toggle(c, dl) } catch (e: Exception) { fail(c, "toggle", e); return }
        if (id.isBlank()) return
        val after = pollUntil(dl, { Ha.isOn(c, id, dl) }) { it != null && it != before }
        cache(c, Keys.CACHE_LIGHT, after)
    }

    fun cover(c: Context, service: String, dl: Deadline) {
        try { Ha.cover(c, service, dl) } catch (e: Exception) { fail(c, "cover", e) }
    }

    /**
     * TV / LED action from a fresh state read. [requireTvState]: if the TV state is unknown,
     * do nothing and tell the user (used by the tile).
     */
    fun tv(c: Context, action: TvAction, dl: Deadline, requireTvState: Boolean = false) {
        val tvId = Ha.tvEntity(c)
        val ledId = Ha.ledEntity(c)
        val (tv, led) = readOnOff(c, listOf(tvId, ledId), dl)
        cache(c, Keys.CACHE_TV, tv)
        cache(c, Keys.CACHE_LED, led)
        if (requireTvState && tv == null) {
            Ha.toast(c, c.getString(R.string.err_tv_state_unknown))
            return
        }
        val targets = tvTargets(action, tvId, ledId, tv, led)
        val errors = parallel(dl, targets.map { (id, on) ->
            { try { Ha.power(c, id, on, dl); null } catch (e: Exception) { e } }
        })
        errors.filterNotNull().firstOrNull()?.let { fail(c, "power", it) }
        val sent = targets.filterIndexed { i, _ -> errors.getOrNull(i) == null }
        if (sent.isEmpty()) return
        val after = pollUntil(dl, { readOnOff(c, listOf(tvId, ledId), dl) }) { s ->
            sent.all { (id, on) -> (if (id == tvId) s[0] else s[1]) == on }
        } ?: return
        cache(c, Keys.CACHE_TV, after[0])
        cache(c, Keys.CACHE_LED, after[1])
    }

    /** PC on/off through its dedicated entity, then poll the state sensor (if any) until it matches. */
    fun pc(c: Context, on: Boolean, dl: Deadline) {
        val id = if (on) Ha.pcEntity(c) else Ha.pcOffEntity(c)
        if (id.isBlank()) return
        try { Ha.trigger(c, id, dl) } catch (e: Exception) { fail(c, "pc action", e); return }
        val stateId = Ha.pcStateEntity(c)
        if (stateId.isBlank()) return
        val after = pollUntil(dl, { Ha.isAlive(c, stateId, dl) }) { it == on }
        cache(c, Keys.CACHE_PC, after)
    }
}

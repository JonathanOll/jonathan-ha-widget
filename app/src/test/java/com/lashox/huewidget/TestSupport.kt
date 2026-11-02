package com.lashox.huewidget

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.robolectric.Shadows.shadowOf
import java.net.InetAddress
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * Real monotonic time for [HaClock] (Robolectric freezes SystemClock, so deadlines would never expire).
 * Every [install] jumps one hour ahead, so debounce / toast throttling windows of a previous test never leak.
 */
object TestClock {
    private val origin = System.nanoTime()
    @Volatile private var offset = 0L
    private val original = HaClock.now

    fun install() {
        offset += 3_600_000L
        HaClock.now = { (System.nanoTime() - origin) / 1_000_000L + offset }
    }

    fun advance(ms: Long) { offset += ms }

    fun restore() { HaClock.now = original }
}

/** One recorded HA service call. */
data class Call(val service: String, val entity: String)

/**
 * Minimal fake Home Assistant REST API:
 *  GET  /api/states            -> every entity
 *  GET  /api/states/<id>       -> {"entity_id","state","attributes":{"friendly_name"}} or 404
 *  POST /api/services/<d>/<s>  -> records the call; turn_on/turn_off/toggle update the state
 */
class FakeHa : Dispatcher() {
    val states = ConcurrentHashMap<String, String>()
    val names = ConcurrentHashMap<String, String>()
    /** Forced answer for GET /api/states/<id>. */
    val stateOverride = ConcurrentHashMap<String, () -> MockResponse>()
    /** Forced answer for every service call (status code, delay, redirect...). */
    @Volatile var serviceOverride: (() -> MockResponse)? = null
    /** false: services are accepted but the state never changes (HA lagging). */
    @Volatile var applyServices = true
    /** Extra side effect of a service call (e.g. a PC that boots). */
    @Volatile var onService: ((Call) -> Unit)? = null
    val calls = CopyOnWriteArrayList<Call>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path.orEmpty()
        if (request.method == "GET" && path == "/api/states") {
            val arr = JSONArray()
            states.keys.forEach { arr.put(stateJson(it)) }
            return MockResponse().setBody(arr.toString())
        }
        if (request.method == "GET" && path.startsWith("/api/states/")) {
            val id = URLDecoder.decode(path.removePrefix("/api/states/"), "UTF-8")
            stateOverride[id]?.let { return it() }
            if (!states.containsKey(id)) return MockResponse().setResponseCode(404).setBody("{\"message\":\"Entity not found.\"}")
            return MockResponse().setBody(stateJson(id).toString())
        }
        if (request.method == "POST" && path.startsWith("/api/services/")) {
            val svc = path.removePrefix("/api/services/")
            val id = JSONObject(request.body.clone().readUtf8()).getString("entity_id")
            val call = Call(svc, id)
            calls += call
            serviceOverride?.let { return it() }
            if (applyServices) {
                when {
                    svc.endsWith("/turn_on") -> states[id] = "on"
                    svc.endsWith("/turn_off") -> states[id] = "off"
                    svc.endsWith("/toggle") -> states[id] = if (states[id] == "on") "off" else "on"
                }
                onService?.invoke(call)
            }
            return MockResponse().setBody("[]")
        }
        return MockResponse().setResponseCode(404)
    }

    private fun stateJson(id: String) = JSONObject()
        .put("entity_id", id)
        .put("state", states[id])
        .put("attributes", JSONObject().put("friendly_name", names[id] ?: ""))
}

/** Base for Robolectric tests talking to a [FakeHa] on 127.0.0.1 (local, so plain http is allowed). */
abstract class FakeHaTest {
    protected lateinit var ctx: Context
    protected lateinit var server: MockWebServer
    protected val ha = FakeHa()
    protected val baseUrl get() = "http://127.0.0.1:${server.port}"

    @Before fun startFakeHa() {
        TestClock.install()
        ctx = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.dispatcher = ha
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        Ha.save(ctx, baseUrl, TOKEN, "light.salon")
    }

    @After fun stopFakeHa() {
        drainActions()
        server.shutdown()
        TestClock.restore()
    }

    protected fun prefs() = ctx.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)

    protected fun dl(ms: Long = Budget.ACTION_MS) = Deadline.after(ms)

    protected fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    protected fun elapsedMs(block: () -> Unit): Long {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / 1_000_000L
    }

    companion object {
        const val TOKEN = "test-token"

        /** Waits until every job queued on the shared [Actions] executor has finished. */
        fun drainActions() {
            val f = Actions::class.java.getDeclaredField("exec").apply { isAccessible = true }
            (f.get(Actions) as ExecutorService).submit {}.get(20, TimeUnit.SECONDS)
        }
    }
}

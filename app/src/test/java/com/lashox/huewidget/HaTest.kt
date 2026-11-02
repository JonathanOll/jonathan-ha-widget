package com.lashox.huewidget

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ConnectException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HaTest : FakeHaTest() {

    private inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw AssertionError("expected ${T::class.java.simpleName}, got $e", e)
        }
        fail("expected ${T::class.java.simpleName}, nothing thrown")
        throw IllegalStateException()
    }

    // ---- service calls ----

    @Test fun toggle_postsServiceWithBearerTokenAndJsonBody() {
        Ha.toggle(ctx, dl())
        val r = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("POST", r.method)
        assertEquals("/api/services/light/toggle", r.path)
        assertEquals("Bearer $TOKEN", r.getHeader("Authorization"))
        assertEquals("application/json", r.getHeader("Content-Type"))
        assertEquals("light.salon", JSONObject(r.body.readUtf8()).getString("entity_id"))
        assertEquals(1, server.requestCount)
    }

    @Test fun save_trimsUrlSlashAndToken() {
        Ha.save(ctx, "  $baseUrl/  ", "  $TOKEN  ", " light.salon ")
        assertEquals(baseUrl, Ha.url(ctx))
        assertEquals(TOKEN, Ha.token(ctx))
        assertEquals("light.salon", Ha.entity(ctx))
        Ha.toggle(ctx, dl())
        val r = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/services/light/toggle", r.path)
        assertEquals("Bearer $TOKEN", r.getHeader("Authorization"))
    }

    @Test fun power_usesGenericHomeassistantService() {
        Ha.power(ctx, "switch.tv", true, dl())
        Ha.power(ctx, "switch.tv", false, dl())
        assertEquals(listOf(Call("homeassistant/turn_on", "switch.tv"), Call("homeassistant/turn_off", "switch.tv")), ha.calls)
    }

    @Test fun trigger_picksServiceFromDomain() {
        for (id in listOf("button.pc_on", "input_button.pc_on", "script.pc_on", "scene.soir", "switch.pc")) Ha.trigger(ctx, id, dl())
        assertEquals(
            listOf(
                Call("button/press", "button.pc_on"),
                Call("input_button/press", "input_button.pc_on"),
                Call("script/turn_on", "script.pc_on"),
                Call("scene/turn_on", "scene.soir"),
                Call("homeassistant/turn_on", "switch.pc"),
            ),
            ha.calls
        )
    }

    @Test fun cover_usesConfiguredCover() {
        Ha.saveCover(ctx, " cover.salon ")
        for (s in listOf("open_cover", "stop_cover", "close_cover")) Ha.cover(ctx, s, dl())
        assertEquals(
            listOf("cover/open_cover", "cover/stop_cover", "cover/close_cover").map { Call(it, "cover.salon") },
            ha.calls
        )
    }

    @Test fun service_non2xx_throwsHaHttpExceptionWithCode() {
        for (code in listOf(400, 401, 403, 404, 500, 502, 503)) {
            ha.serviceOverride = { MockResponse().setResponseCode(code).setBody("{\"message\":\"x\"}") }
            val e = expectThrows<HaHttpException> { Ha.toggle(ctx, dl()) }
            assertEquals(code, e.code)
        }
        // no retry on an HTTP error: one request per call
        assertEquals(7, server.requestCount)
    }

    @Test fun service_2xxWithEmptyBody_ok() {
        ha.serviceOverride = { MockResponse().setResponseCode(204) }
        Ha.toggle(ctx, dl())
        assertEquals(1, server.requestCount)
    }

    // ---- state reads ----

    @Test fun isOn_parsesStates() {
        val cases = mapOf("on" to true, "off" to false, "home" to true, "not_home" to false, "unavailable" to null, "unknown" to null, "" to null)
        for ((state, expected) in cases) {
            ha.states["switch.tv"] = state
            assertEquals("state '$state'", expected, Ha.isOn(ctx, "switch.tv", dl()))
        }
        val r = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("GET", r.method)
        assertEquals("/api/states/switch.tv", r.path)
        assertEquals("Bearer $TOKEN", r.getHeader("Authorization"))
    }

    @Test fun isOn_errorsAreUnknown() {
        assertNull("404", Ha.isOn(ctx, "switch.missing", dl()))
        for (code in listOf(401, 403, 500)) {
            ha.stateOverride["switch.tv"] = { MockResponse().setResponseCode(code) }
            assertNull("HTTP $code", Ha.isOn(ctx, "switch.tv", dl()))
        }
        ha.stateOverride["switch.tv"] = { MockResponse().setBody("not json") }
        assertNull("bad body", Ha.isOn(ctx, "switch.tv", dl()))
        ha.stateOverride["switch.tv"] = { MockResponse().setBody("{\"entity_id\":\"switch.tv\"}") }
        assertNull("no state field", Ha.isOn(ctx, "switch.tv", dl()))
    }

    @Test fun stateRead_urlEncodesEntityId() {
        ha.states["sensor.température salon"] = "on"
        assertEquals(true, Ha.isOn(ctx, "sensor.température salon", dl()))
        assertEquals("/api/states/sensor.temp%C3%A9rature%20salon", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
        Ha.isOn(ctx, "light.a/b?c", dl())
        assertEquals("/api/states/light.a%2Fb%3Fc", server.takeRequest(5, TimeUnit.SECONDS)!!.path)
    }

    @Test fun isAlive_mapping() {
        ha.states["sensor.pc_cpu"] = "12.5"
        assertEquals(true, Ha.isAlive(ctx, "sensor.pc_cpu", dl()))
        for (s in listOf("unavailable", "unknown", "")) {
            ha.states["sensor.pc_cpu"] = s
            assertEquals("state '$s'", false, Ha.isAlive(ctx, "sensor.pc_cpu", dl()))
        }
        assertEquals("404 = entity missing = off", false, Ha.isAlive(ctx, "sensor.missing", dl()))
        for (code in listOf(401, 403, 500, 503)) {
            ha.stateOverride["sensor.pc_cpu"] = { MockResponse().setResponseCode(code) }
            assertNull("HTTP $code", Ha.isAlive(ctx, "sensor.pc_cpu", dl()))
        }
    }

    @Test fun isAlive_networkError_isUnknown() {
        server.shutdown()
        assertNull(Ha.isAlive(ctx, "sensor.pc_cpu", dl()))
        assertNull(Ha.isOn(ctx, "switch.tv", dl()))
        assertNull(Ha.info(ctx, "sensor.chambre_temperature", dl()))
    }

    @Test fun info_returnsStateAndFriendlyName() {
        ha.states["sensor.chambre_temperature"] = "21.5"
        ha.names["sensor.chambre_temperature"] = "Chambre"
        assertEquals("21.5" to "Chambre", Ha.info(ctx, "sensor.chambre_temperature", dl()))
        ha.stateOverride["sensor.noname"] = { MockResponse().setBody("{\"state\":\"40\"}") }
        assertEquals("40" to "", Ha.info(ctx, "sensor.noname", dl()))
    }

    @Test fun info_unavailableOrErrorIsNull() {
        for (s in listOf("unavailable", "unknown", "")) {
            ha.states["sensor.chambre_temperature"] = s
            assertNull("state '$s'", Ha.info(ctx, "sensor.chambre_temperature", dl()))
        }
        assertNull("404", Ha.info(ctx, "sensor.missing", dl()))
        ha.stateOverride["sensor.x"] = { MockResponse().setResponseCode(500) }
        assertNull("500", Ha.info(ctx, "sensor.x", dl()))
        ha.stateOverride["sensor.x"] = { MockResponse().setBody("<html>") }
        assertNull("bad body", Ha.info(ctx, "sensor.x", dl()))
    }

    // ---- deadline ----

    @Test fun slowServer_readTimesOutWithinBudget() {
        ha.states["switch.tv"] = "on"
        ha.stateOverride["switch.tv"] = { MockResponse().setBody("{\"state\":\"on\"}").setHeadersDelay(4, TimeUnit.SECONDS) }
        var v: Boolean? = true
        val ms = elapsedMs { v = Ha.isOn(ctx, "switch.tv", dl(1000)) }
        assertNull(v)
        assertTrue("took $ms ms", ms in 700..2500)
    }

    @Test fun slowServer_serviceTimesOutWithinBudget_noRetry() {
        ha.serviceOverride = { MockResponse().setHeadersDelay(4, TimeUnit.SECONDS) }
        val ms = elapsedMs { expectThrows<SocketTimeoutException> { Ha.toggle(ctx, dl(1200)) } }
        assertTrue("took $ms ms", ms in 900..2700)
        Thread.sleep(200)
        assertEquals("a read timeout is not retried", 1, server.requestCount)
    }

    @Test fun exhaustedBudget_noRequestSent() {
        val ms = elapsedMs { expectThrows<SocketTimeoutException> { Ha.toggle(ctx, dl(Budget.MIN_REQUEST_MS - 1)) } }
        assertTrue(ms < 500)
        assertNull(Ha.isOn(ctx, "switch.tv", dl(100)))
        assertNull(Ha.isAlive(ctx, "sensor.pc_cpu", dl(0)))
        assertEquals(0, server.requestCount)
    }

    @Test fun connectionRefused_throwsConnectException() {
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        Ha.save(ctx, "http://127.0.0.1:$port", TOKEN, "light.salon")
        val ms = elapsedMs { expectThrows<ConnectException> { Ha.toggle(ctx, dl()) } }
        assertTrue("took $ms ms", ms < Budget.ACTION_MS)
        assertNull(Ha.isOn(ctx, "light.salon", dl()))
    }

    // ---- security ----

    @Test fun publicHttpUrl_refusedBeforeAnyRequest() {
        Ha.save(ctx, "http://ha.example.com:8123", TOKEN, "light.salon")
        expectThrows<InsecureUrlException> { Ha.toggle(ctx, dl()) }
        expectThrows<InsecureUrlException> { Ha.power(ctx, "switch.tv", true, dl()) }
        assertNull(Ha.isOn(ctx, "light.salon", dl()))
        assertNull(Ha.isAlive(ctx, "sensor.pc_cpu", dl()))
        assertTrue(Ha.fetchEntities("http://ha.example.com:8123", TOKEN).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test fun redirect_notFollowed_tokenNotForwarded() {
        val other = MockWebServer()
        other.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            val target = "http://127.0.0.1:${other.port}/api/services/light/toggle"
            ha.serviceOverride = { MockResponse().setResponseCode(302).setHeader("Location", target) }
            val e = expectThrows<HaHttpException> { Ha.toggle(ctx, dl()) }
            assertEquals(302, e.code)
            ha.stateOverride["switch.tv"] = { MockResponse().setResponseCode(301).setHeader("Location", target) }
            assertNull(Ha.isOn(ctx, "switch.tv", dl()))
            ha.states["switch.tv"] = "on"
            assertTrue(Ha.fetchEntities(baseUrl, TOKEN).isNotEmpty())
            assertEquals(0, other.requestCount)
        } finally {
            other.shutdown()
        }
    }

    // ---- fetchEntities ----

    @Test fun fetchEntities_parsesAndSorts() {
        ha.states["switch.tv"] = "off"
        ha.states["light.salon"] = "on"
        ha.names["light.salon"] = "Salon"
        val list = Ha.fetchEntities(" $baseUrl/ ", " $TOKEN ")
        assertEquals(listOf("light.salon" to "Salon", "switch.tv" to ""), list)
        val r = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/api/states", r.path)
        assertEquals("Bearer $TOKEN", r.getHeader("Authorization"))
    }

    @Test fun fetchEntities_errorsGiveEmptyList() {
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            var n = 0
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest) = when (n++) {
                0 -> MockResponse().setResponseCode(401)
                1 -> MockResponse().setBody("{}")
                else -> MockResponse().setResponseCode(500)
            }
        }
        assertTrue(Ha.fetchEntities(baseUrl, "wrong-token").isEmpty())
        assertTrue(Ha.fetchEntities(baseUrl, TOKEN).isEmpty())
        assertTrue(Ha.fetchEntities(baseUrl, TOKEN).isEmpty())
        assertTrue(Ha.fetchEntities("not a url", TOKEN).isEmpty())
        assertFalse(server.requestCount > 3)
    }

    @Test fun httpExceptionMessage() {
        assertEquals("HTTP 404", HaHttpException(404).message)
    }
}

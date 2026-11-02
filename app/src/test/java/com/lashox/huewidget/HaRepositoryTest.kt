package com.lashox.huewidget

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class HaRepositoryTest : FakeHaTest() {

    private fun configureAll() {
        Ha.saveTv(ctx, "switch.tv", "switch.led_tv")
        Ha.saveMisc(ctx, "button.pc_on", "button.pc_off", "sensor.pc_cpu", "sensor.chambre_temperature", "sensor.chambre_humidity")
    }

    private fun setStates(vararg s: Pair<String, String>) = s.forEach { (k, v) -> ha.states[k] = v }

    // ---- fetchAll ----

    @Test fun fetchAll_writesEveryCacheKey() {
        configureAll()
        setStates(
            "light.salon" to "on", "switch.tv" to "off", "switch.led_tv" to "on", "sensor.pc_cpu" to "12",
            "sensor.chambre_temperature" to "21.5", "sensor.chambre_humidity" to "40"
        )
        ha.names["sensor.chambre_temperature"] = "Chambre"
        HaRepository.fetchAll(ctx, dl())
        val p = prefs()
        assertEquals(true, p.getBoolean(Keys.CACHE_LIGHT, false))
        assertEquals(false, p.getBoolean(Keys.CACHE_TV, true))
        assertEquals(true, p.getBoolean(Keys.CACHE_LED, false))
        assertEquals(true, p.getBoolean(Keys.CACHE_PC, false))
        assertEquals("21.5", p.getString(Keys.CACHE_TEMP, null))
        assertEquals("Chambre", p.getString(Keys.CACHE_LABEL, null))
        assertEquals("40", p.getString(Keys.CACHE_HUM, null))
        assertEquals(6, server.requestCount)
    }

    @Test fun fetchAll_unknownOrFailedReadsKeepPreviousCache() {
        configureAll()
        setStates(
            "light.salon" to "on", "switch.tv" to "off", "switch.led_tv" to "on", "sensor.pc_cpu" to "12",
            "sensor.chambre_temperature" to "21.5", "sensor.chambre_humidity" to "40"
        )
        ha.names["sensor.chambre_temperature"] = "Chambre"
        HaRepository.fetchAll(ctx, dl())

        setStates("light.salon" to "unavailable", "switch.led_tv" to "unknown", "sensor.chambre_temperature" to "unavailable")
        ha.stateOverride["switch.tv"] = { MockResponse().setResponseCode(500) }
        ha.stateOverride["sensor.pc_cpu"] = { MockResponse().setResponseCode(401) }
        ha.stateOverride["sensor.chambre_humidity"] = { MockResponse().setResponseCode(503) }
        HaRepository.fetchAll(ctx, dl())

        val p = prefs()
        assertEquals(true, p.getBoolean(Keys.CACHE_LIGHT, false))
        assertEquals(false, p.getBoolean(Keys.CACHE_TV, true))
        assertEquals(true, p.getBoolean(Keys.CACHE_LED, false))
        assertEquals(true, p.getBoolean(Keys.CACHE_PC, false))
        assertEquals("21.5", p.getString(Keys.CACHE_TEMP, null))
        assertEquals("Chambre", p.getString(Keys.CACHE_LABEL, null))
        assertEquals("40", p.getString(Keys.CACHE_HUM, null))
    }

    @Test fun fetchAll_unreachableKeepsCache() {
        configureAll()
        prefs().edit().putBoolean(Keys.CACHE_LIGHT, true).putString(Keys.CACHE_TEMP, "19.0").commit()
        server.shutdown()
        HaRepository.fetchAll(ctx, dl())
        assertTrue(prefs().getBoolean(Keys.CACHE_LIGHT, false))
        assertEquals("19.0", prefs().getString(Keys.CACHE_TEMP, null))
    }

    @Test fun fetchAll_pcSensorUnavailableMeansOff() {
        configureAll()
        prefs().edit().putBoolean(Keys.CACHE_PC, true).commit()
        setStates("sensor.pc_cpu" to "unavailable")
        HaRepository.fetchAll(ctx, dl())
        assertFalse(prefs().getBoolean(Keys.CACHE_PC, true))
    }

    @Test fun fetchAll_unconfiguredEntitiesAreNotRead() {
        Ha.save(ctx, baseUrl, TOKEN, "")
        HaRepository.fetchAll(ctx, dl())
        assertEquals(0, server.requestCount)
        assertFalse(prefs().contains(Keys.CACHE_LIGHT))
        assertFalse(prefs().contains(Keys.CACHE_TEMP))
    }

    // ---- TV / LED ----

    private fun tvCase(action: TvAction, tv: String, led: String, expected: Set<Call>, tvAfter: Boolean, ledAfter: Boolean) {
        ha.calls.clear()
        setStates("switch.tv" to tv, "switch.led_tv" to led)
        HaRepository.tv(ctx, action, dl())
        val label = "$action tv=$tv led=$led"
        assertEquals(label, expected, ha.calls.toSet())
        assertEquals(label, expected.size, ha.calls.size)
        assertEquals("$label cache tv", tvAfter, prefs().getBoolean(Keys.CACHE_TV, !tvAfter))
        assertEquals("$label cache led", ledAfter, prefs().getBoolean(Keys.CACHE_LED, !ledAfter))
    }

    private fun on(id: String) = Call("homeassistant/turn_on", id)
    private fun off(id: String) = Call("homeassistant/turn_off", id)

    @Test fun tv_all_followsTvState() {
        configureAll()
        tvCase(TvAction.ALL, "on", "on", setOf(off("switch.tv"), off("switch.led_tv")), tvAfter = false, ledAfter = false)
        tvCase(TvAction.ALL, "on", "off", setOf(off("switch.tv"), off("switch.led_tv")), tvAfter = false, ledAfter = false)
        tvCase(TvAction.ALL, "off", "on", setOf(on("switch.tv"), on("switch.led_tv")), tvAfter = true, ledAfter = true)
        tvCase(TvAction.ALL, "off", "off", setOf(on("switch.tv"), on("switch.led_tv")), tvAfter = true, ledAfter = true)
        tvCase(TvAction.ALL, "unavailable", "off", setOf(on("switch.tv"), on("switch.led_tv")), tvAfter = true, ledAfter = true)
    }

    @Test fun tv_single_flipsOnlyThatDevice() {
        configureAll()
        tvCase(TvAction.TV, "on", "on", setOf(off("switch.tv")), tvAfter = false, ledAfter = true)
        tvCase(TvAction.TV, "off", "on", setOf(on("switch.tv")), tvAfter = true, ledAfter = true)
        tvCase(TvAction.LED, "on", "on", setOf(off("switch.led_tv")), tvAfter = true, ledAfter = false)
        tvCase(TvAction.LED, "on", "off", setOf(on("switch.led_tv")), tvAfter = true, ledAfter = true)
    }

    @Test fun tv_ledNotConfigured_onlyTvSwitched() {
        Ha.saveTv(ctx, "switch.tv", "")
        setStates("switch.tv" to "off")
        HaRepository.tv(ctx, TvAction.ALL, dl())
        assertEquals(listOf(on("switch.tv")), ha.calls)
    }

    @Test fun tvTile_unknownTvState_doesNothingAndToasts() {
        configureAll()
        setStates("switch.tv" to "unavailable", "switch.led_tv" to "on")
        HaRepository.tv(ctx, TvAction.ALL, dl(), requireTvState = true)
        assertTrue(ha.calls.isEmpty())
        idleMain()
        assertEquals(ctx.getString(R.string.err_tv_state_unknown), ShadowToast.getTextOfLatestToast())
    }

    @Test fun tv_serviceError_toastsAndDoesNotPoll() {
        configureAll()
        setStates("switch.tv" to "off", "switch.led_tv" to "off")
        ha.serviceOverride = { MockResponse().setResponseCode(500) }
        val ms = elapsedMs { HaRepository.tv(ctx, TvAction.ALL, dl()) }
        assertEquals(2, ha.calls.size)
        assertTrue("no polling after a failed action ($ms ms)", ms < Budget.POLL_INTERVAL_MS)
        assertEquals(4, server.requestCount) // 2 reads + 2 calls
        idleMain()
        assertEquals(ctx.getString(R.string.err_ha_http, 500), ShadowToast.getTextOfLatestToast())
    }

    // ---- light ----

    @Test fun toggleLight_pollsUntilNewStateAndCaches() {
        setStates("light.salon" to "off")
        HaRepository.toggleLight(ctx, dl())
        assertEquals(listOf(Call("light/toggle", "light.salon")), ha.calls)
        assertTrue(prefs().getBoolean(Keys.CACHE_LIGHT, false))
    }

    @Test fun toggleLight_haNeverChanges_stopsAtDeadline() {
        setStates("light.salon" to "off")
        ha.applyServices = false
        val ms = elapsedMs { HaRepository.toggleLight(ctx, dl(2000)) }
        assertTrue("took $ms ms", ms < 2300)
        assertFalse(prefs().getBoolean(Keys.CACHE_LIGHT, true))
    }

    @Test fun toggleLight_unauthorized_toastsTokenRefused() {
        setStates("light.salon" to "off")
        ha.serviceOverride = { MockResponse().setResponseCode(401) }
        HaRepository.toggleLight(ctx, dl())
        idleMain()
        assertEquals(ctx.getString(R.string.err_token_refused), ShadowToast.getTextOfLatestToast())
    }

    // ---- cover / PC ----

    @Test fun cover_failureIsToastedNotThrown() {
        Ha.saveCover(ctx, "cover.salon")
        ha.serviceOverride = { MockResponse().setResponseCode(404) }
        HaRepository.cover(ctx, "open_cover", dl())
        assertEquals(listOf(Call("cover/open_cover", "cover.salon")), ha.calls)
        idleMain()
        assertEquals(ctx.getString(R.string.err_entity_not_found), ShadowToast.getTextOfLatestToast())
    }

    @Test fun pc_on_triggersThenPollsStateSensor() {
        configureAll()
        setStates("sensor.pc_cpu" to "unavailable")
        ha.onService = { if (it.entity == "button.pc_on") ha.states["sensor.pc_cpu"] = "5" }
        HaRepository.pc(ctx, true, dl())
        assertEquals(listOf(Call("button/press", "button.pc_on")), ha.calls)
        assertTrue(prefs().getBoolean(Keys.CACHE_PC, false))
    }

    @Test fun pc_off_usesOffEntity() {
        configureAll()
        setStates("sensor.pc_cpu" to "5")
        ha.onService = { if (it.entity == "button.pc_off") ha.states.remove("sensor.pc_cpu") }
        HaRepository.pc(ctx, false, dl())
        assertEquals(listOf(Call("button/press", "button.pc_off")), ha.calls)
        assertFalse(prefs().getBoolean(Keys.CACHE_PC, true))
    }

    @Test fun pc_noEntity_noCall() {
        HaRepository.pc(ctx, true, dl())
        HaRepository.pc(ctx, false, dl())
        assertEquals(0, server.requestCount)
    }

    // ---- parallel / polling ----

    @Test fun parallel_slowTaskYieldsNullAtDeadline() {
        val ms = elapsedMs {
            val r = HaRepository.parallel(dl(400), listOf({ 1 }, { Thread.sleep(5000); 2 }, { throw IllegalStateException() }))
            assertEquals(listOf(1, null, null), r)
        }
        assertTrue("took $ms ms", ms < 1500)
    }

    @Test fun parallel_expiredDeadline_runsNothing() {
        val ran = AtomicInteger()
        val r = HaRepository.parallel(dl(0), listOf({ ran.incrementAndGet() }, { ran.incrementAndGet() }))
        assertEquals(listOf(null, null), r)
        assertEquals(0, ran.get())
        assertTrue(HaRepository.parallel(dl(), emptyList<() -> Int>()).isEmpty())
    }

    @Test fun pollUntil_stopsAtDeadline() {
        val reads = AtomicInteger()
        val ms = elapsedMs {
            val v = HaRepository.pollUntil(dl(1300), { reads.incrementAndGet() }) { false }
            assertEquals(reads.get(), v)
        }
        assertTrue("took $ms ms", ms < 1300)
        assertTrue(reads.get() in 1..1)
    }

    @Test fun pollUntil_stopsAtMaxPolls() {
        val reads = AtomicInteger()
        HaRepository.pollUntil(dl(60_000), { reads.incrementAndGet() }) { false }
        assertEquals(Budget.POLL_MAX, reads.get())
    }

    @Test fun pollUntil_stopsWhenDone() {
        val reads = AtomicInteger()
        assertEquals(2, HaRepository.pollUntil(dl(), { reads.incrementAndGet() }) { it == 2 })
        assertNull(HaRepository.pollUntil(dl(100), { reads.incrementAndGet() }) { true })
    }

    @Test fun cached_defaultsToFalse() {
        assertFalse(HaRepository.cached(ctx, Keys.CACHE_TV))
        prefs().edit().putBoolean(Keys.CACHE_TV, true).commit()
        assertTrue(HaRepository.cached(ctx, Keys.CACHE_TV))
    }
}

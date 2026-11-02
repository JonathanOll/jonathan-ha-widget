package com.lashox.huewidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {

    // ---- parseOnOff / isRealState ----

    @Test fun parseOnOff_onStates() {
        assertEquals(true, parseOnOff("on"))
        assertEquals(true, parseOnOff("home"))
    }

    @Test fun parseOnOff_offStates() {
        assertEquals(false, parseOnOff("off"))
        assertEquals(false, parseOnOff("not_home"))
    }

    @Test fun parseOnOff_unknownStates() {
        for (s in listOf(null, "", "unknown", "unavailable", "idle", "ON", "Off", "open", "42")) {
            assertNull("state=$s", parseOnOff(s))
        }
    }

    @Test fun isRealState_values() {
        assertTrue(isRealState("12.5"))
        assertTrue(isRealState("off"))
        assertFalse(isRealState(null))
        assertFalse(isRealState(""))
        assertFalse(isRealState("unavailable"))
        assertFalse(isRealState("unknown"))
    }

    // ---- aliveFrom ----

    @Test fun aliveFrom_realValueIsOn() {
        assertEquals(true, aliveFrom(200, "12.3"))
        assertEquals(true, aliveFrom(200, "on"))
        assertEquals(true, aliveFrom(201, "off")) // any real value means the sensor exists
    }

    @Test fun aliveFrom_unavailableUnknownEmptyIsOff() {
        assertEquals(false, aliveFrom(200, "unavailable"))
        assertEquals(false, aliveFrom(200, "unknown"))
        assertEquals(false, aliveFrom(200, ""))
        assertEquals(false, aliveFrom(200, null))
    }

    @Test fun aliveFrom_missingEntityIsOff() {
        assertEquals(false, aliveFrom(404, null))
    }

    @Test fun aliveFrom_errorsAreUnknown() {
        assertNull(aliveFrom(null, null)) // network error
        assertNull(aliveFrom(401, null))
        assertNull(aliveFrom(403, null))
        assertNull(aliveFrom(500, null))
        assertNull(aliveFrom(502, null))
        assertNull(aliveFrom(503, null))
        assertNull(aliveFrom(301, null))
    }

    // ---- tvTargets ----

    private val tv = "switch.tv"
    private val led = "switch.led_tv"

    @Test fun tvTargets_allFollowsTvOnly() {
        assertEquals(listOf(tv to false, led to false), tvTargets(TvAction.ALL, tv, led, true, true))
        assertEquals(listOf(tv to false, led to false), tvTargets(TvAction.ALL, tv, led, true, false))
        assertEquals(listOf(tv to false, led to false), tvTargets(TvAction.ALL, tv, led, true, null))
        assertEquals(listOf(tv to true, led to true), tvTargets(TvAction.ALL, tv, led, false, true))
        assertEquals(listOf(tv to true, led to true), tvTargets(TvAction.ALL, tv, led, false, false))
        assertEquals(listOf(tv to true, led to true), tvTargets(TvAction.ALL, tv, led, null, true))
        assertEquals(listOf(tv to true, led to true), tvTargets(TvAction.ALL, tv, led, null, null))
    }

    @Test fun tvTargets_tvOnlyFlipsTv() {
        for (l in listOf(true, false, null)) {
            assertEquals(listOf(tv to false), tvTargets(TvAction.TV, tv, led, true, l))
            assertEquals(listOf(tv to true), tvTargets(TvAction.TV, tv, led, false, l))
            assertEquals(listOf(tv to true), tvTargets(TvAction.TV, tv, led, null, l))
        }
    }

    @Test fun tvTargets_ledOnlyFlipsLed() {
        for (t in listOf(true, false, null)) {
            assertEquals(listOf(led to false), tvTargets(TvAction.LED, tv, led, t, true))
            assertEquals(listOf(led to true), tvTargets(TvAction.LED, tv, led, t, false))
            assertEquals(listOf(led to true), tvTargets(TvAction.LED, tv, led, t, null))
        }
    }

    @Test fun tvTargets_blankEntitiesSkipped() {
        assertEquals(listOf(led to true), tvTargets(TvAction.ALL, "", led, false, false))
        assertEquals(listOf(tv to false), tvTargets(TvAction.ALL, tv, " ", true, true))
        assertEquals(emptyList<Pair<String, Boolean>>(), tvTargets(TvAction.TV, "", led, null, null))
        assertEquals(emptyList<Pair<String, Boolean>>(), tvTargets(TvAction.LED, tv, "", null, null))
        assertEquals(emptyList<Pair<String, Boolean>>(), tvTargets(TvAction.ALL, "", "", null, null))
    }

    // ---- tempBucket ----

    @Test fun tempBucket_boundaries() {
        assertEquals(TempBucket.UNKNOWN, tempBucket(null))
        assertEquals(TempBucket.COLD, tempBucket(-5.0))
        assertEquals(TempBucket.COLD, tempBucket(17.99))
        assertEquals(TempBucket.NORMAL, tempBucket(18.0))
        assertEquals(TempBucket.NORMAL, tempBucket(22.0))
        assertEquals(TempBucket.NORMAL, tempBucket(26.0))
        assertEquals(TempBucket.WARM, tempBucket(26.01))
        assertEquals(TempBucket.WARM, tempBucket(35.0))
    }

    // ---- encodeEntityId ----

    @Test fun encodeEntityId_keepsNormalIds() {
        assertEquals("light.salon", encodeEntityId("light.salon"))
        assertEquals("sensor.pc_cpu", encodeEntityId("sensor.pc_cpu"))
    }

    @Test fun encodeEntityId_escapesPathCharacters() {
        assertEquals("a%2Fb%3Fc%23d", encodeEntityId("a/b?c#d"))
        assertEquals("a%20b", encodeEntityId("a b"))
    }

    // ---- Debouncer ----

    @Test fun debouncer_ignoresRepeatWithinWindow() {
        val d = Debouncer(1000)
        assertFalse(d.hit("a", 10_000))
        assertTrue(d.hit("a", 10_500))
        assertTrue(d.hit("a", 10_999))
        assertFalse(d.hit("a", 11_000)) // exactly the window: accepted
    }

    @Test fun debouncer_rejectedHitDoesNotExtendWindow() {
        val d = Debouncer(1000)
        assertFalse(d.hit("a", 0))
        assertTrue(d.hit("a", 900))
        assertFalse(d.hit("a", 1000))
        assertTrue(d.hit("a", 1500))
    }

    @Test fun debouncer_keysAreIndependent() {
        val d = Debouncer(1000)
        assertFalse(d.hit("a", 0))
        assertFalse(d.hit("b", 100))
        assertTrue(d.hit("a", 200))
        assertTrue(d.hit("b", 300))
    }
}

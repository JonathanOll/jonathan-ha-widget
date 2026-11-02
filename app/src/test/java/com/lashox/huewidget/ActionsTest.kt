package com.lashox.huewidget

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ActionsTest : FakeHaTest() {

    private fun receive(action: String?) = ActionReceiver().onReceive(ctx, Intent(action))

    private fun configure() {
        Ha.saveCover(ctx, "cover.salon")
        Ha.saveTv(ctx, "switch.tv", "switch.led_tv")
        Ha.saveMisc(ctx, "button.pc_on", "button.pc_off", "", "", "")
    }

    @Test fun receiver_coverOpen_callsHa() {
        configure()
        receive(Act.COVER_OPEN)
        val r = server.takeRequest(10, TimeUnit.SECONDS)!!
        assertEquals("/api/services/cover/open_cover", r.path)
        assertEquals("Bearer $TOKEN", r.getHeader("Authorization"))
        drainActions()
        assertEquals(listOf(Call("cover/open_cover", "cover.salon")), ha.calls)
    }

    @Test fun receiver_debounce_dropsDuplicateWithinOneSecond() {
        configure()
        receive(Act.COVER_STOP)
        receive(Act.COVER_STOP)
        drainActions()
        assertEquals(listOf(Call("cover/stop_cover", "cover.salon")), ha.calls)

        // another action is not affected by the first one's window
        receive(Act.COVER_CLOSE)
        drainActions()
        assertEquals(2, ha.calls.size)

        // past the window the same action is accepted again
        TestClock.advance(Budget.DEBOUNCE_MS + 100)
        receive(Act.COVER_STOP)
        drainActions()
        assertEquals(
            listOf(Call("cover/stop_cover", "cover.salon"), Call("cover/close_cover", "cover.salon"), Call("cover/stop_cover", "cover.salon")),
            ha.calls
        )
    }

    @Test fun receiver_ignoresUnknownOrMissingAction() {
        configure()
        receive(null)
        receive("com.example.OTHER")
        receive(Intent.ACTION_BOOT_COMPLETED)
        drainActions()
        assertEquals(0, server.requestCount)
    }

    @Test fun receiver_refresh_readsStatesAndCaches() {
        ha.states["light.salon"] = "on"
        receive(Act.DASH_REFRESH)
        drainActions()
        assertTrue(prefs().getBoolean(Keys.CACHE_LIGHT, false))
        assertTrue(ha.calls.isEmpty())
    }

    @Test fun receiver_refreshesAreNotDebounced_butCoalesced() {
        ha.states["light.salon"] = "on"
        receive(Act.TILE_REFRESH)
        drainActions()
        ha.states["light.salon"] = "off"
        receive(Act.TILE_REFRESH)
        drainActions()
        assertFalse(prefs().getBoolean(Keys.CACHE_LIGHT, true))
    }

    @Test fun perform_mapsEveryAction() {
        configure()
        ha.states["switch.tv"] = "off"
        ha.states["switch.led_tv"] = "off"
        ha.states["light.salon"] = "off"
        val expected = listOf(
            Act.LIGHT_TOGGLE to Call("light/toggle", "light.salon"),
            Act.TILE_LIGHT to Call("light/toggle", "light.salon"),
            Act.COVER_OPEN to Call("cover/open_cover", "cover.salon"),
            Act.COVER_STOP to Call("cover/stop_cover", "cover.salon"),
            Act.COVER_CLOSE to Call("cover/close_cover", "cover.salon"),
            Act.TV_ONLY to Call("homeassistant/turn_on", "switch.tv"),
            Act.LED_ONLY to Call("homeassistant/turn_on", "switch.led_tv"),
            Act.PC_ON to Call("button/press", "button.pc_on"),
            Act.PC_OFF to Call("button/press", "button.pc_off"),
        )
        for ((action, call) in expected) {
            ha.calls.clear()
            Actions.perform(ctx, action, dl())
            assertEquals(action, listOf(call), ha.calls)
        }
        ha.calls.clear()
        Actions.perform(ctx, Act.TV_ALL, dl()) // tv and led are on now -> everything off
        assertEquals(setOf(Call("homeassistant/turn_off", "switch.tv"), Call("homeassistant/turn_off", "switch.led_tv")), ha.calls.toSet())
        ha.calls.clear()
        ha.states["switch.tv"] = "unavailable"
        Actions.perform(ctx, Act.TILE_TV, dl()) // unknown TV state: the tile does nothing
        assertTrue(ha.calls.isEmpty())
    }

    @Test fun enqueue_failingWorkStillRefreshesAndFinishes() {
        var ran = false
        Actions.enqueue(ctx, null, dl()) { _, _ -> ran = true; throw IllegalStateException("boom") }
        drainActions()
        assertTrue(ran)
    }

    @Test fun send_targetsNonExportedReceiver() {
        Actions.send(ctx, Act.TILE_LIGHT)
        val sent = shadowOf(ctx as android.app.Application).broadcastIntents.last()
        assertEquals(Act.TILE_LIGHT, sent.action)
        assertEquals(ActionReceiver::class.java.name, sent.component?.className)
    }

    @Test fun requestTileRefresh_throttled() {
        val app = shadowOf(ctx as android.app.Application)
        fun count() = app.broadcastIntents.count { it.action == Act.TILE_REFRESH }
        TestClock.advance(Budget.TILE_REFETCH_MS + 1)
        Actions.requestTileRefresh(ctx)
        Actions.requestTileRefresh(ctx)
        assertEquals(1, count())
        TestClock.advance(Budget.TILE_REFETCH_MS + 1)
        Actions.requestTileRefresh(ctx)
        assertEquals(2, count())
        assertNull(server.takeRequest(10, TimeUnit.MILLISECONDS))
    }
}

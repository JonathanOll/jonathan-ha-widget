package com.lashox.huewidget

import android.app.Application
import android.content.Context
import android.service.quicksettings.Tile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class TilesTest {
    private lateinit var ctx: Context

    @Before fun setUp() {
        TestClock.install()
        ctx = ApplicationProvider.getApplicationContext()
    }

    @After fun tearDown() = TestClock.restore()

    private fun broadcasts(action: String) = shadowOf(ctx as Application).broadcastIntents.count { it.action == action }

    @Test fun lightTile_drawsCacheAndRequestsRefresh() {
        ctx.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE).edit().putBoolean(Keys.CACHE_LIGHT, true).commit()
        val tile = Robolectric.setupService(LightTile::class.java)
        tile.onStartListening()
        val t = tile.qsTile
        assertEquals(Tile.STATE_ACTIVE, t.state)
        assertEquals(ctx.getString(R.string.tile_light), t.label.toString())
        assertEquals(ctx.getString(R.string.state_on_m), t.subtitle.toString())
        assertEquals(1, broadcasts(Act.TILE_REFRESH))
        tile.onStopListening()
    }

    @Test fun tvTile_click_flipsOptimisticallyAndSendsAction() {
        val tile = Robolectric.setupService(TvTile::class.java)
        tile.onStartListening()
        assertEquals(Tile.STATE_INACTIVE, tile.qsTile.state)
        assertEquals(ctx.getString(R.string.tile_tv_label), tile.qsTile.label.toString())
        tile.onClick()
        assertEquals(Tile.STATE_ACTIVE, tile.qsTile.state)
        assertEquals(1, broadcasts(Act.TILE_TV))
        tile.onStopListening()
    }

    @Test fun redrawActive_followsCache() {
        val tile = Robolectric.setupService(LightTile::class.java)
        tile.onStartListening()
        assertEquals(Tile.STATE_INACTIVE, tile.qsTile.state)
        ctx.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE).edit().putBoolean(Keys.CACHE_LIGHT, true).commit()
        Widgets.refreshTiles(ctx)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(Tile.STATE_ACTIVE, tile.qsTile.state)
        tile.onStopListening()
        // a stopped tile is no longer redrawn
        ctx.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE).edit().putBoolean(Keys.CACHE_LIGHT, false).commit()
        BaseTile.redrawActive()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(Tile.STATE_ACTIVE, tile.qsTile.state)
    }
}

package com.lashox.huewidget

import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Quick Settings tiles: reachable from the notification shade, including from the lock screen.
 * No work runs here: clicks and refreshes are sent to [ActionReceiver] (goAsync-backed, shared executor),
 * and the tile is drawn from the shared cache (unreachable HA -> cache unchanged -> last shown state kept).
 */
abstract class BaseTile : TileService() {
    abstract val iconRes: Int
    abstract val labelRes: Int
    abstract val clickAction: String
    abstract val cacheKey: String

    override fun onStartListening() {
        active.add(this)
        redraw()
        Actions.requestTileRefresh(applicationContext)
    }

    override fun onStopListening() {
        active.remove(this)
    }

    override fun onDestroy() {
        active.remove(this)
        super.onDestroy()
    }

    override fun onClick() {
        // optimistic flip so the tile reacts instantly; the real state is redrawn once the action ends
        val wasOn = qsTile?.state == Tile.STATE_ACTIVE
        qsTile?.let { style(it, !wasOn); it.updateTile() }
        Actions.send(applicationContext, clickAction)
    }

    private fun redraw() {
        qsTile?.let { style(it, HaRepository.cached(applicationContext, cacheKey)); it.updateTile() }
    }

    private fun style(t: Tile, on: Boolean) {
        t.label = getString(labelRes)
        t.icon = Icon.createWithResource(this, iconRes)
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = getString(if (on) R.string.state_on_m else R.string.state_off_m)
    }

    companion object {
        private val active = CopyOnWriteArraySet<BaseTile>()
        private val main = Handler(Looper.getMainLooper())

        /** Redraw tiles currently visible in the shade from the cache. Safe from any thread. */
        fun redrawActive() {
            main.post { active.forEach { it.redraw() } }
        }
    }
}

class LightTile : BaseTile() {
    override val iconRes = R.drawable.ic_bulb
    override val labelRes = R.string.tile_light
    override val clickAction = Act.TILE_LIGHT
    override val cacheKey = Keys.CACHE_LIGHT
}

/** Tile state follows the TV only (LEDs are secondary). Click decides from a fresh TV state read. */
class TvTile : BaseTile() {
    override val iconRes = R.drawable.ic_tv
    override val labelRes = R.string.tile_tv_label
    override val clickAction = Act.TILE_TV
    override val cacheKey = Keys.CACHE_TV
}

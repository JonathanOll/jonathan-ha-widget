package com.lashox.huewidget

import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import kotlin.concurrent.thread

/** Quick Settings tiles: reachable from the notification shade, including from the lock screen. */
abstract class BaseTile : TileService() {
    abstract val iconRes: Int
    abstract val labelText: String
    abstract fun read(c: Context): Boolean?
    abstract fun toggle(c: Context, currentlyOn: Boolean)

    override fun onStartListening() = refresh(0)

    override fun onClick() {
        val c = applicationContext
        // optimistic flip so the tile reacts instantly
        val wasOn = qsTile?.state == Tile.STATE_ACTIVE
        qsTile?.let { style(it, !wasOn); it.updateTile() }
        thread {
            try { toggle(c, wasOn) } catch (e: Exception) { Log.e("HueWidget", "tile toggle failed", e) }
            refresh(1200)
        }
    }

    private fun style(t: Tile, on: Boolean) {
        t.label = labelText
        t.icon = Icon.createWithResource(this, iconRes)
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = if (on) "Allum\u00e9" else "\u00c9teint"
    }

    private fun refresh(delayMs: Long) {
        val c = applicationContext
        thread {
            if (delayMs > 0) Thread.sleep(delayMs)
            val on = read(c)
            Handler(Looper.getMainLooper()).post {
                qsTile?.let { style(it, on == true); it.updateTile() }
            }
        }
    }
}

class LightTile : BaseTile() {
    override val iconRes = R.drawable.ic_bulb
    override val labelText = "Lumi\u00e8re"
    override fun read(c: Context) = Ha.isOn(c)
    override fun toggle(c: Context, currentlyOn: Boolean) = Ha.toggle(c)
}

class TvTile : BaseTile() {
    override val iconRes = R.drawable.ic_tv
    override val labelText = "T\u00e9l\u00e9 + LED"
    private fun ids(c: Context) = listOf(Ha.tvEntity(c), Ha.ledEntity(c)).filter { it.isNotBlank() }

    override fun read(c: Context): Boolean? {
        // tile state follows the TV only (LEDs are secondary)
        return Ha.tvEntity(c).takeIf { it.isNotBlank() }?.let { Ha.isOn(c, it) }
    }

    override fun toggle(c: Context, currentlyOn: Boolean) {
        ids(c).map { id -> thread { try { Ha.power(c, id, !currentlyOn) } catch (e: Exception) { Log.e("HueWidget", "tile power $id", e) } } }
            .forEach { it.join() }
    }
}

package com.lashox.huewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService

/** Redraws every widget and tile from the shared cache, and builds the click PendingIntents. */
object Widgets {

    /** Redraw all widget providers from the cache and ask the tiles to refresh. Safe from any thread. */
    fun refreshAll(c: Context) {
        val mgr = AppWidgetManager.getInstance(c)
        fun ids(cls: Class<*>) = mgr.getAppWidgetIds(ComponentName(c, cls))
        ids(LightWidget::class.java).takeIf { it.isNotEmpty() }?.let { LightWidget.render(c, mgr, it) }
        ids(CoverWidget::class.java).takeIf { it.isNotEmpty() }?.let { CoverWidget.render(c, mgr, it) }
        ids(TvWidget::class.java).takeIf { it.isNotEmpty() }?.let { TvWidget.render(c, mgr, it) }
        ids(ThermoWidget::class.java).takeIf { it.isNotEmpty() }?.let { ThermoWidget.render(c, mgr, it) }
        ids(PcWidget::class.java).takeIf { it.isNotEmpty() }?.let { PcWidget.render(c, mgr, it) }
        ids(DashWidget::class.java).takeIf { it.isNotEmpty() }?.let { DashWidget.render(c, mgr, it) }
        refreshTiles(c)
    }

    fun refreshTiles(c: Context) {
        BaseTile.redrawActive()
        for (cls in listOf(LightTile::class.java, TvTile::class.java)) {
            try { TileService.requestListeningState(c, ComponentName(c, cls)) }
            catch (e: Exception) { HaLog.w("requestListeningState failed", e) }
        }
    }

    /** Click -> non-exported [ActionReceiver]. [code] must be unique per button. */
    fun action(c: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, code, Intent(c, ActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Click -> PC confirmation dialog. */
    fun confirmPc(c: Context, code: Int): PendingIntent =
        PendingIntent.getActivity(
            c, code, Intent(c, ConfirmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** "allumé" / "éteinte" / "allumées"... (lowercase, for spoken descriptions). */
    fun onOffText(c: Context, on: Boolean, feminine: Boolean = false, plural: Boolean = false): String =
        c.getString(
            when {
                feminine && plural -> if (on) R.string.onoff_on_fp else R.string.onoff_off_fp
                feminine -> if (on) R.string.onoff_on_f else R.string.onoff_off_f
                plural -> if (on) R.string.onoff_on_mp else R.string.onoff_off_mp
                else -> if (on) R.string.onoff_on_m else R.string.onoff_off_m
            }
        )

    /** "Lumière, allumée": name + state, for contentDescription. */
    fun describe(c: Context, name: String, state: String): String = c.getString(R.string.a11y_name_state, name, state)
}

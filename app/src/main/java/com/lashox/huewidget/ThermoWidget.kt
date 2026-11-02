package com.lashox.huewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.widget.RemoteViews
import java.util.Locale
import kotlin.concurrent.thread

/** Shows a temperature sensor (+ optional humidity). Tap = refresh. */
class ThermoWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids)
        val pending = goAsync()
        thread {
            try {
                fetch(context)
                render(context, mgr, ids)
            } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val mgr = AppWidgetManager.getInstance(context)
            onUpdate(context, mgr, mgr.getAppWidgetIds(ComponentName(context, ThermoWidget::class.java)))
        }
    }

    /** Fetch from HA and cache. On error the cache is kept (last known value). */
    private fun fetch(c: Context) {
        val prefs = c.getSharedPreferences("ha", Context.MODE_PRIVATE)
        val t = Ha.thermoEntity(c).takeIf { it.isNotBlank() }?.let { Ha.info(c, it) }
        val h = Ha.humEntity(c).takeIf { it.isNotBlank() }?.let { Ha.info(c, it) }
        prefs.edit().apply {
            if (t != null) {
                putString("thermo_temp", t.first)
                putString("thermo_label", t.second)
            }
            if (h != null) putString("thermo_hum", h.first)
        }.apply()
    }

    private fun render(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        val prefs = c.getSharedPreferences("ha", Context.MODE_PRIVATE)
        val temp = prefs.getString("thermo_temp", null)?.toDoubleOrNull()
        val hum = prefs.getString("thermo_hum", null)?.toDoubleOrNull()
        val label = prefs.getString("thermo_label", "")?.takeIf { it.isNotBlank() } ?: "Température"

        val color = when {
            temp == null -> Color.parseColor("#8A8D94")
            temp < 18 -> Color.parseColor("#64B5F6")
            temp > 26 -> Color.parseColor("#FF8A65")
            else -> Color.WHITE
        }
        val v = RemoteViews(c.packageName, R.layout.widget_thermo)
        v.setInt(R.id.root, "setBackgroundResource", when {
            temp == null -> R.drawable.bg_widget_off
            temp < 18 -> R.drawable.bg_widget_cold
            temp > 26 -> R.drawable.bg_widget_warm
            else -> R.drawable.bg_widget_off
        })
        v.setViewVisibility(R.id.hum_row, if (hum != null) android.view.View.VISIBLE else android.view.View.GONE)
        v.setTextViewText(R.id.label, label)
        v.setTextViewText(R.id.temp, if (temp != null) String.format(Locale.getDefault(), "%.1f°", temp) else "--")
        v.setTextColor(R.id.temp, color)
        v.setTextViewText(R.id.hum, if (hum != null) String.format(Locale.getDefault(), "%.0f %%", hum) else "")
        v.setOnClickPendingIntent(
            R.id.root,
            PendingIntent.getBroadcast(
                c, 21, Intent(c, ThermoWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        Log.d("HueWidget", "thermo render temp=$temp hum=$hum")
        mgr.updateAppWidget(ids, v)
    }

    companion object {
        const val ACTION_REFRESH = "com.lashox.huewidget.THERMO_REFRESH"
    }
}

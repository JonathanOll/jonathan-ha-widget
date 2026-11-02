package com.lashox.huewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import java.util.Locale

/** Shows a temperature sensor (+ optional humidity). Tap = refresh (via [ActionReceiver]). */
class ThermoWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids)
        Actions.requestRefresh(context, goAsync())
    }

    companion object {
        fun tempColor(temp: Double?): Int = when (tempBucket(temp)) {
            TempBucket.UNKNOWN -> Colors.GREY
            TempBucket.COLD -> Colors.COLD
            TempBucket.WARM -> Colors.WARM
            TempBucket.NORMAL -> Colors.WHITE
        }

        fun tempText(temp: Double?): String =
            if (temp != null) String.format(Locale.getDefault(), "%.1f°", temp) else "--"

        fun humText(hum: Double?): String =
            if (hum != null) String.format(Locale.getDefault(), "%.0f %%", hum) else ""

        /** Spoken description, e.g. "Salon, 21,5 degrés, humidité 40 %". */
        fun describe(c: Context, label: String, temp: Double?, hum: Double?): String {
            val t = if (temp != null) c.getString(R.string.a11y_thermo_degrees, temp) else c.getString(R.string.a11y_thermo_unknown)
            val h = if (hum != null) c.getString(R.string.a11y_thermo_hum, humText(hum)) else ""
            return c.getString(R.string.a11y_thermo, label, t) + h
        }

        /** Draws from the cache (last known values). */
        fun render(c: Context, mgr: AppWidgetManager, ids: IntArray) {
            val prefs = c.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)
            val temp = prefs.getString(Keys.CACHE_TEMP, null)?.toDoubleOrNull()
            val hum = prefs.getString(Keys.CACHE_HUM, null)?.toDoubleOrNull()
            val label = prefs.getString(Keys.CACHE_LABEL, "")?.takeIf { it.isNotBlank() } ?: c.getString(R.string.thermo_default_label)

            val v = RemoteViews(c.packageName, R.layout.widget_thermo)
            v.setInt(R.id.root, "setBackgroundResource", when (tempBucket(temp)) {
                TempBucket.COLD -> R.drawable.bg_widget_cold
                TempBucket.WARM -> R.drawable.bg_widget_warm
                else -> R.drawable.bg_widget_off
            })
            v.setViewVisibility(R.id.hum_row, if (hum != null) View.VISIBLE else View.GONE)
            v.setTextViewText(R.id.label, label)
            v.setTextViewText(R.id.temp, tempText(temp))
            v.setTextColor(R.id.temp, tempColor(temp))
            v.setTextViewText(R.id.hum, humText(hum))
            v.setContentDescription(R.id.root, describe(c, label, temp, hum))
            v.setOnClickPendingIntent(R.id.root, Widgets.action(c, Act.THERMO_REFRESH, 21))
            HaLog.d { "thermo render temp=$temp hum=$hum" }
            mgr.updateAppWidget(ids, v)
        }
    }
}

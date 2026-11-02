package com.lashox.huewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import kotlin.concurrent.thread

class LightWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids, null)
        val pending = goAsync()
        thread {
            try { render(context, mgr, ids, Ha.isOn(context)) } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        android.util.Log.d("HueWidget", "onReceive " + intent.action)
        if (intent.action == ACTION_TOGGLE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, LightWidget::class.java))
            val pending = goAsync()
            thread {
                try {
                    val before = Ha.isOn(context)
                    var ok = true
                    try { Ha.toggle(context) } catch (e: Exception) { ok = false; android.util.Log.e("HueWidget", "toggle failed", e) }
                    var after: Boolean? = null
                    if (ok) {
                        // HA/Hue can lag: poll until state differs from before (max ~3.5 s)
                        for (i in 0 until 7) {
                            Thread.sleep(500)
                            after = Ha.isOn(context)
                            if (after != null && after != before) break
                        }
                    }
                    render(context, mgr, ids, if (ok) after else null)
                } finally { pending.finish() }
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.lashox.huewidget.TOGGLE"

        fun render(c: Context, mgr: AppWidgetManager, ids: IntArray, on: Boolean?) {
            val views = RemoteViews(c.packageName, R.layout.widget)
            val prefs = c.getSharedPreferences("ha", Context.MODE_PRIVATE)
            // unknown (network error) -> keep last known state
            val state = on ?: prefs.getBoolean("last_on", false)
            if (on != null) prefs.edit().putBoolean("last_on", on).apply()
            val color = if (state) Color.parseColor("#FFC107") else Color.parseColor("#8A8D94")
            views.setInt(R.id.icon, "setColorFilter", color)
            views.setInt(R.id.root, "setBackgroundResource",
                if (state) R.drawable.bg_widget_on else R.drawable.bg_widget_off)
            val i = Intent(c, LightWidget::class.java).setAction(ACTION_TOGGLE)
            val pi = PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.root, pi)
            mgr.updateAppWidget(ids, views)
        }
    }
}

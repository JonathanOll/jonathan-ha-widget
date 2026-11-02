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
import kotlin.concurrent.thread

/** Big button = TV + LEDs together. Small buttons = TV only / LEDs only. */
class TvWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids, null, null)
        val pending = goAsync()
        thread {
            try {
                val (tv, led) = states(context)
                render(context, mgr, ids, tv, led)
            } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action != ACTION_ALL && action != ACTION_TV && action != ACTION_LED) return
        Log.d("HueWidget", "tv action $action")
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, TvWidget::class.java))
        val pending = goAsync()
        thread {
            try {
                val tvId = Ha.tvEntity(context)
                val ledId = Ha.ledEntity(context)
                val before = states(context)
                val targets: List<Pair<String, Boolean>> = when (action) {
                    ACTION_ALL -> {
                        // TV on -> turn everything off, otherwise turn everything on
                        val on = before.first != true
                        listOf(tvId to on, ledId to on)
                    }
                    ACTION_TV -> listOf(tvId to (before.first != true))
                    else -> listOf(ledId to (before.second != true))
                }.filter { it.first.isNotBlank() }

                targets.map { (id, on) ->
                    thread {
                        try { Ha.power(context, id, on) }
                        catch (e: Exception) { Log.e("HueWidget", "power failed $id", e) }
                    }
                }.forEach { it.join() }

                var after = before
                for (i in 0 until 7) {
                    Thread.sleep(500)
                    after = states(context)
                    val done = targets.all { (id, on) -> (if (id == tvId) after.first else after.second) == on }
                    if (done) break
                }
                render(context, mgr, ids, after.first, after.second)
            } finally { pending.finish() }
        }
    }

    private fun states(c: Context): Pair<Boolean?, Boolean?> {
        val tv = Ha.tvEntity(c).takeIf { it.isNotBlank() }?.let { Ha.isOn(c, it) }
        val led = Ha.ledEntity(c).takeIf { it.isNotBlank() }?.let { Ha.isOn(c, it) }
        return tv to led
    }

    private fun pi(c: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, code, Intent(c, TvWidget::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun render(c: Context, mgr: AppWidgetManager, ids: IntArray, tv: Boolean?, led: Boolean?) {
        val prefs = c.getSharedPreferences("ha", Context.MODE_PRIVATE)
        // unknown (network error) -> keep last known state
        val tvOn = tv ?: prefs.getBoolean("tv_on", false)
        val ledOn = led ?: prefs.getBoolean("led_on", false)
        prefs.edit().apply {
            if (tv != null) putBoolean("tv_on", tv)
            if (led != null) putBoolean("led_on", led)
        }.apply()
        val any = tvOn // main button + background follow the TV only, not the LEDs
        val amber = Color.parseColor("#FFC107")
        val grey = Color.parseColor("#8A8D94")

        val v = RemoteViews(c.packageName, R.layout.widget_tv)
        v.setInt(R.id.root, "setBackgroundResource", if (any) R.drawable.bg_widget_on else R.drawable.bg_widget_off)
        v.setInt(R.id.i_all, "setColorFilter", if (any) amber else grey)
        v.setTextColor(R.id.l_all, if (any) amber else grey)
        v.setInt(R.id.btn_all, "setBackgroundResource", if (any) R.drawable.bg_btn_on else R.drawable.bg_btn)
        v.setInt(R.id.i_tv, "setColorFilter", if (tvOn) amber else grey)
        v.setTextColor(R.id.l_tv, if (tvOn) amber else grey)
        v.setInt(R.id.btn_tv, "setBackgroundResource", if (tvOn) R.drawable.bg_btn_on else R.drawable.bg_btn)
        v.setInt(R.id.i_led, "setColorFilter", if (ledOn) amber else grey)
        v.setTextColor(R.id.l_led, if (ledOn) amber else grey)
        v.setInt(R.id.btn_led, "setBackgroundResource", if (ledOn) R.drawable.bg_btn_on else R.drawable.bg_btn)
        v.setOnClickPendingIntent(R.id.btn_all, pi(c, ACTION_ALL, 11))
        v.setOnClickPendingIntent(R.id.btn_tv, pi(c, ACTION_TV, 12))
        v.setOnClickPendingIntent(R.id.btn_led, pi(c, ACTION_LED, 13))
        mgr.updateAppWidget(ids, v)
    }

    companion object {
        const val ACTION_ALL = "com.lashox.huewidget.TV_ALL"
        const val ACTION_TV = "com.lashox.huewidget.TV_ONLY"
        const val ACTION_LED = "com.lashox.huewidget.LED_ONLY"
    }
}

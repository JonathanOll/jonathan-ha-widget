package com.lashox.huewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Big button = TV + LEDs together. Small buttons = TV only / LEDs only. Clicks go to [ActionReceiver]. */
class TvWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids)
        Actions.requestRefresh(context, goAsync())
    }

    companion object {
        /** Draws from the cache (last known states). */
        fun render(c: Context, mgr: AppWidgetManager, ids: IntArray) {
            val tvOn = HaRepository.cached(c, Keys.CACHE_TV)
            val ledOn = HaRepository.cached(c, Keys.CACHE_LED)
            val any = tvOn // main button + background follow the TV only, not the LEDs
            val amber = Colors.AMBER
            val grey = Colors.GREY

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
            v.setContentDescription(R.id.btn_all, c.getString(R.string.a11y_tv_all, Widgets.onOffText(c, any, feminine = true)))
            v.setContentDescription(R.id.btn_tv, Widgets.describe(c, c.getString(R.string.tv), Widgets.onOffText(c, tvOn, feminine = true)))
            v.setContentDescription(R.id.btn_led,
                Widgets.describe(c, c.getString(R.string.led), Widgets.onOffText(c, ledOn, feminine = true, plural = true)))
            v.setOnClickPendingIntent(R.id.btn_all, Widgets.action(c, Act.TV_ALL, 11))
            v.setOnClickPendingIntent(R.id.btn_tv, Widgets.action(c, Act.TV_ONLY, 12))
            v.setOnClickPendingIntent(R.id.btn_led, Widgets.action(c, Act.LED_ONLY, 13))
            mgr.updateAppWidget(ids, v)
        }
    }
}

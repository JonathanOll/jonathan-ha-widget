package com.lashox.huewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Light toggle. Clicks go to [ActionReceiver]; this provider only handles APPWIDGET_UPDATE. */
class LightWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids)
        Actions.requestRefresh(context, goAsync())
    }

    companion object {
        /** Draws from the cache (last known state). */
        fun render(c: Context, mgr: AppWidgetManager, ids: IntArray) {
            val state = HaRepository.cached(c, Keys.CACHE_LIGHT)
            val views = RemoteViews(c.packageName, R.layout.widget)
            views.setInt(R.id.icon, "setColorFilter", if (state) Colors.AMBER else Colors.GREY)
            views.setInt(R.id.icon, "setBackgroundResource", if (state) R.drawable.bg_pill_on else R.drawable.bg_pill_off)
            views.setInt(R.id.root, "setBackgroundResource",
                if (state) R.drawable.bg_widget_on else R.drawable.bg_widget_off)
            views.setContentDescription(R.id.root, Widgets.describe(c, c.getString(R.string.light), Widgets.onOffText(c, state, feminine = true)))
            views.setOnClickPendingIntent(R.id.root, Widgets.action(c, Act.LIGHT_TOGGLE, 0))
            mgr.updateAppWidget(ids, views)
        }
    }
}

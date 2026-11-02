package com.lashox.huewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Cover open / stop / close. Clicks go to [ActionReceiver]; this provider only handles APPWIDGET_UPDATE. */
class CoverWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) = render(context, mgr, ids)

    companion object {
        fun render(c: Context, mgr: AppWidgetManager, ids: IntArray) {
            val views = RemoteViews(c.packageName, R.layout.widget_cover)
            views.setOnClickPendingIntent(R.id.btn_up, Widgets.action(c, Act.COVER_OPEN, 1))
            views.setOnClickPendingIntent(R.id.btn_stop, Widgets.action(c, Act.COVER_STOP, 2))
            views.setOnClickPendingIntent(R.id.btn_down, Widgets.action(c, Act.COVER_CLOSE, 3))
            views.setContentDescription(R.id.btn_up, c.getString(R.string.a11y_cover_open))
            views.setContentDescription(R.id.btn_stop, c.getString(R.string.a11y_cover_stop))
            views.setContentDescription(R.id.btn_down, c.getString(R.string.a11y_cover_close))
            mgr.updateAppWidget(ids, views)
        }
    }
}

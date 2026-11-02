package com.lashox.huewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlin.concurrent.thread

class CoverWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_cover)
        views.setOnClickPendingIntent(R.id.btn_up, pi(context, ACTION_OPEN, 1))
        views.setOnClickPendingIntent(R.id.btn_stop, pi(context, ACTION_STOP, 2))
        views.setOnClickPendingIntent(R.id.btn_down, pi(context, ACTION_CLOSE, 3))
        mgr.updateAppWidget(ids, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val service = when (intent.action) {
            ACTION_OPEN -> "open_cover"
            ACTION_CLOSE -> "close_cover"
            ACTION_STOP -> "stop_cover"
            else -> return
        }
        android.util.Log.d("HueWidget", "cover " + service)
        val pending = goAsync()
        thread {
            try {
                Ha.cover(context, service)
            } catch (e: Exception) {
                android.util.Log.e("HueWidget", "cover failed", e)
                Ha.toast(context, e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun pi(c: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, code, Intent(c, CoverWidget::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val ACTION_OPEN = "com.lashox.huewidget.COVER_OPEN"
        const val ACTION_CLOSE = "com.lashox.huewidget.COVER_CLOSE"
        const val ACTION_STOP = "com.lashox.huewidget.COVER_STOP"
    }
}

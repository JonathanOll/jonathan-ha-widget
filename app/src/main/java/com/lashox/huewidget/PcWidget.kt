package com.lashox.huewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import kotlin.concurrent.thread

/** Tap opens a confirmation dialog (ConfirmActivity), so the PC can't be toggled by accident. */
class PcWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids, null)
        val pending = goAsync()
        thread {
            try {
                val id = Ha.pcStateEntity(context)
                val on = if (id.isBlank()) null else Ha.isAlive(context, id)
                render(context, mgr, ids, on)
            } finally { pending.finish() }
        }
    }

    private fun render(c: Context, mgr: AppWidgetManager, ids: IntArray, on: Boolean?) {
        val prefs = c.getSharedPreferences("ha", Context.MODE_PRIVATE)
        // no state entity -> state unknown; network error -> keep last known state
        val state = if (Ha.pcStateEntity(c).isBlank()) null else on ?: prefs.getBoolean("pc_on", false)
        if (on != null) prefs.edit().putBoolean("pc_on", on).apply()

        val v = RemoteViews(c.packageName, R.layout.widget_pc)
        v.setInt(R.id.root, "setBackgroundResource", if (state == true) R.drawable.bg_widget_on else R.drawable.bg_widget_off)
        v.setInt(R.id.icon, "setBackgroundResource", if (state == true) R.drawable.bg_circle_on else R.drawable.bg_circle_off)
        v.setInt(R.id.icon, "setColorFilter", Color.parseColor(if (state == true) "#FFC107" else "#8A8D94"))
        v.setTextViewText(R.id.status, when (state) { true -> "Allumé"; false -> "Éteint"; null -> "Appuyer" })
        val open = PendingIntent.getActivity(
            c, 31, Intent(c, ConfirmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        v.setOnClickPendingIntent(R.id.root, open)
        mgr.updateAppWidget(ids, v)
    }
}

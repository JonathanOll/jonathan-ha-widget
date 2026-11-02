package com.lashox.huewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Tap opens a confirmation dialog (ConfirmActivity), so the PC can't be toggled by accident. */
class PcWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids)
        Actions.requestRefresh(context, goAsync())
    }

    companion object {
        /** PC state for display: null when no state entity is configured, else the cached state. */
        fun state(c: Context): Boolean? =
            if (Ha.pcStateEntity(c).isBlank()) null else HaRepository.cached(c, Keys.CACHE_PC)

        fun render(c: Context, mgr: AppWidgetManager, ids: IntArray) {
            val state = state(c)
            val v = RemoteViews(c.packageName, R.layout.widget_pc)
            v.setInt(R.id.root, "setBackgroundResource", if (state == true) R.drawable.bg_widget_on else R.drawable.bg_widget_off)
            v.setInt(R.id.icon, "setBackgroundResource", if (state == true) R.drawable.bg_circle_on else R.drawable.bg_circle_off)
            v.setInt(R.id.icon, "setColorFilter", if (state == true) Colors.AMBER else Colors.GREY)
            v.setTextViewText(R.id.status, c.getString(when (state) {
                true -> R.string.state_on_m; false -> R.string.state_off_m; null -> R.string.state_tap
            }))
            val name = c.getString(R.string.pc)
            v.setContentDescription(R.id.root, if (state == null) name else Widgets.describe(c, name, Widgets.onOffText(c, state)))
            v.setOnClickPendingIntent(R.id.root, Widgets.confirmPc(c, 31))
            mgr.updateAppWidget(ids, v)
        }
    }
}

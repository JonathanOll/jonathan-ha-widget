package com.lashox.huewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/** Big dashboard: thermometer, light, PC (confirm), cover, TV + LED. Clicks go to [ActionReceiver]. */
class DashWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids)
        Actions.requestRefresh(context, goAsync())
    }

    companion object {
        /** Draws from the cache (last known states). */
        fun render(c: Context, mgr: AppWidgetManager, ids: IntArray) {
            val p = c.getSharedPreferences(Keys.PREFS, Context.MODE_PRIVATE)
            val light = p.getBoolean(Keys.CACHE_LIGHT, false)
            val tv = p.getBoolean(Keys.CACHE_TV, false)
            val led = p.getBoolean(Keys.CACHE_LED, false)
            val pcState = PcWidget.state(c)
            val pcKnown = pcState != null
            val pc = pcState == true
            val temp = p.getString(Keys.CACHE_TEMP, null)?.toDoubleOrNull()
            val hum = p.getString(Keys.CACHE_HUM, null)?.toDoubleOrNull()
            val label = p.getString(Keys.CACHE_LABEL, "")?.takeIf { it.isNotBlank() } ?: ""
            val amber = Colors.AMBER
            val grey = Colors.GREY
            val any = light || tv || led || pc

            val v = RemoteViews(c.packageName, R.layout.widget_dash)
            v.setInt(R.id.root, "setBackgroundResource", if (any) R.drawable.bg_widget_on else R.drawable.bg_widget_off)

            v.setTextViewText(R.id.d_temp, ThermoWidget.tempText(temp))
            v.setTextColor(R.id.d_temp, ThermoWidget.tempColor(temp))
            v.setTextViewText(R.id.d_hum, ThermoWidget.humText(hum))
            v.setTextViewText(R.id.d_label, label)
            v.setContentDescription(
                R.id.d_thermo, c.getString(
                    R.string.a11y_thermo_refresh,
                    ThermoWidget.describe(c, label.ifBlank { c.getString(R.string.thermo_default_label) }, temp, hum)
                )
            )
            v.setOnClickPendingIntent(R.id.d_thermo, Widgets.action(c, Act.DASH_REFRESH, 40))

            fun tile(tile: Int, icon: Int, state: Int, on: Boolean, text: String, name: String) {
                v.setInt(tile, "setBackgroundResource", if (on) R.drawable.bg_btn_on else R.drawable.bg_btn)
                v.setInt(icon, "setColorFilter", if (on) amber else grey)
                v.setTextViewText(state, text)
                v.setTextColor(state, if (on) amber else grey)
                v.setContentDescription(tile, Widgets.describe(c, name, text.lowercase()))
            }
            fun s(id: Int) = c.getString(id)
            tile(R.id.t_light, R.id.i_light, R.id.s_light, light,
                s(if (light) R.string.state_on_f else R.string.state_off_f), s(R.string.light))
            tile(
                R.id.t_pc, R.id.i_pc, R.id.s_pc, pc,
                s(if (!pcKnown) R.string.state_tap else if (pc) R.string.state_on_m else R.string.state_off_m), s(R.string.pc)
            )
            tile(R.id.t_tv, R.id.i_tv, R.id.s_tv, tv,
                s(if (tv) R.string.state_on_f else R.string.state_off_f), s(R.string.tv))
            tile(R.id.t_led, R.id.i_led, R.id.s_led, led,
                s(if (led) R.string.state_on_fp else R.string.state_off_fp), s(R.string.led))
            tile(
                R.id.t_all, R.id.i_all, R.id.s_all, tv,
                s(when {
                    tv && led -> R.string.state_all_on
                    tv -> R.string.state_tv_only
                    led -> R.string.state_led_only
                    else -> R.string.state_all_off
                }),
                s(R.string.all)
            )
            // cover tiles: static, white icons
            for (id in listOf(R.id.i_up, R.id.i_stop, R.id.i_down)) v.setInt(id, "setColorFilter", Colors.COVER_ICON)
            v.setContentDescription(R.id.t_up, s(R.string.a11y_cover_open))
            v.setContentDescription(R.id.t_stop, s(R.string.a11y_cover_stop))
            v.setContentDescription(R.id.t_down, s(R.string.a11y_cover_close))

            v.setOnClickPendingIntent(R.id.t_light, Widgets.action(c, Act.LIGHT_TOGGLE, 41))
            v.setOnClickPendingIntent(R.id.t_up, Widgets.action(c, Act.COVER_OPEN, 42))
            v.setOnClickPendingIntent(R.id.t_stop, Widgets.action(c, Act.COVER_STOP, 43))
            v.setOnClickPendingIntent(R.id.t_down, Widgets.action(c, Act.COVER_CLOSE, 44))
            v.setOnClickPendingIntent(R.id.t_all, Widgets.action(c, Act.TV_ALL, 45))
            v.setOnClickPendingIntent(R.id.t_tv, Widgets.action(c, Act.TV_ONLY, 46))
            v.setOnClickPendingIntent(R.id.t_led, Widgets.action(c, Act.LED_ONLY, 47))
            v.setOnClickPendingIntent(R.id.t_pc, Widgets.confirmPc(c, 48))
            mgr.updateAppWidget(ids, v)
        }
    }
}

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
import java.util.Locale
import kotlin.concurrent.thread

/** Big dashboard: thermometer, light, PC (confirm), cover, TV + LED. */
class DashWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids)
        val pending = goAsync()
        thread {
            try {
                fetchAll(context)
                render(context, mgr, ids)
            } finally { pending.finish() }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in ACTIONS) return
        Log.d("HueWidget", "dash $action")
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, DashWidget::class.java))
        val pending = goAsync()
        thread {
            try {
                handle(context, action)
                fetchAll(context)
                render(context, mgr, ids)
            } finally { pending.finish() }
        }
    }

    private fun safe(what: String, block: () -> Unit) {
        try { block() } catch (e: Exception) { Log.e("HueWidget", "$what failed", e) }
    }

    private fun handle(c: Context, action: String) {
        val lightId = Ha.entity(c)
        val tvId = Ha.tvEntity(c)
        val ledId = Ha.ledEntity(c)
        when (action) {
            ACTION_LIGHT -> {
                val before = Ha.isOn(c, lightId)
                safe("toggle") { Ha.toggle(c) }
                waitFor(c, listOf(lightId to (before != true)))
            }
            ACTION_UP -> safe("open") { Ha.cover(c, "open_cover") }
            ACTION_STOP -> safe("stop") { Ha.cover(c, "stop_cover") }
            ACTION_DOWN -> safe("close") { Ha.cover(c, "close_cover") }
            ACTION_ALL, ACTION_TV, ACTION_LED -> {
                val tv = if (tvId.isBlank()) null else Ha.isOn(c, tvId)
                val led = if (ledId.isBlank()) null else Ha.isOn(c, ledId)
                val targets = when (action) {
                    ACTION_ALL -> {
                        val on = tv != true
                        listOf(tvId to on, ledId to on)
                    }
                    ACTION_TV -> listOf(tvId to (tv != true))
                    else -> listOf(ledId to (led != true))
                }.filter { it.first.isNotBlank() }
                targets.map { (id, on) -> thread { safe("power $id") { Ha.power(c, id, on) } } }.forEach { it.join() }
                waitFor(c, targets)
            }
        }
    }

    private fun waitFor(c: Context, targets: List<Pair<String, Boolean>>) {
        for (i in 0 until 7) {
            Thread.sleep(500)
            if (targets.all { (id, on) -> Ha.isOn(c, id) == on }) break
        }
    }

    /** Fetch every configured entity and cache; on error the cache is kept. */
    private fun fetchAll(c: Context) {
        fun state(id: String) = if (id.isBlank()) null else Ha.isOn(c, id)
        val light = state(Ha.entity(c))
        val tv = state(Ha.tvEntity(c))
        val led = state(Ha.ledEntity(c))
        val pc = Ha.pcStateEntity(c).takeIf { it.isNotBlank() }?.let { Ha.isAlive(c, it) }
        val t = Ha.thermoEntity(c).takeIf { it.isNotBlank() }?.let { Ha.info(c, it) }
        val h = Ha.humEntity(c).takeIf { it.isNotBlank() }?.let { Ha.info(c, it) }
        c.getSharedPreferences("ha", Context.MODE_PRIVATE).edit().apply {
            if (light != null) putBoolean("last_on", light)
            if (tv != null) putBoolean("tv_on", tv)
            if (led != null) putBoolean("led_on", led)
            if (pc != null) putBoolean("pc_on", pc)
            if (t != null) { putString("thermo_temp", t.first); putString("thermo_label", t.second) }
            if (h != null) putString("thermo_hum", h.first)
        }.apply()
    }

    private fun bc(c: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, code, Intent(c, DashWidget::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun render(c: Context, mgr: AppWidgetManager, ids: IntArray) {
        val p = c.getSharedPreferences("ha", Context.MODE_PRIVATE)
        val light = p.getBoolean("last_on", false)
        val tv = p.getBoolean("tv_on", false)
        val led = p.getBoolean("led_on", false)
        val pcKnown = Ha.pcStateEntity(c).isNotBlank()
        val pc = p.getBoolean("pc_on", false)
        val temp = p.getString("thermo_temp", null)?.toDoubleOrNull()
        val hum = p.getString("thermo_hum", null)?.toDoubleOrNull()
        val label = p.getString("thermo_label", "")?.takeIf { it.isNotBlank() } ?: ""
        val amber = Color.parseColor("#FFC107")
        val grey = Color.parseColor("#8A8D94")
        val any = light || tv || led || (pcKnown && pc)

        val v = RemoteViews(c.packageName, R.layout.widget_dash)
        v.setInt(R.id.root, "setBackgroundResource", if (any) R.drawable.bg_widget_on else R.drawable.bg_widget_off)

        v.setTextViewText(R.id.d_temp, if (temp != null) String.format(Locale.getDefault(), "%.1f\u00b0", temp) else "--")
        v.setTextColor(R.id.d_temp, when {
            temp == null -> grey
            temp < 18 -> Color.parseColor("#64B5F6")
            temp > 26 -> Color.parseColor("#FF8A65")
            else -> Color.WHITE
        })
        v.setTextViewText(R.id.d_hum, if (hum != null) String.format(Locale.getDefault(), "%.0f %%", hum) else "")
        v.setTextViewText(R.id.d_label, label)
        v.setOnClickPendingIntent(R.id.d_thermo, bc(c, ACTION_REFRESH, 40))

        fun tile(tile: Int, icon: Int, state: Int, on: Boolean, text: String) {
            v.setInt(tile, "setBackgroundResource", if (on) R.drawable.bg_btn_on else R.drawable.bg_btn)
            v.setInt(icon, "setColorFilter", if (on) amber else grey)
            v.setTextViewText(state, text)
            v.setTextColor(state, if (on) amber else grey)
        }
        tile(R.id.t_light, R.id.i_light, R.id.s_light, light, if (light) "Allum\u00e9e" else "\u00c9teinte")
        tile(
            R.id.t_pc, R.id.i_pc, R.id.s_pc, pcKnown && pc,
            if (!pcKnown) "Appuyer" else if (pc) "Allum\u00e9" else "\u00c9teint"
        )
        tile(R.id.t_tv, R.id.i_tv, R.id.s_tv, tv, if (tv) "Allum\u00e9e" else "\u00c9teinte")
        tile(R.id.t_led, R.id.i_led, R.id.s_led, led, if (led) "Allum\u00e9es" else "\u00c9teintes")
        tile(
            R.id.t_all, R.id.i_all, R.id.s_all, tv,
            when {
                tv && led -> "Tout allum\u00e9"
                tv -> "T\u00e9l\u00e9 seule"
                led -> "LED seules"
                else -> "Tout \u00e9teint"
            }
        )
        // cover tiles: static, white icons
        for (id in listOf(R.id.i_up, R.id.i_stop, R.id.i_down)) v.setInt(id, "setColorFilter", Color.parseColor("#E6E6E9"))

        v.setOnClickPendingIntent(R.id.t_light, bc(c, ACTION_LIGHT, 41))
        v.setOnClickPendingIntent(R.id.t_up, bc(c, ACTION_UP, 42))
        v.setOnClickPendingIntent(R.id.t_stop, bc(c, ACTION_STOP, 43))
        v.setOnClickPendingIntent(R.id.t_down, bc(c, ACTION_DOWN, 44))
        v.setOnClickPendingIntent(R.id.t_all, bc(c, ACTION_ALL, 45))
        v.setOnClickPendingIntent(R.id.t_tv, bc(c, ACTION_TV, 46))
        v.setOnClickPendingIntent(R.id.t_led, bc(c, ACTION_LED, 47))
        v.setOnClickPendingIntent(
            R.id.t_pc,
            PendingIntent.getActivity(
                c, 48, Intent(c, ConfirmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        mgr.updateAppWidget(ids, v)
    }

    companion object {
        const val ACTION_REFRESH = "com.lashox.huewidget.DASH_REFRESH"
        const val ACTION_LIGHT = "com.lashox.huewidget.DASH_LIGHT"
        const val ACTION_UP = "com.lashox.huewidget.DASH_UP"
        const val ACTION_STOP = "com.lashox.huewidget.DASH_STOP"
        const val ACTION_DOWN = "com.lashox.huewidget.DASH_DOWN"
        const val ACTION_ALL = "com.lashox.huewidget.DASH_ALL"
        const val ACTION_TV = "com.lashox.huewidget.DASH_TV"
        const val ACTION_LED = "com.lashox.huewidget.DASH_LED"
        private val ACTIONS = setOf(
            ACTION_REFRESH, ACTION_LIGHT, ACTION_UP, ACTION_STOP, ACTION_DOWN, ACTION_ALL, ACTION_TV, ACTION_LED
        )
    }
}

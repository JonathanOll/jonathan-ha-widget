package com.lashox.huewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.view.View
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAppWidgetManager

@RunWith(AndroidJUnit4::class)
class WidgetsTest : FakeHaTest() {
    private lateinit var mgr: AppWidgetManager
    private lateinit var shadow: ShadowAppWidgetManager

    @Before fun setUpWidgets() {
        mgr = AppWidgetManager.getInstance(ctx)
        shadow = shadowOf(mgr)
    }

    /** Adds a widget (runs its onUpdate, which may queue a refresh) and waits for the queued work. */
    private fun add(cls: Class<out AppWidgetProvider>, layout: Int): Int {
        val id = shadow.createWidget(cls, layout)
        drainActions()
        idleMain()
        return id
    }

    private fun cache(vararg kv: Pair<String, Any>) {
        val e = prefs().edit()
        for ((k, v) in kv) when (v) {
            is Boolean -> e.putBoolean(k, v)
            is String -> e.putString(k, v)
        }
        e.commit()
    }

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()
    private fun View.color(id: Int) = findViewById<TextView>(id).currentTextColor
    private fun View.desc(id: Int) = findViewById<View>(id).contentDescription?.toString()
    private fun View.bg(id: Int) = shadowOf(findViewById<View>(id).background).createdFromResId
    private fun s(id: Int) = ctx.getString(id)

    // ---- light ----

    @Test fun light_onAndOff() {
        val id = add(LightWidget::class.java, R.layout.widget)
        cache(Keys.CACHE_LIGHT to true)
        LightWidget.render(ctx, mgr, intArrayOf(id))
        var v = shadow.getViewFor(id)
        assertEquals(s(R.string.light) + ", " + s(R.string.onoff_on_f), v.desc(R.id.root))
        assertEquals(R.drawable.bg_widget_on, v.bg(R.id.root))
        assertEquals(R.drawable.bg_pill_on, v.bg(R.id.icon))

        cache(Keys.CACHE_LIGHT to false)
        LightWidget.render(ctx, mgr, intArrayOf(id))
        v = shadow.getViewFor(id)
        assertEquals("Lumière, éteinte", v.desc(R.id.root))
        assertEquals(R.drawable.bg_widget_off, v.bg(R.id.root))
    }

    @Test fun light_onUpdate_fetchesStateFromHa() {
        ha.states["light.salon"] = "on"
        val id = add(LightWidget::class.java, R.layout.widget)
        assertTrue(prefs().getBoolean(Keys.CACHE_LIGHT, false))
        // the refresh redraws the widget from the new cache
        LightWidget.render(ctx, mgr, intArrayOf(id))
        assertEquals("Lumière, allumée", shadow.getViewFor(id).desc(R.id.root))
    }

    // ---- TV ----

    @Test fun tv_mainButtonFollowsTvOnly() {
        val id = add(TvWidget::class.java, R.layout.widget_tv)
        cache(Keys.CACHE_TV to false, Keys.CACHE_LED to true)
        TvWidget.render(ctx, mgr, intArrayOf(id))
        val v = shadow.getViewFor(id)
        assertEquals(Colors.GREY, v.color(R.id.l_all))
        assertEquals(Colors.GREY, v.color(R.id.l_tv))
        assertEquals(Colors.AMBER, v.color(R.id.l_led))
        assertEquals(R.drawable.bg_widget_off, v.bg(R.id.root))
        assertEquals(R.drawable.bg_btn_on, v.bg(R.id.btn_led))
        assertEquals(ctx.getString(R.string.a11y_tv_all, "éteinte"), v.desc(R.id.btn_all))
        assertEquals("Télé, éteinte", v.desc(R.id.btn_tv))
        assertEquals("LED, allumées", v.desc(R.id.btn_led))

        cache(Keys.CACHE_TV to true, Keys.CACHE_LED to false)
        TvWidget.render(ctx, mgr, intArrayOf(id))
        val v2 = shadow.getViewFor(id)
        assertEquals(Colors.AMBER, v2.color(R.id.l_all))
        assertEquals(Colors.AMBER, v2.color(R.id.l_tv))
        assertEquals(Colors.GREY, v2.color(R.id.l_led))
        assertEquals(R.drawable.bg_widget_on, v2.bg(R.id.root))
        assertEquals("LED, éteintes", v2.desc(R.id.btn_led))
    }

    // ---- thermometer ----

    @Test fun thermo_coldWithoutHumidity() {
        val id = add(ThermoWidget::class.java, R.layout.widget_thermo)
        cache(Keys.CACHE_TEMP to "16.5")
        ThermoWidget.render(ctx, mgr, intArrayOf(id))
        val v = shadow.getViewFor(id)
        assertEquals(ThermoWidget.tempText(16.5), v.text(R.id.temp))
        assertEquals(Colors.COLD, v.color(R.id.temp))
        assertEquals(View.GONE, v.findViewById<View>(R.id.hum_row).visibility)
        assertEquals(s(R.string.thermo_default_label), v.text(R.id.label))
        assertEquals(R.drawable.bg_widget_cold, v.bg(R.id.root))
        assertEquals(ThermoWidget.describe(ctx, s(R.string.thermo_default_label), 16.5, null), v.desc(R.id.root))
    }

    @Test fun thermo_warmWithHumidityAndLabel() {
        val id = add(ThermoWidget::class.java, R.layout.widget_thermo)
        cache(Keys.CACHE_TEMP to "28", Keys.CACHE_HUM to "40", Keys.CACHE_LABEL to "Chambre")
        ThermoWidget.render(ctx, mgr, intArrayOf(id))
        val v = shadow.getViewFor(id)
        assertEquals(Colors.WARM, v.color(R.id.temp))
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.hum_row).visibility)
        assertEquals(ThermoWidget.humText(40.0), v.text(R.id.hum))
        assertEquals("Chambre", v.text(R.id.label))
        assertEquals(R.drawable.bg_widget_warm, v.bg(R.id.root))
        assertTrue(v.desc(R.id.root)!!.startsWith("Chambre, "))
        assertTrue(v.desc(R.id.root)!!.contains(ctx.getString(R.string.a11y_thermo_hum, ThermoWidget.humText(40.0))))
    }

    @Test fun thermo_unknownTemperature() {
        val id = add(ThermoWidget::class.java, R.layout.widget_thermo)
        cache(Keys.CACHE_TEMP to "unavailable")
        ThermoWidget.render(ctx, mgr, intArrayOf(id))
        val v = shadow.getViewFor(id)
        assertEquals("--", v.text(R.id.temp))
        assertEquals(Colors.GREY, v.color(R.id.temp))
        assertEquals(R.drawable.bg_widget_off, v.bg(R.id.root))
        assertEquals(s(R.string.thermo_default_label) + ", " + s(R.string.a11y_thermo_unknown), v.desc(R.id.root))
    }

    @Test fun thermo_helpers() {
        assertEquals(Colors.WHITE, ThermoWidget.tempColor(21.0))
        assertEquals(Colors.GREY, ThermoWidget.tempColor(null))
        assertEquals("--", ThermoWidget.tempText(null))
        assertEquals("", ThermoWidget.humText(null))
    }

    // ---- PC ----

    @Test fun pc_withoutStateEntity_showsTap() {
        val id = add(PcWidget::class.java, R.layout.widget_pc)
        cache(Keys.CACHE_PC to true) // ignored: no state entity configured
        PcWidget.render(ctx, mgr, intArrayOf(id))
        val v = shadow.getViewFor(id)
        assertEquals(s(R.string.state_tap), v.text(R.id.status))
        assertEquals(s(R.string.pc), v.desc(R.id.root))
        assertEquals(R.drawable.bg_widget_off, v.bg(R.id.root))
    }

    @Test fun pc_withStateEntity_onAndOff() {
        Ha.saveMisc(ctx, "button.pc_on", "button.pc_off", "sensor.pc_cpu", "", "")
        ha.states["sensor.pc_cpu"] = "7"
        val id = add(PcWidget::class.java, R.layout.widget_pc) // onUpdate refresh -> alive
        PcWidget.render(ctx, mgr, intArrayOf(id))
        var v = shadow.getViewFor(id)
        assertEquals(s(R.string.state_on_m), v.text(R.id.status))
        assertEquals("PC, allumé", v.desc(R.id.root))
        assertEquals(R.drawable.bg_widget_on, v.bg(R.id.root))
        assertEquals(R.drawable.bg_circle_on, v.bg(R.id.icon))

        cache(Keys.CACHE_PC to false)
        PcWidget.render(ctx, mgr, intArrayOf(id))
        v = shadow.getViewFor(id)
        assertEquals(s(R.string.state_off_m), v.text(R.id.status))
        assertEquals("PC, éteint", v.desc(R.id.root))
    }

    // ---- cover ----

    @Test fun cover_buttonsDescribed() {
        val id = add(CoverWidget::class.java, R.layout.widget_cover)
        val v = shadow.getViewFor(id)
        assertEquals(s(R.string.a11y_cover_open), v.desc(R.id.btn_up))
        assertEquals(s(R.string.a11y_cover_stop), v.desc(R.id.btn_stop))
        assertEquals(s(R.string.a11y_cover_close), v.desc(R.id.btn_down))
    }

    // ---- dashboard ----

    @Test fun dash_states() {
        val id = add(DashWidget::class.java, R.layout.widget_dash)
        cache(
            Keys.CACHE_LIGHT to true, Keys.CACHE_TV to true, Keys.CACHE_LED to false,
            Keys.CACHE_TEMP to "21.5", Keys.CACHE_HUM to "45", Keys.CACHE_LABEL to "Chambre"
        )
        DashWidget.render(ctx, mgr, intArrayOf(id))
        val v = shadow.getViewFor(id)
        assertEquals(R.drawable.bg_widget_on, v.bg(R.id.root))
        assertEquals(s(R.string.state_on_f), v.text(R.id.s_light))
        assertEquals(Colors.AMBER, v.color(R.id.s_light))
        assertEquals(s(R.string.state_on_f), v.text(R.id.s_tv))
        assertEquals(s(R.string.state_off_fp), v.text(R.id.s_led))
        assertEquals(Colors.GREY, v.color(R.id.s_led))
        assertEquals(s(R.string.state_tv_only), v.text(R.id.s_all))
        assertEquals(s(R.string.state_tap), v.text(R.id.s_pc))
        assertEquals(ThermoWidget.tempText(21.5), v.text(R.id.d_temp))
        assertEquals(Colors.WHITE, v.color(R.id.d_temp))
        assertEquals(ThermoWidget.humText(45.0), v.text(R.id.d_hum))
        assertEquals("Chambre", v.text(R.id.d_label))
        assertEquals("Lumière, allumée", v.desc(R.id.t_light))
        assertEquals("LED, éteintes", v.desc(R.id.t_led))
        assertEquals(s(R.string.a11y_cover_open), v.desc(R.id.t_up))
        assertEquals(R.drawable.bg_btn_on, v.bg(R.id.t_light))
        assertEquals(R.drawable.bg_btn, v.bg(R.id.t_led))
    }

    @Test fun dash_allOffAndUnknownTemperature() {
        val id = add(DashWidget::class.java, R.layout.widget_dash)
        cache(Keys.CACHE_LIGHT to false, Keys.CACHE_TV to false, Keys.CACHE_LED to true)
        DashWidget.render(ctx, mgr, intArrayOf(id))
        var v = shadow.getViewFor(id)
        assertEquals(R.drawable.bg_widget_on, v.bg(R.id.root)) // LEDs alone count as "something on"
        assertEquals(s(R.string.state_led_only), v.text(R.id.s_all))
        assertEquals("--", v.text(R.id.d_temp))
        assertEquals(Colors.GREY, v.color(R.id.d_temp))
        assertEquals("", v.text(R.id.d_label))

        cache(Keys.CACHE_LED to false)
        DashWidget.render(ctx, mgr, intArrayOf(id))
        v = shadow.getViewFor(id)
        assertEquals(R.drawable.bg_widget_off, v.bg(R.id.root))
        assertEquals(s(R.string.state_all_off), v.text(R.id.s_all))
    }

    // ---- refreshAll / helpers ----

    @Test fun refreshAll_redrawsEveryWidgetWithoutCrash() {
        val light = add(LightWidget::class.java, R.layout.widget)
        add(CoverWidget::class.java, R.layout.widget_cover)
        val tv = add(TvWidget::class.java, R.layout.widget_tv)
        add(ThermoWidget::class.java, R.layout.widget_thermo)
        add(PcWidget::class.java, R.layout.widget_pc)
        val dash = add(DashWidget::class.java, R.layout.widget_dash)
        cache(Keys.CACHE_LIGHT to true, Keys.CACHE_TV to true, Keys.CACHE_LED to true)
        Widgets.refreshAll(ctx)
        idleMain()
        assertEquals("Lumière, allumée", shadow.getViewFor(light).desc(R.id.root))
        assertEquals(Colors.AMBER, shadow.getViewFor(tv).color(R.id.l_all))
        assertEquals(s(R.string.state_all_on), shadow.getViewFor(dash).text(R.id.s_all))
    }

    @Test fun refreshAll_noWidgets_noCrash() {
        Widgets.refreshAll(ctx)
        idleMain()
    }

    @Test fun clickIntents() {
        val pi = Widgets.action(ctx, Act.LIGHT_TOGGLE, 0)
        val sp = shadowOf(pi)
        assertTrue(sp.isBroadcast)
        assertEquals(Act.LIGHT_TOGGLE, sp.savedIntent.action)
        assertEquals(ActionReceiver::class.java.name, sp.savedIntent.component?.className)
        assertTrue(pi.isImmutable)

        val confirm = shadowOf(Widgets.confirmPc(ctx, 31))
        assertTrue(confirm.isActivity)
        assertEquals(ConfirmActivity::class.java.name, confirm.savedIntent.component?.className)
        assertNotNull(confirm.savedIntent)
    }

    @Test fun onOffText_andDescribe() {
        assertEquals("allumé", Widgets.onOffText(ctx, true))
        assertEquals("éteint", Widgets.onOffText(ctx, false))
        assertEquals("allumée", Widgets.onOffText(ctx, true, feminine = true))
        assertEquals("éteintes", Widgets.onOffText(ctx, false, feminine = true, plural = true))
        assertEquals("allumés", Widgets.onOffText(ctx, true, plural = true))
        assertEquals("éteints", Widgets.onOffText(ctx, false, plural = true))
        assertEquals("PC, allumé", Widgets.describe(ctx, "PC", "allumé"))
    }
}

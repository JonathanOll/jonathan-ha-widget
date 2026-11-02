package com.lashox.huewidget

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke tests of the Compose screens (Robolectric). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ScreensTest : FakeHaTest() {
    @get:Rule val compose = createEmptyComposeRule()

    private fun s(id: Int) = ctx.getString(id)

    // ---- ConfigActivity ----

    /** Types [url] on the Connection page (shown first: nothing saved yet), taps Save, expects [expectText]. */
    private fun saveUrlOnConnectionPage(url: String, expectText: String) {
        Ha.save(ctx, "", TOKEN, "")
        ActivityScenario.launch(ConfigActivity::class.java).use {
            compose.onNodeWithText(s(R.string.field_url)).performTextInput(url)
            compose.onNodeWithText(s(R.string.btn_save)).performScrollTo().performClick()
            compose.onNodeWithText(expectText).assertExists()
        }
    }

    @Test fun config_invalidUrl_showsErrorAndIsNotSaved() {
        saveUrlOnConnectionPage("not a url", s(R.string.url_invalid))
        assertEquals("", Ha.url(ctx))
    }

    @Test fun config_publicHttpUrl_showsInsecureError() {
        saveUrlOnConnectionPage("http://ha.example.com:8123", s(R.string.url_insecure))
        assertEquals("", Ha.url(ctx))
    }

    @Test fun config_localUrl_savedAndHomeShown() {
        saveUrlOnConnectionPage(baseUrl, s(R.string.label_devices).uppercase())
        assertEquals(baseUrl, Ha.url(ctx))
    }

    @Test fun config_savedConnection_autoTestListsEntities() {
        ha.states["light.salon"] = "on"
        ha.states["switch.tv"] = "off"
        ActivityScenario.launch(ConfigActivity::class.java).use {
            val connected = ctx.getString(R.string.status_connected, 2)
            compose.waitUntilAtLeastOneExists(hasText(connected), 10_000)
        }
        assertTrue(server.requestCount >= 1)
        assertEquals("/api/states", server.takeRequest().path)
    }

    // ---- ConfirmActivity ----

    @Test fun confirm_unknownState_showsBothButtons() {
        Ha.saveMisc(ctx, "button.pc_on", "button.pc_off", "", "", "")
        ActivityScenario.launch(ConfirmActivity::class.java).use {
            compose.onNodeWithText(s(R.string.action_on)).assertExists()
            compose.onNodeWithText(s(R.string.action_off)).assertExists()
            compose.onNodeWithText(s(R.string.action_cancel)).assertExists()
            compose.onNodeWithText(s(R.string.confirm_question)).assertExists()
        }
    }

    @Test fun confirm_haError_stateUnknown_showsBothButtons() {
        Ha.saveMisc(ctx, "button.pc_on", "button.pc_off", "sensor.pc_cpu", "", "")
        ha.stateOverride["sensor.pc_cpu"] = { okhttp3.mockwebserver.MockResponse().setResponseCode(500) }
        ActivityScenario.launch(ConfirmActivity::class.java).use {
            compose.waitUntilAtLeastOneExists(hasText(s(R.string.confirm_question)), 10_000)
            compose.onNodeWithText(s(R.string.action_on)).assertExists()
            compose.onNodeWithText(s(R.string.action_off)).assertExists()
        }
    }

    @Test fun confirm_pcOn_offersOnlyShutdown() {
        Ha.saveMisc(ctx, "button.pc_on", "button.pc_off", "sensor.pc_cpu", "", "")
        ha.states["sensor.pc_cpu"] = "12"
        ActivityScenario.launch(ConfirmActivity::class.java).use {
            compose.waitUntilAtLeastOneExists(hasText(s(R.string.confirm_pc_off_title)), 10_000)
            compose.onNodeWithText(s(R.string.action_off)).assertExists()
            compose.onNodeWithText(s(R.string.action_on)).assertDoesNotExist()
            compose.onNodeWithText(s(R.string.action_off)).performClick()
        }
        val sent = org.robolectric.Shadows.shadowOf(ctx as android.app.Application).broadcastIntents.last()
        assertEquals(Act.PC_OFF, sent.action)
    }

    @Test fun confirm_noEntity_showsHint() {
        ActivityScenario.launch(ConfirmActivity::class.java).use {
            compose.onNodeWithText(s(R.string.confirm_no_entity)).assertExists()
            compose.onNodeWithText(s(R.string.action_on)).assertDoesNotExist()
        }
    }
}

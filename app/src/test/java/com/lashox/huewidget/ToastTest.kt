package com.lashox.huewidget

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@RunWith(AndroidJUnit4::class)
class ToastTest {
    private lateinit var ctx: Context

    @Before fun setUp() {
        TestClock.install()
        ctx = ApplicationProvider.getApplicationContext()
    }

    @After fun tearDown() = TestClock.restore()

    /** Shows the toast for [e] (past the 2 s throttle) and returns its text. */
    private fun toastOf(e: Exception): String {
        TestClock.advance(3000)
        Ha.toast(ctx, e)
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowToast.getTextOfLatestToast()
    }

    @Test fun insecureUrl() = assertEquals(ctx.getString(R.string.err_insecure_url), toastOf(InsecureUrlException()))

    @Test fun tokenRefused_401_403() {
        assertEquals(ctx.getString(R.string.err_token_refused), toastOf(HaHttpException(401)))
        assertEquals(ctx.getString(R.string.err_token_refused), toastOf(HaHttpException(403)))
    }

    @Test fun entityNotFound_404() = assertEquals(ctx.getString(R.string.err_entity_not_found), toastOf(HaHttpException(404)))

    @Test fun otherHttpCodes() {
        assertEquals(ctx.getString(R.string.err_ha_http, 500), toastOf(HaHttpException(500)))
        assertEquals(ctx.getString(R.string.err_ha_http, 302), toastOf(HaHttpException(302)))
        assertEquals("Erreur Home Assistant (503)", toastOf(HaHttpException(503)))
    }

    @Test fun networkErrors_unreachable() {
        val expected = ctx.getString(R.string.err_unreachable)
        for (e in listOf(ConnectException(), SocketTimeoutException(), UnknownHostException(), NoRouteToHostException())) {
            assertEquals(e.javaClass.simpleName, expected, toastOf(e))
        }
    }

    @Test fun genericError_showsMessageOrClassName() {
        assertEquals(ctx.getString(R.string.err_generic, "boom"), toastOf(IllegalStateException("boom")))
        assertEquals(ctx.getString(R.string.err_generic, "IOException"), toastOf(IOException()))
    }

    @Test fun throttled_parallelFailuresGiveOneToast() {
        TestClock.advance(3000)
        Ha.toast(ctx, HaHttpException(500))
        Ha.toast(ctx, ConnectException())
        Ha.toast(ctx, "third")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, ShadowToast.shownToastCount())
        assertEquals(ctx.getString(R.string.err_ha_http, 500), ShadowToast.getTextOfLatestToast())
        TestClock.advance(2100)
        Ha.toast(ctx, "later")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, ShadowToast.shownToastCount())
        assertEquals("later", ShadowToast.getTextOfLatestToast())
    }

    @Test fun toastFromBackgroundThread_isPostedToMain() {
        TestClock.advance(3000)
        val t = Thread { Ha.toast(ctx, "from worker") }
        t.start(); t.join()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("from worker", ShadowToast.getTextOfLatestToast())
    }
}

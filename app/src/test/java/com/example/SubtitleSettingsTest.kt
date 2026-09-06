package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtitleSettingsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun clearPreferences() {
        context.getSharedPreferences("clutube_app_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `offline subtitle defaults prefer english auto download`() {
        val manager = SettingsManager(context)

        assertTrue(manager.isSubtitleAutoDownloadEnabled)
        assertEquals("en", manager.offlineSubtitleLanguage)
        assertFalse(manager.isSubtitlePreferHearingImpaired)
        assertEquals("", manager.wyzieApiKey)
        assertEquals("", manager.subdlApiKey)
        assertEquals(
            SettingsManager.DEFAULT_SUBTITLE_PROXY_BASE_URL,
            manager.subtitleProxyBaseUrl
        )
    }

    @Test
    fun `subtitle keys and proxy round trip`() {
        val manager = SettingsManager(context)
        manager.wyzieApiKey = "  wyz-key  "
        manager.subdlApiKey = "sub-key"
        manager.subtitleProxyBaseUrl = "https://my-worker.example.workers.dev/"
        manager.offlineSubtitleLanguage = "es"
        manager.isSubtitleAutoDownloadEnabled = false
        manager.isSubtitlePreferHearingImpaired = true

        val reloaded = SettingsManager(context)

        assertEquals("wyz-key", reloaded.wyzieApiKey)
        assertEquals("sub-key", reloaded.subdlApiKey)
        // Trailing slash is normalized so URL building stays exact.
        assertEquals("https://my-worker.example.workers.dev", reloaded.subtitleProxyBaseUrl)
        assertEquals("es", reloaded.offlineSubtitleLanguage)
        assertFalse(reloaded.isSubtitleAutoDownloadEnabled)
        assertTrue(reloaded.isSubtitlePreferHearingImpaired)
    }

    @Test
    fun `blank proxy resets to default`() {
        val manager = SettingsManager(context)
        manager.subtitleProxyBaseUrl = "   "

        assertEquals(
            SettingsManager.DEFAULT_SUBTITLE_PROXY_BASE_URL,
            SettingsManager(context).subtitleProxyBaseUrl
        )
    }
}

package com.example

import com.example.util.PlaybackScript
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackScriptTest {

    @Test
    fun `tracking script contains generation aware playback hooks and clamped resume`() {
        val script = PlaybackScript.build(generation = 17, resumePositionSeconds = 30.0)

        assertTrue(script.contains("onPlaybackSnapshot"))
        assertTrue(script.contains("onVideoEnded"))
        assertTrue(script.contains("onPlayerReady"))
        assertTrue(script.contains("loadedmetadata"))
        assertTrue(script.contains("timeupdate"))
        assertTrue(script.contains("volumechange"))
        assertTrue(script.contains("Math.min(resumePosition"))
        assertTrue(script.contains("var generation = 17"))
        assertTrue(script.contains("defaultMuted = false"))
        assertTrue(script.contains("video.volume = 1"))
        assertTrue(!script.contains("video.muted = true"))
        assertTrue(script.contains("PLAYER_EVENT"))
        assertTrue(script.contains("CLUTUBE_PLAYER_UI"))
        assertTrue(script.contains("onPlayerUiVisibilityChanged"))
        assertTrue(script.contains("currentTime"))
        assertTrue(script.contains("rawStatus === 'ended'"))
    }

    @Test
    fun `script carries persisted quality and subtitle preferences`() {
        val script = PlaybackScript.build(
            generation = 18,
            resumePositionSeconds = 12.0,
            preferredQuality = "1080p",
            preferredSubtitles = "en"
        )

        assertTrue(script.contains("var preferredQuality = '1080p'"))
        assertTrue(script.contains("var preferredSubtitles = 'en'"))
        assertTrue(script.contains("CLUTUBE_PLAYER_PREFERENCE"))
        assertTrue(script.contains("__cluPreferredQuality"))
        assertTrue(script.contains("__cluPreferredSubtitles"))
        assertTrue(script.contains("setCurrentQuality"))
        assertTrue(script.contains("setCurrentCaptions"))
        assertTrue(script.contains("broadcastPlaybackPreference"))
    }
}

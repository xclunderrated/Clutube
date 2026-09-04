package com.example.util

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Applies the CluTube player treatment to the VidSrc player stylesheet while
 * leaving the provider's media and playback code untouched.
 *
 * VidSrc serves its actual player from a nested, cross-origin frame, so a
 * normal evaluateJavascript call in the outer WebView cannot reach it. The
 * stylesheet response is therefore augmented as it passes through the
 * WebView. If the upstream CSS cannot be fetched, returning null preserves the
 * provider's normal loading path.
 */
internal object StreamPlayerSkin {
    private const val PLAYER_CSS_PATH = "/embed/iframe_player/assets/player.css"
    private const val PLAYER_JS_PATH = "/embed/iframe_player/assets/player.js"
    private const val EMBED_PATH_PREFIX = "/embed/"
    private const val PLAYER_HOST = "cloudorchestranova.com"
    private const val VIDLINK_HOST = "vidlink.pro"
    private val VIDLINK_JS_PATHS = listOf("/video.js", "/dist/video.js", "/assets/video.js", "/js/video.js")
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

    /** Returns true only for the known VidSrc player stylesheet. */
    fun isPlayerCssUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val uri = runCatching { URL(rawUrl) }.getOrNull() ?: return false
        val host = uri.host.lowercase()
        return uri.protocol.equals("https", ignoreCase = true) &&
            (host == PLAYER_HOST || host.endsWith(".$PLAYER_HOST")) &&
            uri.path == PLAYER_CSS_PATH
    }

    /** Returns true only for the known VidSrc player runtime script. */
    fun isPlayerJsUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val uri = runCatching { URL(rawUrl) }.getOrNull() ?: return false
        val host = uri.host.lowercase()
        return uri.protocol.equals("https", ignoreCase = true) &&
            (host == PLAYER_HOST || host.endsWith(".$PLAYER_HOST")) &&
            uri.path == PLAYER_JS_PATH
    }

    /** Returns true for VidLink's video.js runtime script. */
    fun isVidLinkPlayerJsUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val uri = runCatching { URL(rawUrl) }.getOrNull() ?: return false
        val host = uri.host.lowercase()
        val path = uri.path.lowercase()
        return uri.protocol.equals("https", ignoreCase = true) &&
            (host == VIDLINK_HOST || host.endsWith(".$VIDLINK_HOST")) &&
            VIDLINK_JS_PATHS.any { path.endsWith(it) }
    }

    /** Returns true for the CloudOrchestra landing page that creates the real player iframe. */
    fun isPlayerEmbedHtmlUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val uri = runCatching { URL(rawUrl) }.getOrNull() ?: return false
        val host = uri.host.lowercase()
        val path = uri.path
        return uri.protocol.equals("https", ignoreCase = true) &&
            (host == PLAYER_HOST || host.endsWith(".$PLAYER_HOST")) &&
            path.startsWith(EMBED_PATH_PREFIX) &&
            !path.startsWith("/embed/iframe_player/") &&
            !path.startsWith("/embed/player/")
    }

    /** Starts VidSrc's landing page so the nested player can load without a tap. */
    fun interceptPlayerEmbedHtml(request: WebResourceRequest?): WebResourceResponse? {
        val requestUrl = request?.url?.toString() ?: return null
        if (!isPlayerEmbedHtmlUrl(requestUrl)) return null

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("Accept", "text/html,*/*;q=0.1")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", USER_AGENT)
                request?.requestHeaders
                    ?.entries
                    ?.firstOrNull { (key, _) -> key.equals("Referer", ignoreCase = true) }
                    ?.value
                    ?.takeIf { it.isNotBlank() }
                    ?.let { setRequestProperty("Referer", it) }
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return null

            val upstreamHtml = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val combinedHtml = if (upstreamHtml.contains("</body>", ignoreCase = true)) {
                upstreamHtml.replace(
                    "</body>",
                    "$AUTOSTART_RUNTIME</body>",
                    ignoreCase = true
                )
            } else {
                upstreamHtml + AUTOSTART_RUNTIME
            }
            WebResourceResponse(
                "text/html",
                "UTF-8",
                ByteArrayInputStream(combinedHtml.toByteArray(Charsets.UTF_8))
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Appends a runtime dock to the nested player, where outer-page JS cannot reach. */
    fun interceptPlayerJs(request: WebResourceRequest?): WebResourceResponse? {
        val requestUrl = request?.url?.toString() ?: return null
        if (!isPlayerJsUrl(requestUrl)) return null

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/javascript,*/*;q=0.1")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", USER_AGENT)
                request?.requestHeaders
                    ?.entries
                    ?.firstOrNull { (key, _) -> key.equals("Referer", ignoreCase = true) }
                    ?.value
                    ?.takeIf { it.isNotBlank() }
                    ?.let { setRequestProperty("Referer", it) }
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return null

            val upstreamJs = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val combinedJs = upstreamJs + "\n\n" + TOP_ACTIONS_RUNTIME + AUTOPLAY_RUNTIME + PLAYER_COMMAND_RUNTIME + PLAYER_PREFERENCE_RUNTIME + PLAYER_UI_RUNTIME
            WebResourceResponse(
                "application/javascript",
                "UTF-8",
                ByteArrayInputStream(combinedJs.toByteArray(Charsets.UTF_8))
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** Appends the runtime dock to VidLink's video.js player. */
    fun interceptVidLinkPlayerJs(request: WebResourceRequest?): WebResourceResponse? {
        val requestUrl = request?.url?.toString() ?: return null
        if (!isVidLinkPlayerJsUrl(requestUrl)) return null

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/javascript,*/*;q=0.1")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", USER_AGENT)
                request?.requestHeaders
                    ?.entries
                    ?.firstOrNull { (key, _) -> key.equals("Referer", ignoreCase = true) }
                    ?.value
                    ?.takeIf { it.isNotBlank() }
                    ?.let { setRequestProperty("Referer", it) }
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return null

            val upstreamJs = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val combinedJs = upstreamJs + "\n\n" + PLAYER_PREFERENCE_RUNTIME
            WebResourceResponse(
                "application/javascript",
                "UTF-8",
                ByteArrayInputStream(combinedJs.toByteArray(Charsets.UTF_8))
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Fetches the original stylesheet and appends the skin as a WebView
     * resource override. This runs on WebView's resource thread, not the UI
     * thread, and falls back to the original request on any failure.
     */
    fun interceptPlayerCss(request: WebResourceRequest?): WebResourceResponse? {
        val requestUrl = request?.url?.toString() ?: return null
        if (!isPlayerCssUrl(requestUrl)) return null

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("Accept", "text/css,*/*;q=0.1")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", USER_AGENT)
                request?.requestHeaders
                    ?.entries
                    ?.firstOrNull { (key, _) -> key.equals("Referer", ignoreCase = true) }
                    ?.value
                    ?.takeIf { it.isNotBlank() }
                    ?.let { setRequestProperty("Referer", it) }
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return null

            val upstreamCss = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val combinedCss = upstreamCss + "\n\n" + YOUTUBE_PLAYER_CSS
            WebResourceResponse(
                "text/css",
                "UTF-8",
                ByteArrayInputStream(combinedCss.toByteArray(Charsets.UTF_8))
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private const val TOP_ACTIONS_RUNTIME = """
(function () {
    'use strict';
    function dockPlayerActions() {
        try {
            var player = document.getElementById('player') || document.querySelector('.jw');
            var cc = document.getElementById('ccBtn');
            var settings = document.getElementById('setBtn');
            if (!player || !cc || !settings) return;

            var dock = document.getElementById('clu-top-actions');
            if (!dock) {
                dock = document.createElement('div');
                dock.id = 'clu-top-actions';
                player.appendChild(dock);
            }
            dock.style.cssText =
                'position:absolute!important;top:10px!important;right:10px!important;' +
                'z-index:40!important;display:flex!important;gap:2px!important;' +
                'align-items:center!important;';
            if (cc.parentNode !== dock) dock.appendChild(cc);
            if (settings.parentNode !== dock) dock.appendChild(settings);
            [cc, settings].forEach(function (button) {
                button.style.cssText +=
                    ';position:static!important;top:auto!important;right:auto!important;' +
                    'bottom:auto!important;left:auto!important;margin:0!important;';
            });
        } catch (_) {}
    }
    dockPlayerActions();
    setTimeout(dockPlayerActions, 100);
    setTimeout(dockPlayerActions, 500);
    setTimeout(dockPlayerActions, 1500);
    if (window.MutationObserver && document.documentElement) {
        new MutationObserver(dockPlayerActions).observe(document.documentElement, {
            childList: true,
            subtree: true
        });
    }
})();
"""

    private const val AUTOSTART_RUNTIME = """
<script>
(function () {
    'use strict';
    var triggered = false;
    var attempts = 0;

    function startLandingPlayer() {
        if (triggered) return true;
        var config = window.CFG || window.CONFIG || {};
        if (!config.playerUrl && !config.autoStart) return false;
        // Keep the provider's own start() closure and listener intact, but
        // invoke the same landing action automatically from this page.
        config.autoStart = true;
        var button = document.getElementById('bigPlay');
        if (!button) return false;
        triggered = true;
        button.click();
        return true;
    }

    startLandingPlayer();
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', startLandingPlayer, { once: true });
    } else {
        setTimeout(startLandingPlayer, 0);
    }
    var timer = setInterval(function () {
        attempts++;
        if (startLandingPlayer() || attempts > 30) clearInterval(timer);
    }, 250);
})();
</script>
"""

    private const val AUTOPLAY_RUNTIME = """
(function () {
    'use strict';
    var attempts = 0;
    var started = false;

    function autoplayEnabled() {
        var config = window.CONFIG || window.CFG || {};
        return config.autoplay !== false && config.autoplay !== 0 && String(config.autoplay) !== '0';
    }

    function startVideo() {
        if (started || !autoplayEnabled()) return;
        var video = document.getElementById('video') || document.querySelector('video');
        if (!video) return;
        try {
            video.autoplay = true;
            video.playsInline = true;
            video.defaultMuted = false;
            video.muted = false;
            if (!video.paused) {
                started = true;
                return;
            }
            var result = video.play();
            if (result && typeof result.then === 'function') {
                result.then(function () {
                    started = true;
                }).catch(function () {
                    // Android WebView may reject audible autoplay once. Start
                    // silently, then restore sound so playback still begins.
                    try {
                        video.muted = true;
                        var mutedResult = video.play();
                        if (mutedResult && typeof mutedResult.then === 'function') {
                            mutedResult.then(function () {
                                started = true;
                                setTimeout(function () {
                                    try { video.muted = false; } catch (_) {}
                                }, 150);
                            }).catch(function () {});
                        }
                    } catch (_) {}
                });
            }
        } catch (_) {}
    }

    function clickLandingPlay() {
        if (!autoplayEnabled()) return;
        var config = window.CONFIG || window.CFG || {};
        if (config.landing) {
            var button = document.getElementById('bigPlay');
            if (button) button.click();
        }
        startVideo();
    }

    clickLandingPlay();
    document.addEventListener('DOMContentLoaded', clickLandingPlay, { once: true });
    var timer = setInterval(function () {
        attempts++;
        clickLandingPlay();
        if (started || attempts > 30) clearInterval(timer);
    }, 500);
    ['loadedmetadata', 'canplay', 'playing'].forEach(function (eventName) {
        document.addEventListener(eventName, startVideo, true);
    });
    })();
    """

    /** Receives commands from the outer WebView for cross-frame player gestures. */
    private const val PLAYER_COMMAND_RUNTIME = """
(function () {
    'use strict';
    function applyCommand(video, command) {
        if (!video || !command) return;
        try {
            if (command.action === 'play') video.play();
            else if (command.action === 'pause') video.pause();
            else if (command.action === 'setMuted') {
                video.muted = command.value === 1;
                video.defaultMuted = video.muted;
            } else if (command.action === 'seekTo') {
                video.currentTime = Math.max(0, command.value || 0);
            } else if (command.action === 'seekBy') {
                video.currentTime = Math.max(0, video.currentTime + (command.value || 0));
            } else if (command.action === 'playbackRate') {
                video.playbackRate = command.value > 0 ? command.value : 1;
            }
        } catch (_) {}
    }

    if (window.__cluPlayerCommandListener) {
        window.removeEventListener('message', window.__cluPlayerCommandListener);
    }
    window.__cluPlayerCommandListener = function (event) {
        var command = event && event.data;
        if (!command || command.type !== 'CLUTUBE_PLAYER_COMMAND') return;
        document.querySelectorAll('video').forEach(function (video) {
            applyCommand(video, command);
        });
    };
    window.addEventListener('message', window.__cluPlayerCommandListener);
})();
"""

/** Applies CluTube's account preferences to VidSrc's real HLS/subtitle player and VidLink's video.js player. */
    private const val PLAYER_PREFERENCE_RUNTIME = """
(function () {
    'use strict';

    var pendingQuality = null;
    var pendingSubtitles = null;

    function qualityHeight(value) {
        var text = String(value || '').toLowerCase().replace('p', '').trim();
        if (text === 'auto' || text === '') return null;
        var height = parseInt(text, 10);
        return isFinite(height) && height > 0 ? height : -1;
    }

    function getJwPlayer() {
        return window.__JW;
    }

    function getVideoJsPlayer() {
        var vjs = window.videojs;
        if (vjs && vjs.players) {
            var keys = Object.keys(vjs.players);
            if (keys.length) return vjs.players[keys[0]];
        }
        var root = document.querySelector('.video-js');
        if (root && root.player) return root.player;
        return null;
    }

    function applyJwQuality(value) {
        var player = getJwPlayer();
        var state = player && player.state;
        if (!state) return false;

        var height = qualityHeight(value);
        if (height === -1) return false;
        state.manualHeight = height;
        var hls = state.hls;
        var levels = state.qualities || [];
        if (!hls) return true;
        if (height === null) {
            hls.currentLevel = -1;
            return true;
        }

        var selected = -1;
        var closestDistance = Infinity;
        for (var index = 0; index < levels.length; index++) {
            var levelHeight = Number(levels[index].height || 0);
            var distance = Math.abs(levelHeight - height);
            if (levelHeight === height) {
                selected = index;
                break;
            }
            if (distance < closestDistance) {
                closestDistance = distance;
                selected = index;
            }
        }
        if (selected >= 0) hls.currentLevel = selected;
        return true;
    }

    function applyVideoJsQuality(value) {
        var player = getVideoJsPlayer();
        if (!player || typeof player.qualityLevels !== 'function') return false;

        var text = String(value || 'auto').toLowerCase();
        if (text === 'auto') {
            var levels = player.qualityLevels();
            if (levels && levels.length) {
                for (var i = 0; i < levels.length; i++) {
                    levels[i].enabled = true;
                }
                return true;
            }
            return false;
        }

        var requested = parseInt(text, 10);
        if (!isFinite(requested)) return false;

        var levels = player.qualityLevels();
        if (!levels || !levels.length) return false;

        var selected = -1;
        var closestDistance = Infinity;
        for (var i = 0; i < levels.length; i++) {
            var level = levels[i];
            var levelHeight = Number(level.height || 0);
            var distance = Math.abs(levelHeight - requested);
            if (levelHeight === requested) {
                selected = i;
                break;
            }
            if (distance < closestDistance) {
                closestDistance = distance;
                selected = i;
            }
        }
        if (selected >= 0) {
            for (var i = 0; i < levels.length; i++) {
                levels[i].enabled = (i === selected);
            }
            return true;
        }
        return false;
    }

    function applyQuality(value) {
        if (applyJwQuality(value)) return true;
        if (applyVideoJsQuality(value)) return true;
        return false;
    }

    function subtitleCode(value) {
        var code = String(value || '').toLowerCase().trim();
        if (code === 'off') return 'off';
        if (code === 'auto' || code === '') return '';
        if (code === 'en' || code === 'eng') return 'en';
        if (code === 'es' || code === 'spa') return 'es';
        return code;
    }

    function applyJwSubtitles(value) {
        var player = getJwPlayer();
        if (!player) return false;
        var code = subtitleCode(value);
        var config = player.CONFIG || window.CONFIG;
        if (config) config.dsLang = code === 'off' ? '' : code;

        if (code === 'off') {
            var menu = window.__ccMenu;
            var off = menu && menu.querySelector('[data-key="off"]');
            if (off) {
                off.click();
            } else {
                var subtitleState = player.SUB;
                if (subtitleState) subtitleState.activeKey = 'off';
                if (player.state) player.state.currentCues = null;
                var text = player.$ ? player.$('subText') : null;
                if (text) {
                    text.textContent = '';
                    text.dataset.cur = '';
                }
            }
            return true;
        }

        if (!window.JWSubs || typeof window.JWSubs.auto !== 'function') return false;
        var subtitleState = player.SUB;
        if (subtitleState) subtitleState.activeKey = 'off';
        if (player.state) player.state.currentCues = null;
        var text = player.$ ? player.$('subText') : null;
        if (text) {
            text.textContent = '';
            text.dataset.cur = '';
        }
        window.JWSubs.auto();
        return true;
    }

    function applyVideoJsSubtitles(value) {
        var player = getVideoJsPlayer();
        if (!player || !player.textTracks) return false;

        var code = subtitleCode(value);
        var tracks = player.textTracks();
        if (!tracks || !tracks.length) return false;

        if (code === 'off') {
            for (var i = 0; i < tracks.length; i++) {
                tracks[i].mode = 'disabled';
            }
            return true;
        }

        for (var i = 0; i < tracks.length; i++) {
            var track = tracks[i];
            var label = String(track.label || track.language || track.kind || '').toLowerCase();
            if (label.indexOf(code) >= 0 ||
                (code === 'en' && label.indexOf('eng') >= 0) ||
                (code === 'es' && label.indexOf('spa') >= 0)) {
                track.mode = 'showing';
                return true;
            }
        }
        return false;
    }

    function applySubtitles(value) {
        if (applyJwSubtitles(value)) return true;
        if (applyVideoJsSubtitles(value)) return true;
        return false;
    }

    function applyPreference(command) {
        if (!command) return;
        var quality = command.quality;
        var subtitles = command.subtitles;
        if (typeof quality !== 'string' && command.action === 'quality') quality = command.value;
        if (typeof subtitles !== 'string' && command.action === 'subtitles') subtitles = command.value;

        if (typeof quality === 'string') {
            pendingQuality = quality;
            if (applyQuality(quality)) pendingQuality = null;
        }
        if (typeof subtitles === 'string') {
            pendingSubtitles = subtitles;
            if (applySubtitles(subtitles)) pendingSubtitles = null;
        }
    }

    if (window.__cluPlayerPreferenceListener) {
        window.removeEventListener('message', window.__cluPlayerPreferenceListener);
    }
    window.__cluPlayerPreferenceListener = function (event) {
        var command = event && event.data;
        if (!command || command.type !== 'CLUTUBE_PLAYER_PREFERENCE') return;
        applyPreference(command);
    };
    window.addEventListener('message', window.__cluPlayerPreferenceListener);

    if (window.__cluPreferenceRetry) clearInterval(window.__cluPreferenceRetry);
    window.__cluPreferenceRetry = setInterval(function () {
        if (pendingQuality !== null && applyQuality(pendingQuality)) pendingQuality = null;
        if (pendingSubtitles !== null && applySubtitles(pendingSubtitles)) pendingSubtitles = null;
    }, 250);
})();
"""

    /** Reports the provider's control-bar visibility to the outer WebView. */
    private const val PLAYER_UI_RUNTIME = """
(function () {
    'use strict';
    var lastVisibility = null;

    function playerRoot() {
        return document.querySelector('.jw') ||
            document.getElementById('player') ||
            document.querySelector('.video-js');
    }

    function controlsVisible(root) {
        if (!root) return null;
        var classes = String(root.className || '');
        if (classes.indexOf('jw-flag-user-inactive') >= 0 ||
            classes.indexOf('vjs-user-inactive') >= 0) return false;
        if (classes.indexOf('show-ui') >= 0 ||
            classes.indexOf('vjs-user-active') >= 0) return true;

        // A provider build without a state class is considered hidden until
        // it exposes a visible control bar or receives a user interaction.
        var controls = root.querySelector &&
            (root.querySelector('.jw-controls') || root.querySelector('.vjs-control-bar'));
        if (!controls) return false;
        var style = window.getComputedStyle ? window.getComputedStyle(controls) : null;
        if (!style) return false;
        return style.display !== 'none' &&
            style.visibility !== 'hidden' &&
            Number(style.opacity || 1) > 0.01;
    }

    function report() {
        var visible = controlsVisible(playerRoot());
        if (visible === null || visible === lastVisibility) return;
        lastVisibility = visible;
        try {
            var target = window.parent || window;
            target.postMessage({ type: 'CLUTUBE_PLAYER_UI', visible: visible }, '*');
        } catch (_) {}
    }

    report();
    setTimeout(report, 100);
    setTimeout(report, 500);
    setTimeout(report, 1500);
    if (window.MutationObserver && document.documentElement) {
        new MutationObserver(report).observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['class', 'style'],
            childList: true,
            subtree: true
        });
    }
    setInterval(report, 250);
})();
"""

    /** CSS is limited to player classes and never changes provider page globals. */
    const val YOUTUBE_PLAYER_CSS = """
/* CluTube YouTube-style player skin */
.jw {
    --accent: #ff0000;
    --ctrl: #ffffff;
}

.jw .jw-gradient {
    display: block;
    height: 180px;
    background: linear-gradient(to top, rgba(0, 0, 0, 0.82), rgba(0, 0, 0, 0));
}

.jw .jw-controls {
    left: 0;
    right: 0;
    bottom: 0;
    padding: 0 12px 8px;
    border: 0;
    border-radius: 0;
    background: linear-gradient(to top, rgba(0, 0, 0, 0.86), rgba(0, 0, 0, 0));
    box-shadow: none;
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
}

.jw .jw-seek {
    height: 20px;
}

.jw .jw-rail {
    height: 3px;
    border-radius: 2px;
    background: rgba(255, 255, 255, 0.42);
}

.jw .jw-seek:hover .jw-rail {
    height: 4px;
}

.jw .jw-buffered {
    background: rgba(255, 255, 255, 0.56);
}

.jw .jw-played {
    background: #ff0000;
}

.jw .jw-knob {
    width: 12px;
    height: 12px;
    margin-left: -6px;
    background: #ff0000;
    border: 2px solid #ffffff;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.45);
}

.jw .jw-btn {
    width: 38px;
    height: 38px;
    color: #ffffff;
    border-radius: 3px;
    opacity: 0.94;
}

/* YouTube keeps captions and settings in the upper-right action cluster. */
.jw #ccBtn,
.jw #setBtn {
    position: fixed;
    top: 12px;
    z-index: 20;
}

.jw #ccBtn {
    right: 58px;
}

.jw #setBtn {
    right: 14px;
}

/* Fallback for provider builds that render the action buttons outside the
   .jw class selector. The runtime ad shield also moves them into a top dock. */
#clu-top-actions {
    opacity: 0 !important;
    visibility: hidden !important;
    pointer-events: none !important;
    transition: opacity 0.2s ease, visibility 0.2s ease;
}

.jw.show-ui #clu-top-actions,
#player.show-ui #clu-top-actions {
    opacity: 1 !important;
    visibility: visible !important;
    pointer-events: auto !important;
}

#ccBtn,
#setBtn {
    position: fixed !important;
    top: 10px !important;
    bottom: auto !important;
    left: auto !important;
    margin: 0 !important;
    z-index: 40 !important;
}

#ccBtn {
    right: 54px !important;
}

#setBtn {
    right: 10px !important;
}

/* Let the Android device volume buttons control playback volume. */
.jw .jw-vol,
.jw #mute,
.jw .jw-volume {
    display: none !important;
}

.jw .jw-btn:hover,
.jw .jw-btn:focus-visible {
    background: rgba(255, 255, 255, 0.16);
    opacity: 1;
}

.jw .jw-btn svg {
    filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.45));
}

.jw .jw-time {
    color: #ffffff;
    font-weight: 400;
}

.jw .jw-volume {
    accent-color: #ffffff;
}

.jw .jw-title {
    top: 0;
    left: 0;
    right: 0;
    padding: 14px 16px 38px;
    border: 0;
    border-radius: 0;
    color: #ffffff;
    text-shadow: 0 1px 3px rgba(0, 0, 0, 0.7);
    background: linear-gradient(to bottom, rgba(0, 0, 0, 0.72), rgba(0, 0, 0, 0));
    box-shadow: none;
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
}

.jw .jw-dd,
.jw .jw-style-sel,
.jw .jw-sync-btn,
.jw .jw-style-reset {
    color: #ffffff;
    background: #282828;
    border-color: rgba(255, 255, 255, 0.16);
}

.jw .jw-menu {
    top: 58px !important;
    bottom: auto !important;
    max-height: calc(100% - 70px) !important;
    height: auto !important;
    background: rgba(28, 28, 28, 0.98);
    color: #ffffff;
    border: 1px solid rgba(255, 255, 255, 0.14);
    border-radius: 12px;
    box-shadow: 0 8px 28px rgba(0, 0, 0, 0.55);
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
}

.jw .jw-menu-head {
    color: #ffffff;
    background: rgba(0, 0, 0, 0.22);
}

.jw .jw-menu-close {
    color: #ffffff;
}

.jw .jw-menu-close:hover {
    color: #ffffff;
    background: rgba(255, 255, 255, 0.12);
}

.jw .jw-style-row,
.jw .jw-sync-hint {
    color: #d6d6d6;
}

.jw .jw-style-row > span {
    color: #aaaaaa;
}

.jw .jw-style-reset:hover,
.jw .jw-sync-btn:hover {
    background: #3a3a3a;
}

.jw.landing .jw-bigplay,
.jw .jw-bigplay {
    color: #ffffff;
    border: 0;
    background: transparent;
    box-shadow: none;
    -webkit-backdrop-filter: none;
    backdrop-filter: none;
}

.jw.landing .jw-bigplay:hover,
.jw .jw-bigplay:hover {
    background: transparent;
}

@media (max-width: 480px), (max-height: 420px) {
    .jw .jw-controls {
        padding-left: 8px;
        padding-right: 8px;
        padding-bottom: 5px;
    }

    .jw .jw-title {
        padding: 10px 12px 30px;
    }

    .jw #ccBtn {
        top: 8px;
        right: 48px;
    }

    .jw #setBtn {
        top: 8px;
        right: 6px;
    }

    .jw .jw-menu {
        top: 54px !important;
        right: 8px !important;
        bottom: auto !important;
        left: auto !important;
        width: min(270px, calc(100% - 16px));
        max-height: calc(100% - 62px) !important;
        border-radius: 10px;
    }
}
"""
}

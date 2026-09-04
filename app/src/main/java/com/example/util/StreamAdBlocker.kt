package com.example.util

import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream

/**
 * High-performance AdBlock and anti-redirect shield for streaming WebViews.
 * Blocks invasive ad networks, tracking scripts, malicious popups, and clickjacking overlays
 * while ensuring all legitimate stream provider CDNs, video chunks, and API endpoints load freely.
 */
object StreamAdBlocker {

    // Only the configured VidSrc mirrors, VidLink Pro, and player/media infrastructure.
    private val ALLOWED_STREAM_DOMAINS = setOf(
        "vidsrc2.ru",
        "vidsrc.ir",
        "vidsrcme.ru",
        "vidsrcme.su",
        "vidsrc-me.ru",
        "vidsrc-me.su",
        "vidsrc-embed.ru",
        "vidsrc-embed.su",
        "vsrc.su",
        "vsembed.ru",
        "data.vidsrcme.ru",
        "cloudorchestranova.com",
        "vidlink.pro",
        "vjs.zencdn.net",
        "themoviedb.org",
        "tmdb.org",
        "zencdn.net",
        "cloudflare.com",
        "jsdelivr.net",
        "unpkg.com",
        "googleapis.com",
        "gstatic.com"
    )

    // Known invasive ad networks, popup spinners, and malicious redirect engines
    private val BLOCKED_AD_DOMAINS = setOf(
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "adnxs.com",
        "adsterra.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "monetag.com",
        "adcash.com",
        "exoclick.com",
        "trafficjunky.net",
        "juicyads.com",
        "onclickadu.com",
        "yllix.com",
        "clickadu.com",
        "bet365.com",
        "1xbet.com",
        "melbet.com",
        "ad-maven.com",
        "histats.com",
        "statcounter.com",
        "scorecardresearch.com",
        "outbrain.com",
        "taboola.com",
        "mgid.com",
        "cpmstar.com",
        "trafficfactory.biz",
        "zergnet.com",
        "revcontent.com",
        "tsyndicate.com",
        "adtrue.com",
        "a-ads.com",
        "hilltopads.net",
        "richpush.co",
        "evadav.com",
        "trafficstars.com",
        "adskeeper.co.uk",
        "adroll.com",
        "inmobi.com",
        "mowplayer.com",
        "whos.amung.us",
        "ads-twitter.com",
        "adcolony.com",
        "applovin.com",
        "vungle.com",
        "tapjoy.com",
        "ironsrc.com",
        "smartadserver.com",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "casalemedia.com",
        "criteo.com",
        "advertising.com",
        "adform.net",
        "yieldmo.com",
        "sharethrough.com",
        "spotxchange.com",
        "undertone.com",
        "infolinks.com",
        "chitika.net",
        "sovrn.com",
        "medianet.com",
        "media.net",
        "adblade.com",
        "bidvertiser.com",
        "popmyads.com",
        "adfly.com",
        "adf.ly",
        "shorte.st",
        "bc.vc",
        "ouo.io",
        "linkvertise.com",
        "linkvertise.net",
        "adguard.com",
        "coinhive.com",
        "coin-hive.com",
        "crypto-loot.com",
        "minr.pw",
        "webminepool.com"
    )

    /**
     * Checks whether a given network request URL is an ad, tracker, or disallowed redirect.
     */
    fun shouldBlockRequest(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lowerUrl = url.lowercase()

        // Always allow core video formats, streaming manifests, blob/data, and subtitle assets
        if (lowerUrl.startsWith("blob:") || lowerUrl.startsWith("data:") ||
            lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4") || lowerUrl.contains(".ts") ||
            lowerUrl.contains(".mpd") || lowerUrl.contains(".vtt") || lowerUrl.contains(".srt") ||
            lowerUrl.contains("googlevideo.com")
        ) {
            return false
        }

        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: ""

            // If the request is from a whitelisted stream provider or trusted CDN, do NOT block
            for (allowed in ALLOWED_STREAM_DOMAINS) {
                if (host == allowed || host.endsWith(".$allowed")) {
                    return false
                }
            }

            // Check host domain against known ad server blacklist
            for (domain in BLOCKED_AD_DOMAINS) {
                if (host == domain || host.endsWith(".$domain")) {
                    return true
                }
            }
        } catch (_: Exception) {
            // Ignore parse errors
        }

        return false
    }

    /**
     * Checks if a navigation URL is a redirect trap (e.g. redirecting player to google search or ad page).
     */
    fun isRedirectTrap(url: String?, initialEmbedHost: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lowerUrl = url.lowercase()

        // Trap detection: redirects to search engines, blank pages, or external search traps
        if (lowerUrl.startsWith("https://www.google.com") ||
            lowerUrl.startsWith("http://www.google.com") ||
            lowerUrl.startsWith("https://google.com") ||
            lowerUrl.startsWith("http://google.com") ||
            lowerUrl.startsWith("https://www.bing.com") ||
            lowerUrl.startsWith("https://search.yahoo.com")
        ) {
            return true
        }

        // Non-http schemes that ads trigger (intent, market, whatsapp, tg, mailto, etc.)
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://") && !lowerUrl.startsWith("about:blank")) {
            return true
        }

        // Check if destination is explicitly in the ad domain blocklist
        try {
            val host = Uri.parse(url).host?.lowercase() ?: ""
            for (domain in BLOCKED_AD_DOMAINS) {
                if (host == domain || host.endsWith(".$domain")) {
                    return true
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Checks if the top-level navigation request is permitted to replace the main frame.
     * Prevents clicks on ad overlays from navigating the player away from the movie/show.
     */
    fun isAllowedStreamNavigation(url: String?, currentStreamUrl: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lowerUrl = url.lowercase()

        if (lowerUrl.startsWith("about:blank") || lowerUrl.startsWith("data:") || lowerUrl.startsWith("blob:")) {
            return true
        }

        try {
            val navHost = Uri.parse(url).host?.lowercase() ?: ""
            val streamHost = if (!currentStreamUrl.isNullOrBlank()) {
                Uri.parse(currentStreamUrl).host?.lowercase() ?: ""
            } else ""

            // Allow if navigating within the same host (e.g. a player route change).
            if (streamHost.isNotEmpty() && (navHost == streamHost || navHost.endsWith(".$streamHost"))) {
                return true
            }

            // Allow if navigating to an approved streaming provider or video host
            for (allowed in ALLOWED_STREAM_DOMAINS) {
                if (navHost == allowed || navHost.endsWith(".$allowed")) {
                    return true
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Returns an empty response to neutralize ad script downloads.
     */
    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * Injects uBlock Origin-style CSS rules, anti-popup protections, and clickjacking overlay killers.
     */
    fun injectAdblockProtection(webView: WebView) {
        val script = """
            (function() {
                // 1. Completely neutralize window.open and popup methods
                var dummyWindow = {
                    focus: function() {},
                    close: function() {},
                    closed: true,
                    location: { href: '' },
                    document: { write: function() {} }
                };
                window.open = function() {
                    console.log('[AdBlock] Blocked window.open popup');
                    return dummyWindow;
                };
                window.alert = function() {};
                window.confirm = function() { return false; };
                window.prompt = function() { return null; };

                // 2. Prevent navigation hijacking / onbeforeunload traps
                try {
                    window.onbeforeunload = null;
                    Object.defineProperty(window, 'onbeforeunload', {
                        configurable: false,
                        writable: false,
                        value: null
                    });
                } catch(e) {}

                // 3. Block clickjacking overlay links and fake play buttons
                document.addEventListener('click', function(e) {
                    var target = e.target;
                    var link = target && target.closest ? target.closest('a') : null;
                    if (link && link.href) {
                        var href = link.href.toLowerCase();
                        if (!href.startsWith('javascript:') && !href.startsWith('#')) {
                            var linkHost = link.hostname.toLowerCase();
                            var curHost = window.location.hostname.toLowerCase();
                            var isTrusted = linkHost === curHost ||
                                            linkHost === 'vidsrc2.ru' ||
                                            linkHost === 'vidsrc.ir' ||
                                            linkHost === 'vidsrcme.ru' ||
                                            linkHost === 'vidsrcme.su' ||
                                            linkHost === 'vidsrc-me.ru' ||
                                            linkHost === 'vidsrc-me.su' ||
                                            linkHost === 'vidsrc-embed.ru' ||
                                            linkHost === 'vidsrc-embed.su' ||
                                            linkHost === 'vsrc.su' ||
                                            linkHost === 'vsembed.ru' ||
                                            linkHost === 'cloudorchestranova.com' ||
                                            linkHost === 'vidlink.pro';
                            if (!isTrusted) {
                                e.preventDefault();
                                e.stopPropagation();
                                e.stopImmediatePropagation();
                                console.log('[AdBlock] Blocked click on ad link:', link.href);
                                return false;
                            }
                        }
                    }
                }, true);

                // Some VidSrc ad scripts place a transparent, full-player
                // click target above the real controls. Consume that first
                // tap and start the underlying video instead of requiring a
                // second tap after the ad layer has been hit.
                document.addEventListener('pointerdown', function(e) {
                    try {
                        var target = e.target;
                        var player = target && target.closest ? target.closest('.jwplayer, .jwplayer-container, #player, video') : null;
                        if (!player) return;
                        var isControl = target.closest && target.closest('.jw-controls, .jw-button-container, .jw-settings-menu, #ccBtn, #setBtn, #fsBtn, video');
                        if (isControl) return;
                        var video = player.tagName && player.tagName.toLowerCase() === 'video'
                            ? player : player.querySelector('video');
                        if (video) {
                            e.preventDefault();
                            e.stopImmediatePropagation();
                            video.play().catch(function() {});
                        }
                    } catch(err) {}
                }, true);

                // 4. Cosmetic Ad Filtering CSS
                var style = document.getElementById('clutube-adblock-styles');
                if (!style) {
                    style = document.createElement('style');
                    style.id = 'clutube-adblock-styles';
                    style.innerHTML = `
                        [id*="ad-"], [id*="ads-"], [id*="banner"], [id*="pop-"],
                        [class*="ad-"], [class*="ads-"], [class*="banner"], [class*="popup"],
                        iframe[src*="doubleclick"], iframe[src*="popcash"], iframe[src*="adsterra"], iframe[src*="monetag"],
                        iframe[src*="onclickadu"], iframe[src*="clickadu"], iframe[src*="exoclick"],
                        div[style*="z-index: 2147483647"]:not(#player):not([class*="player"]):not([class*="video"]),
                        div[style*="z-index: 9999999"]:not(#player):not([class*="player"]):not([class*="video"]),
                        .click-tracker, .ad-placement, #ad-container, .adsbygoogle {
                            display: none !important;
                            visibility: hidden !important;
                            pointer-events: none !important;
                            opacity: 0 !important;
                            width: 0 !important;
                            height: 0 !important;
                        }
                    `;
                    document.head.appendChild(style);
                }

                // 5. Remove transparent clickjacking overlays covering video containers
                function removeOverlays() {
                    try {
                        var overlays = document.querySelectorAll('div[style*="z-index"][style*="position: absolute"], div[style*="z-index"][style*="position: fixed"]');
                        overlays.forEach(function(el) {
                            var style = window.getComputedStyle(el);
                            var isVideoContainer = el.querySelector('video') || el.classList.contains('jwplayer') || el.id === 'player' || el.classList.contains('plyr');
                            if (!isVideoContainer && (style.opacity === '0' || style.backgroundColor === 'transparent' || style.backgroundColor === 'rgba(0, 0, 0, 0)')) {
                                var zIndex = parseInt(style.zIndex, 10);
                                if (zIndex > 50) {
                                    el.remove();
                                }
                            }
                        });
                    } catch(err) {}
                }

                // The provider puts these buttons in the bottom control row.
                // Move them into their own overlay so their location cannot be
                // changed by the provider's flex layout or ad markup.
                function pinTopActions() {
                    try {
                        var player = document.querySelector('.jw') || document.getElementById('player');
                        var cc = document.getElementById('ccBtn');
                        var settings = document.getElementById('setBtn');
                        if (!player || !cc || !settings) return;
                        var dock = document.getElementById('clu-top-actions');
                        if (!dock) {
                            dock = document.createElement('div');
                            dock.id = 'clu-top-actions';
                            player.appendChild(dock);
                        }
                        dock.style.setProperty('position', 'absolute', 'important');
                        dock.style.setProperty('top', '10px', 'important');
                        dock.style.setProperty('right', '10px', 'important');
                        dock.style.setProperty('z-index', '40', 'important');
                        dock.style.setProperty('display', 'flex', 'important');
                        dock.style.setProperty('gap', '2px', 'important');
                        dock.style.setProperty('align-items', 'center', 'important');
                        if (cc.parentElement !== dock) dock.appendChild(cc);
                        if (settings.parentElement !== dock) dock.appendChild(settings);
                        [cc, settings].forEach(function(button) {
                            button.style.setProperty('position', 'static', 'important');
                            button.style.setProperty('top', 'auto', 'important');
                            button.style.setProperty('right', 'auto', 'important');
                            button.style.setProperty('bottom', 'auto', 'important');
                            button.style.setProperty('left', 'auto', 'important');
                            button.style.setProperty('margin', '0', 'important');
                        });
                    } catch(err) {}
                }

                // 6. Auto-Play helper for the provider's native HTML5 player.
                function enableAutoplayOnFrames() {
                    try {
                        var frames = document.querySelectorAll('iframe');
                        for (var frameIndex = 0; frameIndex < frames.length; frameIndex++) {
                            var frame = frames[frameIndex];
                            var allow = frame.getAttribute('allow') || '';
                            if (allow.toLowerCase().indexOf('autoplay') === -1) {
                                frame.setAttribute('allow', allow ? allow + '; autoplay' : 'autoplay');
                            }
                        }
                    } catch(e) {}
                }

                function preferAudiblePlayback(video) {
                    if (!video) return;
                    try {
                        video.defaultMuted = false;
                        video.muted = false;
                        video.removeAttribute('muted');
                        video.volume = 1;
                    } catch(e) {}
                }

                function playNativeVideo(video) {
                    try {
                        if (!video || video.__cluAutoplayAttempted || video.readyState < 2) return;
                        preferAudiblePlayback(video);
                        video.autoplay = true;
                        video.playsInline = true;
                        if (!video.paused) {
                            video.__cluAutoplayAttempted = true;
                            return;
                        }

                        video.__cluAutoplayAttempted = true;
                        var playPromise = video.play();
                        if (playPromise && typeof playPromise.catch === 'function') {
                            playPromise.catch(function() {
                                // Keep the requested audible state. If Android
                                // blocks audible autoplay, the provider's own
                                // play control can start playback after a tap.
                                preferAudiblePlayback(video);
                            });
                        }
                    } catch(e) {}
                }

                function tryAutoPlay() {
                    try {
                        var videos = document.querySelectorAll('video');
                        for (var videoIndex = 0; videoIndex < videos.length; videoIndex++) {
                            playNativeVideo(videos[videoIndex]);
                        }
                    } catch(e) {}
                }

                enableAutoplayOnFrames();
                removeOverlays();
                pinTopActions();
                tryAutoPlay();
                if (window.MutationObserver && !window.__cluAutoplayObserver) {
                    window.__cluAutoplayObserver = new MutationObserver(function() {
                        enableAutoplayOnFrames();
                        pinTopActions();
                        tryAutoPlay();
                    });
                    if (document.documentElement) {
                        window.__cluAutoplayObserver.observe(document.documentElement, {
                            childList: true,
                            subtree: true
                        });
                    }
                }
                setTimeout(removeOverlays, 800);
                setTimeout(pinTopActions, 800);
                setTimeout(tryAutoPlay, 1000);
                setTimeout(removeOverlays, 2500);
                setTimeout(pinTopActions, 2500);
                setTimeout(tryAutoPlay, 3000);
            })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }
}


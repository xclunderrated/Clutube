package com.example.util

/** JavaScript injected into provider pages to expose HTML5 playback state. */
internal object PlaybackScript {
    fun build(
        generation: Long,
        resumePositionSeconds: Double,
        autoplay: Boolean = true,
        preferredQuality: String = "auto",
        preferredSubtitles: String = "off"
    ): String {
        val safeResumePosition = resumePositionSeconds
            .takeIf { it.isFinite() && it >= 0.0 }
            ?: 0.0
        val safeQuality = preferredQuality.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "auto" }
        val safeSubtitles = preferredSubtitles.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "off" }

        return """
            (function() {
                var generation = $generation;
                var resumePosition = $safeResumePosition;
                var shouldAutoplay = $autoplay;
                var preferredQuality = '$safeQuality';
                var preferredSubtitles = '$safeSubtitles';

                function getBridge() {
                    return window.AndroidPlayerBridge;
                }

                function numberOrZero(value) {
                    if (typeof value === 'string') {
                        var text = value.trim();
                        if (text.indexOf(':') >= 0) {
                            var parts = text.split(':').map(Number);
                            if (parts.every(function(part) { return isFinite(part) && part >= 0; })) {
                                var total = 0;
                                for (var partIndex = 0; partIndex < parts.length; partIndex++) {
                                    total = total * 60 + parts[partIndex];
                                }
                                return isFinite(total) && total >= 0 ? total : 0;
                            }
                        }
                        value = text.replace(',', '.');
                    }
                    var number = Number(value);
                    return isFinite(number) && number >= 0 ? number : 0;
                }

                var resumeSettled = resumePosition <= 1;
                var resumeGuardStartedAt = Date.now();

                function shouldSuppressResumeReset(position, duration) {
                    if (resumeSettled || resumePosition <= 1) return false;
                    if (position + 2 >= resumePosition) {
                        resumeSettled = true;
                        return false;
                    }
                    // VidLink emits a zero/early event while its player is
                    // mounting. Do not let that event erase a valid resume
                    // point. If the real source is shorter, release the guard
                    // after a few seconds and clamp normally.
                    if (Date.now() - resumeGuardStartedAt < 5000) return true;
                    if (duration > 0 && duration <= resumePosition + 1) {
                        resumeSettled = true;
                    }
                    return false;
                }

                function emitSnapshot(video, force) {
                    var bridge = getBridge();
                    if (!video || !bridge || typeof bridge.onPlaybackSnapshot !== 'function') return;
                    var now = Date.now();
                    if (!force && video.__cluLastSnapshotAt && now - video.__cluLastSnapshotAt < 750) return;
                    video.__cluLastSnapshotAt = now;
                    var position = numberOrZero(video.currentTime);
                    var duration = numberOrZero(video.duration);
                    if (shouldSuppressResumeReset(position, duration)) return;
                    try {
                        bridge.onPlaybackSnapshot(
                            position,
                            duration,
                            !video.paused,
                            !!video.muted,
                            generation
                        );
                    } catch (_) {}
                }

                function providerEventData(event) {
                    var payload = event && event.data;
                    if (typeof payload === 'string') {
                        try { payload = JSON.parse(payload); } catch (_) { return null; }
                    }
                    if (!payload || payload.type !== 'PLAYER_EVENT' || !payload.data) return null;
                    return payload.data;
                }

                function emitProviderUiEvent(event) {
                    var payload = event && event.data;
                    if (typeof payload === 'string') {
                        try { payload = JSON.parse(payload); } catch (_) { return; }
                    }
                    if (!payload || payload.type !== 'CLUTUBE_PLAYER_UI') return;
                    var bridge = getBridge();
                    if (!bridge || typeof bridge.onPlayerUiVisibilityChanged !== 'function') return;
                    var visible = payload.visible === true || payload.visible === 'true';
                    try { bridge.onPlayerUiVisibilityChanged(visible, generation); } catch (_) {}
                }

                function emitProviderEvent(event) {
                    var data = providerEventData(event);
                    if (!data) return;
                    var rawStatus = String(data.player_status || data.event || '').toLowerCase();
                    var status = rawStatus;
                    if (rawStatus === 'play' || rawStatus === 'timeupdate') status = 'playing';
                    if (rawStatus === 'pause') status = 'paused';
                    if (rawStatus === 'seek') status = 'seeked';
                    if (rawStatus === 'ended' || rawStatus === 'complete') status = 'completed';
                    if (status !== 'playing' && status !== 'paused' &&
                        status !== 'seeked' && status !== 'completed') return;

                    var bridge = getBridge();
                    if (!bridge) return;
                    var position = numberOrZero(
                        data.player_progress !== undefined ? data.player_progress : data.currentTime
                    );
                    var duration = numberOrZero(
                        data.player_duration !== undefined ? data.player_duration : data.duration
                    );
                    if (shouldSuppressResumeReset(position, duration)) return;
                    if (typeof bridge.onPlaybackSnapshot === 'function') {
                        try {
                            bridge.onPlaybackSnapshot(
                                position,
                                duration,
                                status === 'playing',
                                false,
                                generation
                            );
                        } catch (_) {}
                    }
                    if (status === 'completed' && typeof bridge.onVideoEnded === 'function') {
                        try { bridge.onVideoEnded(generation); } catch (_) {}
                    }
                }

                function applyResume(video) {
                    if (!video) return false;
                    var duration = Number(video.duration);
                    if (!isFinite(duration) || duration <= 0) return false;
                    var appliedDuration = Number(video.__cluResumeDuration);
                    var recentlyApplied = video.__cluResumeGeneration === generation &&
                        isFinite(appliedDuration) && appliedDuration > 0;
                    if (recentlyApplied &&
                        (Math.abs(duration - appliedDuration) / Math.max(duration, appliedDuration) < 0.05 ||
                            video.currentTime >= resumePosition - 2 ||
                            Date.now() - video.__cluResumeAppliedAt > 10000)) {
                        return true;
                    }
                    if (resumePosition > 1 && duration <= resumePosition + 1 &&
                        Date.now() - resumeGuardStartedAt < 5000) return false;
                    var safePosition = Math.min(resumePosition, Math.max(0, duration - 0.25));
                    try {
                        if (safePosition > 0) video.currentTime = safePosition;
                        video.__cluResumeGeneration = generation;
                        video.__cluResumeDuration = duration;
                        video.__cluResumeAppliedAt = Date.now();
                        emitSnapshot(video, true);
                        return true;
                    } catch (_) {
                        return false;
                    }
                }

                function preferAudiblePlayback(video) {
                    if (!video) return;
                    try {
                        video.defaultMuted = false;
                        video.muted = false;
                        video.removeAttribute('muted');
                        video.volume = 1;
                    } catch (_) {}
                }

                function getEmbeddedJwPlayer() {
                    if (typeof window.jwplayer !== 'function') return null;
                    var host = document.querySelector('.jwplayer');
                    try {
                        if (host) {
                            var hosted = window.jwplayer(host);
                            if (hosted) return hosted;
                        }
                    } catch (_) {}
                    try { return window.jwplayer(); } catch (_) { return null; }
                }

                function levelHeight(level) {
                    if (!level) return 0;
                    var height = Number(level.height || 0);
                    if (height > 0) return height;
                    var label = String(level.label || level.name || '');
                    var match = label.match(/(2160|1440|1080|720|480|360|240|144)/);
                    return match ? Number(match[1]) : 0;
                }

                function applyJwQuality(player, value) {
                    if (!player || typeof player.getQualityLevels !== 'function' ||
                        typeof player.setCurrentQuality !== 'function') return false;
                    var levels;
                    try { levels = player.getQualityLevels() || []; } catch (_) { return false; }
                    if (!levels.length) return false;
                    var text = String(value || 'auto').toLowerCase();
                    if (text === 'auto') {
                        try { player.setCurrentQuality(-1); return true; } catch (_) { return false; }
                    }
                    var requested = parseInt(text, 10);
                    if (!isFinite(requested)) return false;
                    var selected = -1;
                    var closestDistance = Infinity;
                    for (var index = 0; index < levels.length; index++) {
                        var height = levelHeight(levels[index]);
                        var distance = Math.abs(height - requested);
                        if (height === requested) { selected = index; break; }
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            selected = index;
                        }
                    }
                    if (selected < 0) return false;
                    try { player.setCurrentQuality(selected); return true; } catch (_) { return false; }
                }

                function applyJwSubtitles(player, value) {
                    var text = String(value || 'off').toLowerCase();
                    var requested = text === 'en' ? 'english' : (text === 'es' ? 'spanish' : text);
                    if (player && typeof player.getCaptionsList === 'function' &&
                        typeof player.setCurrentCaptions === 'function') {
                        var tracks;
                        try { tracks = player.getCaptionsList() || []; } catch (_) { tracks = []; }
                        if (tracks.length) {
                            if (requested === 'off') {
                                try { player.setCurrentCaptions(0); return true; } catch (_) {}
                            } else {
                                for (var index = 0; index < tracks.length; index++) {
                                    var track = tracks[index] || {};
                                    var label = String(track.label || track.language || track.name || '').toLowerCase();
                                    if (label.indexOf(requested) >= 0 ||
                                        (requested === 'english' && label.indexOf('eng') >= 0) ||
                                        (requested === 'spanish' && label.indexOf('spa') >= 0)) {
                                        try { player.setCurrentCaptions(index); return true; } catch (_) {}
                                    }
                                }
                            }
                        }
                    }

                    var video = document.querySelector('video');
                    if (!video || !video.textTracks) return requested === 'off';
                    for (var trackIndex = 0; trackIndex < video.textTracks.length; trackIndex++) {
                        var nativeTrack = video.textTracks[trackIndex];
                        var nativeLabel = String(nativeTrack.label || nativeTrack.language || '').toLowerCase();
                        if (requested === 'off') nativeTrack.mode = 'disabled';
                        else if (nativeLabel.indexOf(requested) >= 0 ||
                            (requested === 'english' && nativeLabel.indexOf('eng') >= 0) ||
                            (requested === 'spanish' && nativeLabel.indexOf('spa') >= 0)) {
                            nativeTrack.mode = 'showing';
                            return true;
                        }
                    }
                    return requested === 'off';
                }

                var lastPreference = null;
                function applyProviderPreference(preference) {
                    if (!preference) return;
                    var quality = preference.quality;
                    var subtitles = preference.subtitles;
                    if (typeof quality !== 'string' && preference.action === 'quality') quality = preference.value;
                    if (typeof subtitles !== 'string' && preference.action === 'subtitles') subtitles = preference.value;
                    if (typeof quality === 'string') preferredQuality = quality;
                    if (typeof subtitles === 'string') preferredSubtitles = subtitles;
                    lastPreference = {
                        quality: preferredQuality,
                        subtitles: preferredSubtitles
                    };

                    var jwPlayer = getEmbeddedJwPlayer();
                    if (jwPlayer) {
                        applyJwQuality(jwPlayer, preferredQuality);
                        applyJwSubtitles(jwPlayer, preferredSubtitles);
                    }
                    document.querySelectorAll('video').forEach(function(video) {
                        try {
                            video.__cluPreferredQuality = preferredQuality;
                            video.__cluPreferredSubtitles = preferredSubtitles;
                        } catch (_) {}
                    });
                }

                function broadcastPlaybackPreference() {
                    var preference = {
                        type: 'CLUTUBE_PLAYER_PREFERENCE',
                        quality: preferredQuality,
                        subtitles: preferredSubtitles
                    };
                    try {
                        document.querySelectorAll('iframe').forEach(function(frame) {
                            if (frame.contentWindow) frame.contentWindow.postMessage(preference, '*');
                        });
                        if (window.parent && window.parent !== window) {
                            window.parent.postMessage(preference, '*');
                        }
                    } catch (_) {}
                }

                function applyPlaybackPreferences(video) {
                    if (!video) return;
                    try {
                        video.__cluPreferredQuality = preferredQuality;
                        video.__cluPreferredSubtitles = preferredSubtitles;
                    } catch (_) {}
                    applyProviderPreference({
                        quality: preferredQuality,
                        subtitles: preferredSubtitles
                    });
                    broadcastPlaybackPreference();
                }

                function attemptAutoplay(video) {
                    if (!shouldAutoplay) {
                        try {
                            video.autoplay = false;
                            video.pause();
                        } catch (_) {}
                        return;
                    }
                    if (!video || video.readyState < 2 ||
                        video.__cluAutoplayGeneration === generation) return;

                    preferAudiblePlayback(video);
                    video.autoplay = true;
                    var playPromise;
                    try {
                        playPromise = video.play();
                    } catch (_) {
                        return;
                    }

                    video.__cluAutoplayGeneration = generation;
                    if (playPromise && typeof playPromise.catch === 'function') {
                        playPromise.catch(function() {
                            // Do not silently switch to muted playback. If the
                            // WebView blocks audible autoplay, leave the video
                            // unmuted so the provider's play control can start it
                            // with sound after the user's tap.
                            preferAudiblePlayback(video);
                        });
                    }
                }

                function hookVideo(video) {
                    if (!video) return;
                    if (video.__cluPlaybackGeneration === generation) {
                        applyResume(video);
                        emitSnapshot(video, false);
                        return;
                    }

                    video.__cluPlaybackGeneration = generation;
                    applyPlaybackPreferences(video);
                    ['loadedmetadata', 'durationchange', 'canplay', 'timeupdate', 'play', 'pause', 'seeked', 'volumechange'].forEach(function(name) {
                        video.addEventListener(name, function() {
                            if (name === 'loadedmetadata') applyResume(video);
                            if (name === 'canplay') attemptAutoplay(video);
                            emitSnapshot(video, name !== 'timeupdate');
                        });
                    });
                    video.addEventListener('ended', function() {
                        emitSnapshot(video, true);
                        var bridge = getBridge();
                        if (bridge && typeof bridge.onVideoEnded === 'function') {
                            try { bridge.onVideoEnded(generation); } catch (_) {}
                        }
                    });

                    applyResume(video);
                    preferAudiblePlayback(video);
                    attemptAutoplay(video);
                    emitSnapshot(video, true);
                }

                function hookVideos() {
                    var videos = document.querySelectorAll('video');
                    var hasUsableVideo = false;
                    for (var index = 0; index < videos.length; index++) {
                        hookVideo(videos[index]);
                        // Keep the host loading veil up until the media can
                        // actually provide frames, rather than hiding it as
                        // soon as VidLink exposes only metadata.
                        if (videos[index].readyState >= 2) {
                            hasUsableVideo = true;
                        }
                    }
                    return hasUsableVideo;
                }

                window.__cluReportPlayback = function() {
                    var videos = document.querySelectorAll('video');
                    for (var index = 0; index < videos.length; index++) emitSnapshot(videos[index], true);
                };

                if (window.__cluProviderMessageListener) {
                    window.removeEventListener('message', window.__cluProviderMessageListener);
                }
                window.__cluProviderMessageListener = function(event) {
                    try {
                        emitProviderEvent(event);
                        emitProviderUiEvent(event);
                    } catch (_) {}
                };
                window.addEventListener('message', window.__cluProviderMessageListener);

                function applyPlayerCommand(video, command) {
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
                        } else if (command.action === 'quality') {
                            video.__cluPreferredQuality = String(command.value || 'auto');
                        } else if (command.action === 'subtitles') {
                            video.__cluPreferredSubtitles = String(command.value || 'off');
                        }
                    } catch (_) {}
                }

                if (window.__cluPlayerCommandListener) {
                    window.removeEventListener('message', window.__cluPlayerCommandListener);
                }
                window.__cluPlayerCommandListener = function(event) {
                    var command = event && event.data;
                    if (!command || command.type !== 'CLUTUBE_PLAYER_COMMAND') return;
                    document.querySelectorAll('video').forEach(function(video) {
                        applyPlayerCommand(video, command);
                    });
                };
                window.addEventListener('message', window.__cluPlayerCommandListener);

                if (window.__cluPreferenceListener) {
                    window.removeEventListener('message', window.__cluPreferenceListener);
                }
                window.__cluPreferenceListener = function(event) {
                    var preference = event && event.data;
                    if (!preference || preference.type !== 'CLUTUBE_PLAYER_PREFERENCE') return;
                    applyProviderPreference(preference);
                    document.querySelectorAll('video').forEach(applyPlaybackPreferences);
                    broadcastPlaybackPreference();
                };
                window.addEventListener('message', window.__cluPreferenceListener);

                window.__cluApplyPlaybackPreference = applyProviderPreference;
                broadcastPlaybackPreference();

                if (window.__cluPlaybackInterval) clearInterval(window.__cluPlaybackInterval);
                var readyReported = false;
                function reportReadyWhenUsable() {
                    if (readyReported || !hookVideos()) return;
                    readyReported = true;
                    var readyBridge = getBridge();
                    if (readyBridge && typeof readyBridge.onPlayerReady === 'function') {
                        try { readyBridge.onPlayerReady(generation); } catch (_) {}
                    }
                }
                reportReadyWhenUsable();
                window.__cluPlaybackInterval = setInterval(function() {
                    hookVideos();
                    if (lastPreference) applyProviderPreference(lastPreference);
                    broadcastPlaybackPreference();
                    reportReadyWhenUsable();
                    window.__cluReportPlayback();
                }, 1000);
            })();
        """.trimIndent()
    }
}

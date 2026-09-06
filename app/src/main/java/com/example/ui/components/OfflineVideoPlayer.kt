package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.YouTubeRed
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * Full-screen offline video player for playing locally downloaded movies and episodes.
 * Functions 100% offline with zero network connectivity.
 */
@Composable
fun OfflineVideoPlayer(
    title: String,
    subtitle: String?,
    localFilePath: String,
    serverName: String? = null,
    subtitleCc: String? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    // Real sidecar CC (V1): path to a downloaded .srt/.vtt file. When present
    // and CC is on, cues render in an overlay above the controls.
    subtitleFilePath: String? = null,
    // Persisted A/V sync correction from the download row.
    initialSubtitleOffsetMs: Long = 0L,
    // Called (debounced by the caller if needed) when the user changes sync.
    onSubtitleOffsetChanged: ((Long) -> Unit)? = null,
    // Called when the user taps "Get subtitles" with no sidecar present.
    onRequestSubtitles: (() -> Unit)? = null,
    // ISO 639-1 language for the sidecar / preferred embedded track.
    subtitleLanguage: String? = null,
    // Persisted selected track id ("sidecar", "emb:<group>:<track>", null).
    initialTrackId: String? = null,
    // Called when the user picks a different subtitle track.
    onSubtitleTrackChanged: ((String?) -> Unit)? = null
) {
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var ccToastMessage by remember { mutableStateOf<String?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var subtitleOffsetMs by remember(subtitleFilePath) { mutableStateOf(initialSubtitleOffsetMs) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var showTrackPicker by remember { mutableStateOf(false) }
    var embeddedTracks by remember { mutableStateOf(emptyList<OfflineTextTrack>()) }

    val context = LocalContext.current
    val sidecarLang = remember(subtitleLanguage) {
        com.example.data.subtitles.normalizeSubtitleLanguage(subtitleLanguage).takeIf { it != "off" } ?: "en"
    }

    // Sidecar validity: only a parseable file is offered as a track.
    val hasSidecar = remember(subtitleFilePath) {
        val path = subtitleFilePath
        if (path.isNullOrBlank()) false
        else runCatching {
            val f = File(path)
            f.exists() && f.length() > 0 && com.example.data.subtitles.SrtSync.parse(
                com.example.data.subtitles.SrtSync.decodeBytes(f.readBytes())
            ).isNotEmpty()
        }.getOrDefault(false)
    }

    // Null = subtitles off. Defaults to the sidecar when one exists.
    var selectedTrackId by remember(subtitleFilePath) {
        mutableStateOf(initialTrackId ?: if (hasSidecar) TRACK_ID_SIDECAR else null)
    }
    val isCcActive = selectedTrackId != null

    // Effective sidecar URI: the stored file at offset 0, otherwise a shifted
    // temp copy in cache (ExoPlayer has no delay API, so cues are rewritten).
    // The stored file is never modified; the offset lives on the DB row.
    val sidecarUri = remember(subtitleFilePath, subtitleOffsetMs) {
        val path = subtitleFilePath
        if (path.isNullOrBlank()) null
        else if (subtitleOffsetMs == 0L) Uri.fromFile(File(path))
        else runCatching {
            val src = File(path)
            val shifted = com.example.data.subtitles.SrtSync.shift(
                com.example.data.subtitles.SrtSync.parse(
                    com.example.data.subtitles.SrtSync.decodeBytes(src.readBytes())
                ),
                subtitleOffsetMs
            )
            if (shifted.isEmpty()) {
                Uri.fromFile(src)
            } else {
                val dir = File(context.cacheDir, "subs_shifted").apply { mkdirs() }
                val ext = src.extension.lowercase().takeIf { it.isNotBlank() } ?: "srt"
                val out = File(dir, "${src.nameWithoutExtension}_${subtitleOffsetMs}.$ext")
                if (!out.exists() || out.length() <= 0L) {
                    out.writeText(com.example.data.subtitles.SrtSync.toSrt(shifted))
                }
                Uri.fromFile(out)
            }
        }.getOrNull()
    }

    val mediaItem = remember(localFilePath, sidecarUri, sidecarLang, subtitleFilePath) {
        val builder = MediaItem.Builder().setUri(Uri.fromFile(File(localFilePath)))
        if (sidecarUri != null && !subtitleFilePath.isNullOrBlank()) {
            val ext = subtitleFilePath.substringAfterLast('.', "srt").lowercase()
            val mime = when (ext) {
                "vtt" -> MimeTypes.TEXT_VTT
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(sidecarUri)
                        .setId(TRACK_ID_SIDECAR)
                        .setMimeType(mime)
                        .setLanguage(sidecarLang)
                        .setLabel("External ($sidecarLang)")
                        .build()
                )
            )
        }
        builder.build()
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    // Validate the file up-front so a missing/empty download shows an explicit
    // error instead of an endless buffering spinner ("can't open" reports).
    val missingFileError: String? = remember(localFilePath) {
        val f = File(localFilePath)
        when {
            !f.exists() -> "File not found. The download may still be finalizing — check it shows 100% in Downloads, then retry."
            f.length() <= 0L -> "File is empty (0 bytes). The torrent likely never completed — delete it and re-download."
            else -> null
        }
    }

    // Applies the selected subtitle track without rebuilding the item:
    // Off disables the text renderer, otherwise the matching group/track is
    // forced via override (sidecar by id, embedded by position).
    fun applyTextSelection() {
        val params = player.trackSelectionParameters.buildUpon()
        val sel = selectedTrackId
        if (sel == null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            params.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            if (sel == TRACK_ID_SIDECAR) {
                textGroups.forEach { group ->
                    (0 until group.length).firstOrNull { i ->
                        group.getTrackFormat(i).id == TRACK_ID_SIDECAR
                    }?.let { trackIndex ->
                        params.addOverride(
                            TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                        )
                    }
                }
            } else {
                parseEmbeddedTrackId(sel)?.let { (groupIndex, trackIndex) ->
                    textGroups.getOrNull(groupIndex)?.let { group ->
                        if (trackIndex in 0 until group.length) {
                            params.addOverride(
                                TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                            )
                        }
                    }
                }
            }
        }
        player.trackSelectionParameters = params.build()
    }

    // (Re)load the media item. Offset changes rebuild with position kept.
    LaunchedEffect(mediaItem) {
        if (missingFileError != null) return@LaunchedEffect
        val resumeMs = currentPositionMs.toLong().takeIf { it > 0 } ?: 0L
        player.setMediaItem(mediaItem)
        player.prepare()
        if (resumeMs > 0) player.seekTo(resumeMs)
        player.setPlaybackSpeed(playbackSpeed)
        applyTextSelection()
    }
    // Re-apply the selection once tracks arrive (embedded catalogue).
    LaunchedEffect(selectedTrackId, embeddedTracks) {
        if (player.playbackState != Player.STATE_IDLE) applyTextSelection()
    }

    LaunchedEffect(missingFileError) {
        if (missingFileError != null) {
            isBuffering = false
            playbackError = missingFileError
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    playbackError = null
                    val dur = player.duration
                    if (dur != C.TIME_UNSET && dur > 0) durationMs = dur.toInt()
                }
                if (state == Player.STATE_ENDED) {
                    isPlaying = false
                    controlsVisible = true
                }
            }

            override fun onIsPlayingChanged(nowPlaying: Boolean) {
                isPlaying = nowPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                isPlaying = false
                playbackError = "This file couldn't be played here " +
                    "(${(error.errorCodeName ?: ("code " + error.errorCode))}). " +
                    "Try an external player with the button below."
            }

            override fun onTracksChanged(tracks: Tracks) {
                val found = mutableListOf<OfflineTextTrack>()
                var textGroupIndex = 0
                tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { group ->
                    (0 until group.length).forEach { trackIndex ->
                        val format = group.getTrackFormat(trackIndex)
                        if (format.id != TRACK_ID_SIDECAR) {
                            val lang = format.language?.takeIf { l -> l.isNotBlank() }
                            val label = format.label?.takeIf { l -> l.isNotBlank() }
                            found.add(
                                OfflineTextTrack(
                                    id = embeddedTrackId(textGroupIndex, trackIndex),
                                    label = label ?: lang?.uppercase() ?: "Track ${found.size + 1}",
                                    isSidecar = false
                                )
                            )
                        }
                    }
                    textGroupIndex++
                }
                embeddedTracks = found
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(ccToastMessage) {
        if (ccToastMessage != null) {
            delay(2000L)
            ccToastMessage = null
        }
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(lastInteractionTime, isPlaying) {
        if (isPlaying && controlsVisible && !isDraggingSlider) {
            delay(4000L)
            controlsVisible = false
        }
    }

    // Position tracking loop (lifecycle-safe: cancelled with composition).
    LaunchedEffect(isPlaying, isDraggingSlider) {
        while (true) {
            if (!isDraggingSlider && player.isPlaying) {
                currentPositionMs = player.currentPosition.toInt().coerceAtLeast(0)
                val dur = player.duration
                if (dur != C.TIME_UNSET && dur > 0) {
                    durationMs = dur.toInt()
                    sliderPosition =
                        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                }
            }
            delay(500L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
                lastInteractionTime = System.currentTimeMillis()
            }
            .testTag("offline_video_player")
    ) {
        // Media3 surface (skipped when the file is already known-missing).
        // useController=false: subtitles still render via SubtitleView while
        // all controls stay in Compose for a consistent offline look.
        if (missingFileError == null) {
            val exo = player
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        setPlayer(exo)
                    }
                },
                update = { view ->
                    if (view.getPlayer() !== exo) view.setPlayer(exo)
                },
                onRelease = { view -> view.setPlayer(null) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading spinner while opening video file
        if (isBuffering && playbackError == null) {
            CircularProgressIndicator(
                color = YouTubeRed,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }

        // Explicit error surface with an external-player fallback, so a
        // codec/container the built-in VideoView can't handle is actionable
        // instead of a black screen.
        if (playbackError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = playbackError ?: "Playback failed",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { openInExternalPlayer(context, localFilePath) },
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                    modifier = Modifier.testTag("offline_open_external_btn")
                ) {
                    Text("Open in external player", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onClose) {
                    Text("Back to Downloads", color = Color.White)
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("offline_player_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Close player",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1
                                )
                            }
                            if (!serverName.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = serverName,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // CC button: opens the track picker when real tracks exist
                    // (sidecar and/or embedded). Falls back to the legacy
                    // label toast, or a one-tap fetch when nothing exists.
                    val legacyLabelOn =
                        !subtitleCc.isNullOrBlank() && !subtitleCc.contains("Off", ignoreCase = true)
                    val trackCount = (if (hasSidecar) 1 else 0) + embeddedTracks.size
                    val showCcToggle = trackCount > 0 || legacyLabelOn
                    if (showCcToggle) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCcActive) YouTubeRed else Color.White.copy(alpha = 0.2f))
                                .clickable {
                                    if (trackCount > 0) {
                                        showTrackPicker = true
                                    } else {
                                        ccToastMessage = "Subtitles: $subtitleCc"
                                    }
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("offline_cc_toggle")
                        ) {
                            Text(
                                text = "CC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    } else if (onRequestSubtitles != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .clickable {
                                    onRequestSubtitles()
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("offline_cc_download_btn")
                        ) {
                            Text(
                                text = "Get CC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Offline Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2E7D32))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "OFFLINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Center Play/Pause & Rewind/Forward Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp)
                ) {
                    // Seek -10s
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            val target = (player.currentPosition - 10_000).coerceAtLeast(0)
                            player.seekTo(target)
                            currentPositionMs = target.toInt()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .testTag("offline_player_replay_10")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Main Play / Pause Button
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(YouTubeRed)
                            .clickable {
                                lastInteractionTime = System.currentTimeMillis()
                                if (player.isPlaying) player.pause() else player.play()
                            }
                            .testTag("offline_player_play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Seek +10s
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: durationMs.toLong()
                            val target = (player.currentPosition + 10_000).coerceAtMost(dur.coerceAtLeast(0))
                            player.seekTo(target)
                            currentPositionMs = target.toInt()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .testTag("offline_player_forward_10")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Timeline & Scrubber Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    // Manual A/V sync for the downloaded sidecar: shifts cues
                    // ±500ms per tap (via a shifted temp file) and persists
                    // via onSubtitleOffsetChanged (survives reboot). Tapping
                    // the label resets to zero. Embedded tracks can't be
                    // shifted (no player API), so the row hides for them.
                    if (selectedTrackId == TRACK_ID_SIDECAR && hasSidecar) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val offsetLabel = when {
                                subtitleOffsetMs == 0L -> "Sync: 0s"
                                subtitleOffsetMs > 0 -> "Sync: +${subtitleOffsetMs / 1000.0}s"
                                else -> "Sync: ${subtitleOffsetMs / 1000.0}s"
                            }
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    subtitleOffsetMs = (subtitleOffsetMs - com.example.data.subtitles.SUBTITLE_OFFSET_STEP_MS)
                                        .coerceIn(
                                            -com.example.data.subtitles.MAX_SUBTITLE_OFFSET_MS,
                                            com.example.data.subtitles.MAX_SUBTITLE_OFFSET_MS
                                        )
                                    onSubtitleOffsetChanged?.invoke(subtitleOffsetMs)
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                modifier = Modifier.testTag("offline_cc_sync_earlier")
                            ) {
                                Text("-0.5s", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                            }
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    subtitleOffsetMs = 0L
                                    onSubtitleOffsetChanged?.invoke(0L)
                                    ccToastMessage = "Subtitle sync reset"
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            ) {
                                Text(offsetLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    subtitleOffsetMs = (subtitleOffsetMs + com.example.data.subtitles.SUBTITLE_OFFSET_STEP_MS)
                                        .coerceIn(
                                            -com.example.data.subtitles.MAX_SUBTITLE_OFFSET_MS,
                                            com.example.data.subtitles.MAX_SUBTITLE_OFFSET_MS
                                        )
                                    onSubtitleOffsetChanged?.invoke(subtitleOffsetMs)
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                modifier = Modifier.testTag("offline_cc_sync_later")
                            ) {
                                Text("+0.5s", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                            }
                        }
                    }
                    Slider(
                        value = if (isDraggingSlider) sliderPosition else (currentPositionMs.toFloat() / durationMs.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                        onValueChange = { newValue ->
                            isDraggingSlider = true
                            sliderPosition = newValue
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            val targetMs = (sliderPosition * durationMs).toLong()
                            player.seekTo(targetMs)
                            currentPositionMs = targetMs.toInt()
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = YouTubeRed,
                            activeTrackColor = YouTubeRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .testTag("offline_player_slider")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimeMs(currentPositionMs),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var showSpeedMenu by remember { mutableStateOf(false) }
                            Box {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        showSpeedMenu = true
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    modifier = Modifier.testTag("offline_player_speed_btn")
                                ) {
                                    Text(
                                        text = "${if (playbackSpeed % 1f == 0f) playbackSpeed.toInt().toString() else playbackSpeed.toString()}x",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    listOf(0.5f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()}x" +
                                                        if (speed == playbackSpeed) " ✓" else ""
                                                )
                                            },
                                            onClick = {
                                                showSpeedMenu = false
                                                playbackSpeed = speed
                                                player.setPlaybackSpeed(speed)
                                                lastInteractionTime = System.currentTimeMillis()
                                            }
                                        )
                                    }
                                }
                            }
                            Text(
                                text = formatTimeMs(durationMs),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        // Subtitle / CC Status Toast notification
        AnimatedVisibility(
            visible = ccToastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = ccToastMessage ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Subtitle track picker: Off + downloaded sidecar + embedded tracks.
        if (showTrackPicker) {
            AlertDialog(
                onDismissRequest = { showTrackPicker = false },
                title = { Text("Subtitles", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SubtitleTrackRow(
                            label = "Off",
                            selected = selectedTrackId == null,
                            onClick = {
                                selectedTrackId = null
                                onSubtitleTrackChanged?.invoke(null)
                                ccToastMessage = "Subtitles: Off"
                                showTrackPicker = false
                            }
                        )
                        if (hasSidecar) {
                            val sidecarLabel = buildString {
                                append("Downloaded ($sidecarLang)")
                                if (subtitleOffsetMs != 0L) {
                                    append(if (subtitleOffsetMs > 0) " +${subtitleOffsetMs / 1000.0}s" else " ${subtitleOffsetMs / 1000.0}s")
                                }
                            }
                            SubtitleTrackRow(
                                label = sidecarLabel,
                                selected = selectedTrackId == TRACK_ID_SIDECAR,
                                onClick = {
                                    selectedTrackId = TRACK_ID_SIDECAR
                                    onSubtitleTrackChanged?.invoke(TRACK_ID_SIDECAR)
                                    ccToastMessage = "Subtitles: $sidecarLang"
                                    showTrackPicker = false
                                }
                            )
                        }
                        embeddedTracks.forEach { track ->
                            SubtitleTrackRow(
                                label = "Embedded · ${track.label}",
                                selected = selectedTrackId == track.id,
                                onClick = {
                                    selectedTrackId = track.id
                                    onSubtitleTrackChanged?.invoke(track.id)
                                    ccToastMessage = "Subtitles: ${track.label}"
                                    showTrackPicker = false
                                }
                            )
                        }
                        if (onRequestSubtitles != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = {
                                    showTrackPicker = false
                                    onRequestSubtitles()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("offline_track_picker_fetch_btn")
                            ) {
                                Text("Search for subtitles")
                            }
                        }
                        if (selectedTrackId != null && selectedTrackId != TRACK_ID_SIDECAR) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sync adjustment (±0.5s) applies to downloaded subtitles only.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showTrackPicker = false }) {
                        Text("Done")
                    }
                },
                modifier = Modifier.testTag("offline_subtitle_picker")
            )
        }
    }
}

/** One row in the subtitle track picker dialog. */
@Composable
private fun SubtitleTrackRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) YouTubeRed else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Text(
                text = "✓",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = YouTubeRed
            )
        }
    }
}

/** A text track known to the offline player (sidecar or embedded). */
data class OfflineTextTrack(
    val id: String,
    val label: String,
    val isSidecar: Boolean
)

/** Stable id for the downloaded sidecar (also the ExoPlayer Format id). */
const val TRACK_ID_SIDECAR = "sidecar"

private fun embeddedTrackId(groupIndex: Int, trackIndex: Int): String =
    "emb:$groupIndex:$trackIndex"

private fun parseEmbeddedTrackId(id: String): Pair<Int, Int>? {
    val parts = id.split(":")
    if (parts.size != 3 || parts[0] != "emb") return null
    val group = parts[1].toIntOrNull() ?: return null
    val track = parts[2].toIntOrNull() ?: return null
    return group to track
}

private fun openInExternalPlayer(context: android.content.Context, localFilePath: String) {
    try {
        val file = File(localFilePath)
        if (!file.exists() || file.length() <= 0L) {
            Toast.makeText(context, "File not available yet", Toast.LENGTH_SHORT).show()
            return
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = when (file.extension.lowercase()) {
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "m4v", "mp4" -> "video/mp4"
            else -> "video/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Play with"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app found to play this file", Toast.LENGTH_SHORT).show()
    }
}

private fun formatTimeMs(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

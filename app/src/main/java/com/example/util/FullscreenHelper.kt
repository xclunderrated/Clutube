package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

object FullscreenHelper {

    private const val UP_NEXT_LEAD_SECONDS = 60.0
    private const val UP_NEXT_COUNTDOWN_SECONDS = 10.0

    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var fullscreenPlayerView: View? = null
    private var playerViewRestoreContainer: ViewGroup? = null
    private var preservingPlayerReload = false

    // Up Next must live in the fullscreen container because the provider's
    // custom WebView is attached above the Compose hierarchy.
    private var upNextTargetKey: String? = null
    private var upNextTitle: String = ""
    private var upNextSubtitle: String = ""
    private var upNextAutoNextEnabled = false
    private var upNextAction: (() -> Unit)? = null
    private var upNextPositionSeconds = 0.0
    private var upNextDurationSeconds = 0.0
    private var upNextTriggered = false
    private var upNextInteractionVisible = false
    private var upNextOverlay: FullscreenUpNextOverlay? = null
    private var upNextAutoNextRunnable: Runnable? = null

    val isCustomViewActive: Boolean
        get() = customView != null || fullscreenPlayerView != null

    fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun showCustomView(activity: Activity, view: View, callback: WebChromeClient.CustomViewCallback) {
        val existingContainer = fullscreenContainer
        if (existingContainer != null && (customView != null || fullscreenPlayerView != null)) {
            // A new episode/provider page can request fullscreen while the
            // previous custom view is still mounted. Replace only the media
            // view and keep the fullscreen container, orientation, and UI.
            restorePlayerViewInternal()
            customView?.let { existingContainer.removeView(it) }
            customView = view
            customViewCallback = callback
            preservingPlayerReload = false
            upNextInteractionVisible = false
            existingContainer.addView(
                view,
                0,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            refreshUpNextOverlay()
            return
        }
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback
        originalOrientation = activity.requestedOrientation

        val decorView = activity.window.decorView as ViewGroup

        val container = FullscreenContainer(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        fullscreenContainer = container
        decorView.addView(container)
        refreshUpNextOverlay()

        // Hide system UI status bar and navigation bar for immersive full screen
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        _isFullscreen.value = true
        // Force horizontal (landscape) orientation in fullscreen
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun hideCustomView(activity: Activity) {
        // The provider may send a stale onHideCustomView while its page is
        // being replaced. Keep the fullscreen surface alive for the new
        // episode; an explicit Back press clears this mode first.
        if (preservingPlayerReload && customView == null) return

        PlayerViewManager.hidePlayerUi()

        val decorView = activity.window.decorView as ViewGroup
        val container = fullscreenContainer

        if (container != null) {
            decorView.removeView(container)
            container.removeAllViews()
        }
        removeUpNextOverlay()
        upNextInteractionVisible = false
        fullscreenContainer = null
        customView = null
        preservingPlayerReload = false

        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        restorePlayerViewInternal()

        // Restore system UI
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, decorView)
        insetsController.show(WindowInsetsCompat.Type.systemBars())

        activity.requestedOrientation = originalOrientation
        _isFullscreen.value = false
    }

    fun exitFullscreen(activity: Activity): Boolean {
        if (_isFullscreen.value || fullscreenContainer != null || customView != null || fullscreenPlayerView != null) {
            preservingPlayerReload = false
            hideCustomView(activity)
            return true
        }
        return false
    }

    fun enterFullscreen(activity: Activity) {
        if (_isFullscreen.value) return
        originalOrientation = activity.requestedOrientation
        val decorView = activity.window.decorView as ViewGroup
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        _isFullscreen.value = true
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun toggleFullscreen(activity: Activity) {
        if (_isFullscreen.value) {
            exitFullscreen(activity)
        } else {
            enterFullscreen(activity)
        }
    }

    /** Registers the current video's next item for both Watch and native fullscreen UI. */
    fun setUpNextTarget(
        key: String?,
        title: String?,
        subtitle: String?,
        autoNextEnabled: Boolean,
        onPlayNext: (() -> Unit)?
    ) {
        mainHandler.post {
            if (key.isNullOrBlank() || title.isNullOrBlank() || onPlayNext == null) {
                clearUpNextTargetInternal()
                return@post
            }

            if (upNextTargetKey != key) {
                upNextTriggered = false
                upNextInteractionVisible = false
                cancelAutoNext()
                upNextPositionSeconds = 0.0
                upNextDurationSeconds = 0.0
            }
            upNextTargetKey = key
            upNextTitle = title.trim()
            upNextSubtitle = subtitle?.trim().orEmpty()
            upNextAutoNextEnabled = autoNextEnabled
            upNextAction = onPlayNext
            refreshUpNextOverlay()
        }
    }

    /** Clears a target only if it still belongs to the caller that registered it. */
    fun clearUpNextTarget(key: String? = null) {
        mainHandler.post {
            if (key != null && key != upNextTargetKey) return@post
            clearUpNextTargetInternal()
        }
    }

    /** Feeds raw provider progress into the fullscreen overlay. */
    fun updateUpNextPlayback(positionSeconds: Double, durationSeconds: Double) {
        mainHandler.post {
            upNextPositionSeconds = positionSeconds
            upNextDurationSeconds = durationSeconds
            refreshUpNextOverlay()
        }
    }

    /** Uses the same controls visibility state as the regular player surface. */
    fun notifyPlayerInteraction() {
        PlayerViewManager.showPlayerUi()
    }

    /** Mirrors the shared player-controls state into the native fullscreen layer. */
    fun setPlayerUiVisible(visible: Boolean) {
        mainHandler.post {
            if (upNextInteractionVisible == visible) {
                refreshUpNextOverlay()
                return@post
            }
            upNextInteractionVisible = visible
            refreshUpNextOverlay()
        }
    }

    /** Keeps fullscreen active while the persistent WebView loads a new episode. */
    fun prepareForPlayerReload() {
        if (!_isFullscreen.value || fullscreenContainer == null) return
        preservingPlayerReload = true
        customView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        customView = null
        customViewCallback = null
        upNextInteractionVisible = false
        removeUpNextOverlay()
    }

    /** Temporarily hosts the persistent player in the fullscreen container during reload. */
    fun attachPlayerViewForReload(view: View, restoreContainer: ViewGroup?) {
        if (!_isFullscreen.value || fullscreenContainer == null) return
        if (restoreContainer != null && restoreContainer !== fullscreenContainer) {
            playerViewRestoreContainer = restoreContainer
        }
        (view.parent as? ViewGroup)?.removeView(view)
        fullscreenPlayerView = view
        fullscreenContainer?.addView(
            view,
            0,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        refreshUpNextOverlay()
    }

    fun isPlayerViewHosted(view: View): Boolean = fullscreenPlayerView === view

    fun updatePlayerViewRestoreContainer(container: ViewGroup) {
        if (fullscreenPlayerView != null) playerViewRestoreContainer = container
    }

    private fun clearUpNextTargetInternal() {
        upNextTargetKey = null
        upNextTitle = ""
        upNextSubtitle = ""
        upNextAutoNextEnabled = false
        upNextAction = null
        upNextTriggered = false
        upNextInteractionVisible = false
        cancelAutoNext()
        removeUpNextOverlay()
    }

    private fun refreshUpNextOverlay() {
        val container = fullscreenContainer
        val targetKey = upNextTargetKey
        val action = upNextAction
        val duration = upNextDurationSeconds
        val position = upNextPositionSeconds
        val remaining = duration - position
        val hasFullscreenSurface = customView != null || fullscreenPlayerView != null
        val validPlayback = duration.isFinite() &&
            position.isFinite() &&
            duration > UP_NEXT_LEAD_SECONDS &&
            position >= 0.0 &&
            position <= duration + 1.0 &&
            remaining >= -1.0
        val canAutoAdvance = fullscreenContainer != null &&
            hasFullscreenSurface &&
            targetKey != null &&
            action != null &&
            !upNextTriggered &&
            validPlayback &&
            upNextAutoNextEnabled &&
            remaining <= UP_NEXT_COUNTDOWN_SECONDS

        if (canAutoAdvance) {
            scheduleAutoNext(targetKey, remaining)
        } else {
            cancelAutoNext()
        }

        val shouldShow = container != null &&
            hasFullscreenSurface &&
            targetKey != null &&
            action != null &&
            upNextTitle.isNotBlank() &&
            upNextInteractionVisible &&
            !upNextTriggered

        if (!shouldShow) {
            removeUpNextOverlay()
            return
        }

        val card = upNextOverlay ?: FullscreenUpNextOverlay(container.context) {
            triggerUpNext(targetKey)
        }.also { created ->
            upNextOverlay = created
            created.layoutParams = FrameLayout.LayoutParams(
                dp(container.context, 40),
                dp(container.context, 40),
                Gravity.CENTER_VERTICAL or Gravity.END
            ).apply {
                setMargins(0, 0, dp(container.context, 10), 0)
            }
            container.addView(created)
        }
        card.render(upNextTitle, upNextSubtitle)
    }

    private fun triggerUpNext(key: String?) {
        if (key == null || key != upNextTargetKey || upNextTriggered) return
        upNextTriggered = true
        cancelAutoNext()
        removeUpNextOverlay()
        upNextAction?.invoke()
    }

    private fun scheduleAutoNext(key: String?, remainingSeconds: Double) {
        if (key == null || upNextTriggered) return
        cancelAutoNext()
        val delayMillis = (remainingSeconds.coerceAtLeast(0.0) * 1000.0)
            .roundToInt()
            .toLong()
            .coerceAtLeast(0L)
        val runnable = Runnable {
            if (upNextTargetKey == key && !upNextTriggered) triggerUpNext(key)
        }
        upNextAutoNextRunnable = runnable
        mainHandler.postDelayed(runnable, delayMillis)
    }

    private fun cancelAutoNext() {
        upNextAutoNextRunnable?.let(mainHandler::removeCallbacks)
        upNextAutoNextRunnable = null
    }

    private fun removeUpNextOverlay() {
        upNextOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        upNextOverlay = null
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private fun restorePlayerViewInternal() {
        val playerView = fullscreenPlayerView ?: return
        val restoreContainer = playerViewRestoreContainer
        (playerView.parent as? ViewGroup)?.removeView(playerView)
        if (restoreContainer != null && playerView.parent !== restoreContainer) {
            restoreContainer.addView(
                playerView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        fullscreenPlayerView = null
        playerViewRestoreContainer = null
    }

    private class FullscreenContainer(context: Context) : FrameLayout(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                notifyPlayerInteraction()
            }
            return super.dispatchTouchEvent(event)
        }
    }

    private class FullscreenUpNextOverlay(
        context: Context,
        onClick: () -> Unit
    ) : FrameLayout(context) {
        init {
            isClickable = false
            isFocusable = false
            setOnClickListener { onClick() }
            contentDescription = "Next episode"
            val nextButton = ImageButton(context).apply {
                setImageResource(android.R.drawable.ic_media_next)
                setColorFilter(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                contentDescription = "Next episode"
                setOnClickListener { onClick() }
                setPadding(dp(5), dp(5), dp(5), dp(5))
            }
            addView(
                nextButton,
                LayoutParams(dp(40), dp(40), Gravity.CENTER)
            )
        }

        fun render(title: String, subtitle: String) {
            // The fullscreen control intentionally stays icon-only.
        }

        private fun dp(value: Int): Int =
            (value * resources.displayMetrics.density).roundToInt()
    }
}

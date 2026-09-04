package com.example.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.example.model.VideoItem
import kotlinx.coroutines.flow.StateFlow

/** Small compatibility facade for player integrations that need one lifecycle entry point. */
object PlayerUtils {
    val isFullscreen: StateFlow<Boolean>
        get() = FullscreenHelper.isFullscreen

    fun findActivity(context: Context): Activity? = FullscreenHelper.findActivity(context)

    fun enterFullscreen(activity: Activity) = FullscreenHelper.enterFullscreen(activity)

    fun exitFullscreen(activity: Activity): Boolean = FullscreenHelper.exitFullscreen(activity)

    fun toggleFullscreen(activity: Activity) = FullscreenHelper.toggleFullscreen(activity)

    fun showCustomView(
        activity: Activity,
        view: View,
        callback: WebChromeClient.CustomViewCallback
    ) = FullscreenHelper.showCustomView(activity, view, callback)

    fun hideCustomView(activity: Activity) = FullscreenHelper.hideCustomView(activity)

    fun attachPlayer(
        context: Context,
        video: VideoItem,
        serverId: String
    ): WebView = PlayerViewManager.attachPlayerToContainer(context, video, serverId)

    fun releasePlayer() = PlayerViewManager.releasePlayer()
}

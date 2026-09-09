package com.example.ui.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * Single source of truth for YouTube-like subtle motion.
 * Fast fades + tiny scales, no bounce. Scroll-critical surfaces must use
 * these tokens so every transition feels consistent and stays under 260ms.
 */
object MotionTokens {
    const val Fast = 120
    const val Medium = 200
    const val Slow = 260

    val EaseOut = FastOutSlowInEasing
    val EaseEmphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EaseEnter = LinearOutSlowInEasing

    fun <T> fastTween() = tween<T>(Fast, easing = EaseOut)
    fun <T> mediumTween() = tween<T>(Medium, easing = EaseOut)
    fun <T> slowTween() = tween<T>(Slow, easing = EaseEmphasized)

    // Card / list item enter: subtle fade + 0.96 -> 1.0 scale.
    val ItemEnter = fadeIn(tween(Medium, easing = EaseOut)) +
        scaleIn(initialScale = 0.96f, animationSpec = tween(Medium, easing = EaseOut))
    val ItemExit = fadeOut(tween(Fast, easing = EaseOut))

    // Overlays (search, history, settings, channel): fade + 24dp rise.
    fun overlayEnter(offsetY: Int = 24) =
        fadeIn(tween(Medium, easing = EaseOut)) +
            slideInVertically(tween(Medium, easing = EaseOut)) { offsetY }

    fun overlayExit(offsetY: Int = 16) =
        fadeOut(tween(Fast, easing = EaseOut)) +
            slideOutVertically(tween(Fast, easing = EaseOut)) { offsetY }
}

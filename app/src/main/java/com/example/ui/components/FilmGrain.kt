package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

private const val GRAIN_TILE_PX = 128

/**
 * Subtle monochrome film grain. A single small noise tile is generated once
 * per process and GPU-tiled via [ImageShader], so the overlay is one cheap
 * draw call no matter how large the surface is.
 */
private var cachedGrainTile: ImageBitmap? = null
private var cachedGrainBrush: ShaderBrush? = null

@Synchronized
private fun grainBrush(): ShaderBrush {
    cachedGrainBrush?.let { return it }
    val tile = cachedGrainTile ?: run {
        val pixels = IntArray(GRAIN_TILE_PX * GRAIN_TILE_PX) {
            val v = Random.nextInt(256)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        Bitmap.createBitmap(pixels, GRAIN_TILE_PX, GRAIN_TILE_PX, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
            .also { cachedGrainTile = it }
    }
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
        .also { cachedGrainBrush = it }
}

/**
 * Draws film grain behind the content. The modifier itself never consumes
 * input, so overlays stay fully touch-transparent.
 *
 * @param alpha grain strength; 0.03–0.06 reads as texture, above 0.10 as noise.
 */
fun Modifier.filmGrain(alpha: Float = 0.05f): Modifier = this.drawBehind {
    drawRect(brush = grainBrush(), alpha = alpha.coerceIn(0f, 1f))
}

/** Full-size touch-transparent grain sheet, meant as the last child of a root layout. */
@Composable
fun FilmGrainOverlay(
    alpha: Float = 0.035f,
    modifier: Modifier = Modifier
) {
    val brush = remember { grainBrush() }
    Box(
        modifier = modifier.drawBehind {
            drawRect(brush = brush, alpha = alpha.coerceIn(0f, 1f))
        }
    )
}

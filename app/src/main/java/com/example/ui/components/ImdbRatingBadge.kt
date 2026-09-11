package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.ui.theme.ImdbBlack
import com.example.ui.theme.ImdbYellow

private val ImdbPillShape = RoundedCornerShape(6.dp)

/**
 * True IMDb-style pill: opaque yellow rounded rect with black
 * "IMDb ★ 7.2" text. Opaque (no alpha) so it stays legible on light/dark
 * themes and over thumbnails. [compact] is for card/overlay rows,
 * default size for the Watch details row.
 */
@Composable
fun ImdbRatingBadge(
    rating: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val horizontal = if (compact) 6.dp else 8.dp
    val vertical = if (compact) 3.dp else 4.dp
    val prefixSize = if (compact) 8.sp else 9.sp
    val ratingSize = if (compact) 11.sp else 12.sp
    val starSize = if (compact) 11.dp else 13.dp
    Box(
        modifier = modifier
            .clip(ImdbPillShape)
            .background(ImdbYellow)
            .padding(horizontal = horizontal, vertical = vertical)
            .semantics(mergeDescendants = true) {
                contentDescription = "IMDb rating $rating out of 10"
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "IMDb",
                color = ImdbBlack,
                fontSize = prefixSize,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp,
                lineHeight = prefixSize
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = ImdbBlack,
                modifier = Modifier.size(starSize)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = rating,
                color = ImdbBlack,
                fontSize = ratingSize,
                fontWeight = FontWeight.Bold,
                lineHeight = ratingSize
            )
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Simple star + score label (e.g. ★ 6.4/10) with no enclosing badge. */
@Composable
fun ImdbRatingBadge(
    rating: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = Color(0xFFF5C518),
            modifier = Modifier.size(if (compact) 12.dp else 14.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            rating,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = if (compact) 11.sp else 12.sp
        )
        Text(
            "/10",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = if (compact) 9.sp else 10.sp
        )
    }
}

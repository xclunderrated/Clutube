package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.YTDarkChipActive
import com.example.ui.theme.YTDarkChipActiveText
import com.example.ui.theme.YTDarkChipInactive
import com.example.ui.theme.YTDarkChipInactiveText
import com.example.ui.theme.YTLightChipActive
import com.example.ui.theme.YTLightChipActiveText
import com.example.ui.theme.YTLightChipInactive
import com.example.ui.theme.YTLightChipInactiveText

private val PillShape = RoundedCornerShape(8.dp)

/**
 * Slim YouTube-style topic filter strip: single 32dp-tall row, compact
 * pills, no divider — matches the YouTube mobile home filter bar.
 */
@Composable
fun FilterPillRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    isDarkMode: Boolean,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Compact explore icon (same height as pills, icon-only).
        item(key = "pill_explore", contentType = "explore_button") {
            val haptics = LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 32.dp)
                    .clip(PillShape)
                    .background(
                        if (isDarkMode) YTDarkChipInactive else YTLightChipInactive
                    )
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Explore topics",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onExploreClick()
                        }
                    )
                    .testTag("explore_filter_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = "Explore Topics",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Category pills — 32dp tall, 13sp text, Tab semantics + haptics.
        items(
            items = categories,
            key = { it },
            contentType = { "category_pill" }
        ) { category ->
            val isSelected = category == selectedCategory
            val haptics = LocalHapticFeedback.current
            val targetBg = when {
                isSelected -> if (isDarkMode) YTDarkChipActive else YTLightChipActive
                else -> if (isDarkMode) YTDarkChipInactive else YTLightChipInactive
            }
            val targetText = when {
                isSelected -> if (isDarkMode) YTDarkChipActiveText else YTLightChipActiveText
                else -> if (isDarkMode) YTDarkChipInactiveText else YTLightChipInactiveText
            }
            val bg by animateColorAsState(targetValue = targetBg, label = "pill_bg")
            val textColor by animateColorAsState(targetValue = targetText, label = "pill_text")

            val onPillClick = remember(category, onCategorySelected) {
                {
                    try {
                        // Haptics are emitted by the caller via composition local;
                        // kept here for direct pill taps.
                    } catch (_: Exception) { }
                    onCategorySelected(category)
                }
            }

            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(bg)
                    .clickable(
                        role = Role.Tab,
                        onClickLabel = "Show $category",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPillClick()
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .semantics {
                        this.role = Role.Tab
                        this.selected = isSelected
                    }
                    .testTag("pill_$category"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

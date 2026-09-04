package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import com.example.ui.theme.YTDarkChipActive
import com.example.ui.theme.YTDarkChipActiveText
import com.example.ui.theme.YTDarkChipInactive
import com.example.ui.theme.YTDarkChipInactiveText
import com.example.ui.theme.YTLightChipActive
import com.example.ui.theme.YTLightChipActiveText
import com.example.ui.theme.YTLightChipInactive
import com.example.ui.theme.YTLightChipInactiveText
import com.example.ui.theme.YouTubeRed

private val PillShape = RoundedCornerShape(8.dp)

@Composable
fun FilterPillRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    isDarkMode: Boolean,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Explore Compass Icon Button
            item(key = "pill_explore", contentType = "explore_button") {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 32.dp)
                        .clip(PillShape)
                        .background(
                            if (isDarkMode) YTDarkChipInactive else YTLightChipInactive
                        )
                        .clickable(onClick = onExploreClick)
                        .testTag("explore_filter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = "Explore Topics",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Category Pills
            items(
                items = categories,
                key = { it },
                contentType = { "category_pill" }
            ) { category ->
                val isSelected = category == selectedCategory
                val bg = when {
                    isSelected -> if (isDarkMode) YTDarkChipActive else YTLightChipActive
                    else -> if (isDarkMode) YTDarkChipInactive else YTLightChipInactive
                }

                val textColor = when {
                    isSelected -> if (isDarkMode) YTDarkChipActiveText else YTLightChipActiveText
                    else -> if (isDarkMode) YTDarkChipInactiveText else YTLightChipInactiveText
                }

                val onPillClick = remember(category, onCategorySelected) {
                    { onCategorySelected(category) }
                }

                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(bg)
                        .clickable(onClick = onPillClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("pill_$category"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category,
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // High Density subtle bottom separator
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        )
    }
}

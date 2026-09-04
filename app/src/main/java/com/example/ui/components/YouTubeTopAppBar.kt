package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun YouTubeTopAppBar(
    isDarkMode: Boolean,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit = {},
    onThemeToggle: () -> Unit,
    onAvatarClick: () -> Unit,
    onCastClick: (() -> Unit)? = null,
    onNotificationsClick: () -> Unit = {},
    notificationBadgeCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Japanese 'M' Katakana Logo ('モ') Icon Badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground)
                    .clickable(onClick = onRefresh)
                    .testTag("japanese_m_logo_header"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "モ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.background
                )
            }

            // Wide Search Bar Pill
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSearchClick() }
                    .padding(horizontal = 12.dp)
                    .testTag("wide_search_bar"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Search movies & series...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            IconButton(
                onClick = onCastClick ?: {},
                enabled = onCastClick != null,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("cast_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Cast,
                    contentDescription = if (onCastClick != null) "Cast" else "Cast unavailable locally",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("notifications_button")
            ) {
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                    if (notificationBadgeCount > 0) {
                        Text(
                            text = if (notificationBadgeCount > 99) "99+" else notificationBadgeCount.toString(),
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .offset(x = 6.dp, y = (-5).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(com.example.ui.theme.YouTubeRed)
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Theme Toggle Button
            IconButton(
                onClick = onThemeToggle,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("theme_toggle_button")
            ) {
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onAvatarClick,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("avatar_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Your profile",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    modifier = Modifier.size(19.dp)
                )
            }
        }
}

}


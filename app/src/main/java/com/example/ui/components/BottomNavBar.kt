package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.offset
import com.example.ui.theme.YouTubeRed

import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications

@Composable
fun BottomNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    badgeCount: Int = 0,
    profileAvatar: String = "C",
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Home (Tab 0)
                NavTabItem(
                    label = "Home",
                    iconFilled = Icons.Filled.Home,
                    iconOutlined = Icons.Outlined.Home,
                    isSelected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    testTag = "tab_home",
                    modifier = Modifier.weight(1f)
                )

                // Shorts (Tab 1)
                NavTabItem(
                    label = "Shorts",
                    iconFilled = Icons.Filled.SmartDisplay,
                    iconOutlined = Icons.Outlined.SmartDisplay,
                    isSelected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    testTag = "tab_shorts",
                    modifier = Modifier.weight(1f)
                )

                // Subscriptions (Tab 2)
                NavTabItem(
                    label = "Subscriptions",
                    iconFilled = Icons.Filled.Subscriptions,
                    iconOutlined = Icons.Outlined.Subscriptions,
                    isSelected = selectedTab == 2,
                    onClick = { onTabSelected(2) },
                    testTag = "tab_subscriptions",
                    modifier = Modifier.weight(1f)
                )

                // Notifications (Tab 3)
                NotificationTabItem(
                    isSelected = selectedTab == 3,
                    badgeCount = badgeCount,
                    onClick = { onTabSelected(3) },
                    modifier = Modifier.weight(1f)
                )

                // You (Tab 4)
                YouTabItem(
                    isSelected = selectedTab == 4,
                    onClick = { onTabSelected(4) },
                    profileAvatar = profileAvatar,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NotificationTabItem(
    isSelected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha = if (isSelected) 1f else 0.7f

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
            .testTag("tab_notifications"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                contentDescription = "Notifications",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
                modifier = Modifier.size(24.dp)
            )

            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 4.dp, y = (-3).dp)
                        .clip(CircleShape)
                        .background(YouTubeRed)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Notifications",
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)
        )
    }
}

@Composable
private fun NavTabItem(
    label: String,
    iconFilled: ImageVector,
    iconOutlined: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val alpha = if (isSelected) 1f else 0.6f

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isSelected) iconFilled else iconOutlined,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha),
            modifier = Modifier.size(23.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)
        )
    }
}

@Composable
private fun YouTabItem(
    isSelected: Boolean,
    onClick: () -> Unit,
    profileAvatar: String,
    modifier: Modifier = Modifier
) {
    val alpha = if (isSelected) 1f else 0.6f

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
            .testTag("tab_you"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    shape = CircleShape
                )
                .padding(1.dp),
            contentAlignment = Alignment.Center
        ) {
            LocalProfileAvatar(
                value = profileAvatar,
                contentDescription = "Local profile",
                textSize = 8.sp,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "You",
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = alpha)
        )
    }
}

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import com.example.ui.theme.YouTubeRed

@Composable
fun SideNavRail(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onCreateClick: () -> Unit = { onTabSelected(2) },
    profileAvatar: String = "C",
    badgeCount: Int = 0,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .width(76.dp)
            .testTag("side_navigation_rail"),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            ) {
                // YouTube Red Play Icon Logo
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(YouTubeRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "YouTube",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Home
        RailItem(
            label = "Home",
            iconFilled = Icons.Filled.Home,
            iconOutlined = Icons.Outlined.Home,
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            testTag = "rail_tab_home"
        )

        // Shorts
        RailItem(
            label = "Shorts",
            iconFilled = Icons.Filled.SmartDisplay,
            iconOutlined = Icons.Outlined.SmartDisplay,
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            testTag = "rail_tab_shorts"
        )

        // Create
        NavigationRailItem(
            selected = false,
            onClick = onCreateClick,
            icon = {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            label = null,
            modifier = Modifier.testTag("rail_tab_create")
        )

        // Subscriptions
        RailItem(
            label = "Subscriptions",
            iconFilled = Icons.Filled.Subscriptions,
            iconOutlined = Icons.Outlined.Subscriptions,
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            testTag = "rail_tab_subscriptions"
        )

        // Notifications
        RailItem(
            label = "Notifications",
            iconFilled = Icons.Filled.Notifications,
            iconOutlined = Icons.Outlined.Notifications,
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            testTag = "rail_tab_notifications",
            badgeCount = badgeCount
        )

        // You / Library
        NavigationRailItem(
            selected = selectedTab == 4,
            onClick = { onTabSelected(4) },
            icon = {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(
                            width = if (selectedTab == 4) 2.dp else 0.dp,
                            color = if (selectedTab == 4) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                            shape = CircleShape
                        )
                ) {
                    LocalProfileAvatar(
                        value = profileAvatar,
                        contentDescription = "Local profile",
                        textSize = 10.sp,
                        modifier = Modifier.matchParentSize()
                    )
                }
            },
            label = {
                Text(
                    text = "You",
                    fontSize = 10.sp,
                    fontWeight = if (selectedTab == 4) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationRailItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onBackground,
                selectedTextColor = MaterialTheme.colorScheme.onBackground,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                indicatorColor = Color.Transparent
            ),
            modifier = Modifier.testTag("rail_tab_you")
        )
    }
}

@Composable
private fun RailItem(
    label: String,
    iconFilled: ImageVector,
    iconOutlined: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    badgeCount: Int = 0
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = if (selected) iconFilled else iconOutlined,
                    contentDescription = label,
                    modifier = Modifier.size(24.dp)
                )
                if (badgeCount > 0) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .offset(x = 7.dp, y = (-5).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(YouTubeRed)
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        },
        label = {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        },
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onBackground,
            selectedTextColor = MaterialTheme.colorScheme.onBackground,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = Color.Transparent
        ),
        modifier = Modifier.testTag(testTag)
    )
}

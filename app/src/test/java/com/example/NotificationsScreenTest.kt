package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.model.AppNotification
import com.example.model.MediaType
import com.example.model.NotificationKind
import com.example.model.VideoItem
import com.example.ui.screens.NotificationsScreen
import com.example.ui.theme.YouTubeTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class NotificationsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `notification can open its target and mark the inbox read`() {
        val video = VideoItem(
            id = "movie-1",
            title = "Test movie",
            description = "",
            channelName = "Test channel",
            channelAvatarUrl = "",
            publishedAt = "",
            duration = "",
            thumbnailUrl = "",
            mediaType = MediaType.MOVIE
        )
        val notification = AppNotification(
            id = "notification-1",
            kind = NotificationKind.RELEASE_ALERT,
            title = "Now available",
            message = "Test movie is now available.",
            video = video,
            createdAtMillis = System.currentTimeMillis()
        )
        var currentNotifications by mutableStateOf(listOf(notification))
        var openedTitle: String? = null

        composeTestRule.setContent {
            YouTubeTheme {
                NotificationsScreen(
                    notifications = currentNotifications,
                    onVideoClick = { openedTitle = it.targetVideo.title },
                    onMarkAllRead = {
                        currentNotifications = currentNotifications.map { item -> item.copy(isRead = true) }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("1 unread").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notification_notification-1").performClick()
        composeTestRule.onNodeWithTag("notifications_mark_all_read").performClick()
        composeTestRule.onNodeWithText("You're all caught up").assertIsDisplayed()

        assertEquals("Test movie", openedTitle)
    }
}

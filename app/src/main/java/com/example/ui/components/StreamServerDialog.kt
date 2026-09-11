package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.data.StreamService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamServerDialog(
    currentServerId: String = StreamService.DEFAULT_SERVER_ID,
    currentVidSrcServerHost: String = StreamService.DEFAULT_VIDSRC_SERVER_HOST,
    vidSrcServerOrder: List<String> = StreamService.VIDSRC_SERVER_HOSTS,
    onSelectServer: (String) -> Unit = {},
    onSelectVidSrcServer: (String) -> Unit = {},
    onSaveVidSrcServerOrder: (List<String>) -> Unit = {},
    onDismiss: () -> Unit,
    onStream: (title: String, id: String, isTv: Boolean, season: Int, episode: Int) -> Unit
) {
    var showVidSrcDomains by remember { mutableStateOf(false) }
    var domainOrder by remember(vidSrcServerOrder) {
        mutableStateOf(StreamService.normalizeVidSrcServerOrder(vidSrcServerOrder))
    }

    fun moveDomain(index: Int, delta: Int) {
        val destination = index + delta
        if (index !in domainOrder.indices || destination !in domainOrder.indices) return
        domainOrder = domainOrder.toMutableList().also {
            val item = it.removeAt(index)
            it.add(destination, item)
        }
    }

    val haptics = LocalHapticFeedback.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .testTag("stream_server_modal")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Choose server",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Select where you want to play",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close server picker",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            StreamProviderRow(
                title = "VidSrc",
                subtitle = "Fast adaptive playback",
                selected = currentServerId == StreamService.VIDSRC_SERVER_ID,
                accent = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.Tune,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelectServer(StreamService.VIDSRC_SERVER_ID)
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            StreamProviderRow(
                title = "VidLink",
                subtitle = "High-quality backup stream",
                selected = currentServerId == StreamService.VIDLINK_SERVER_ID,
                accent = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.PlayArrow,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelectServer(StreamService.VIDLINK_SERVER_ID)
                    onDismiss()
                }
            )

            if (currentServerId == StreamService.VIDSRC_SERVER_ID) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (showVidSrcDomains) "Hide VidSrc domains" else "Change VidSrc domain",
                            onClick = { showVidSrcDomains = !showVidSrcDomains }
                        )
                        .padding(vertical = 12.dp)
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "VidSrc domain",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Preferred: ${domainOrder.firstOrNull() ?: currentVidSrcServerHost}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        if (showVidSrcDomains) "Hide" else "Change",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (showVidSrcDomains) {
                    domainOrder.forEachIndexed { index, host ->
                        val isActive = host == currentVidSrcServerHost
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    else Color.Transparent
                                )
                                .clickable(
                                    role = Role.RadioButton,
                                    onClickLabel = "Use $host",
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectVidSrcServer(host)
                                        onSelectServer(StreamService.VIDSRC_SERVER_ID)
                                    }
                                )
                                .semantics { selected = isActive }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                host,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (isActive) "Active" else "",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            IconButton(
                                onClick = { moveDomain(index, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowUpward,
                                    contentDescription = "Move $host up",
                                    tint = if (index > 0) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { moveDomain(index, 1) },
                                enabled = index < domainOrder.lastIndex,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowDownward,
                                    contentDescription = "Move $host down",
                                    tint = if (index < domainOrder.lastIndex) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    if (domainOrder != StreamService.normalizeVidSrcServerOrder(vidSrcServerOrder)) {
                        Text(
                            "Save domain order",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Save domain order",
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSaveVidSrcServerOrder(domainOrder)
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 12.dp)
                                .heightIn(min = 48.dp)
                                .sizeIn(minWidth = 48.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "YouTube player design • volume uses your device controls",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
        }
    }
}

@Composable
private fun StreamProviderRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (selected) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp)
            )
            .clickable(
                role = Role.RadioButton,
                onClickLabel = "Use $title",
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "$title selected", tint = accent, modifier = Modifier.size(20.dp))
    }
}

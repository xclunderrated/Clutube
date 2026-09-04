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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101010),
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
                    Text("Choose server", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Select where you want to play", color = Color.White.copy(alpha = 0.58f), fontSize = 12.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.8f))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            StreamProviderRow(
                title = "VidSrc",
                subtitle = "Fast adaptive playback",
                selected = currentServerId == StreamService.VIDSRC_SERVER_ID,
                accent = Color(0xFFFF0000),
                icon = Icons.Default.Tune,
                onClick = {
                    onSelectServer(StreamService.VIDSRC_SERVER_ID)
                    onDismiss()
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            StreamProviderRow(
                title = "VidLink",
                subtitle = "High-quality backup stream",
                selected = currentServerId == StreamService.VIDLINK_SERVER_ID,
                accent = Color(0xFFFF0000),
                icon = Icons.Default.PlayArrow,
                onClick = {
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
                        .clickable { showVidSrcDomains = !showVidSrcDomains }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("VidSrc domain", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Preferred: ${domainOrder.firstOrNull() ?: currentVidSrcServerHost}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                    Text(if (showVidSrcDomains) "Hide" else "Change", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                if (showVidSrcDomains) {
                    domainOrder.forEachIndexed { index, host ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (host == currentVidSrcServerHost) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable {
                                    onSelectVidSrcServer(host)
                                    onSelectServer(StreamService.VIDSRC_SERVER_ID)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(host, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(if (host == currentVidSrcServerHost) "Active" else "", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                            IconButton(onClick = { moveDomain(index, -1) }, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                                Text("↑", color = if (index > 0) Color.White else Color.White.copy(alpha = 0.2f), fontSize = 16.sp)
                            }
                            IconButton(onClick = { moveDomain(index, 1) }, enabled = index < domainOrder.lastIndex, modifier = Modifier.size(28.dp)) {
                                Text("↓", color = if (index < domainOrder.lastIndex) Color.White else Color.White.copy(alpha = 0.2f), fontSize = 16.sp)
                            }
                        }
                    }
                    if (domainOrder != StreamService.normalizeVidSrcServerOrder(vidSrcServerOrder)) {
                        Text(
                            "Save domain order",
                            color = Color(0xFFFF0000),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onSaveVidSrcServerOrder(domainOrder) }
                                .padding(start = 10.dp, top = 7.dp, bottom = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "YouTube player design • volume uses your device controls",
                color = Color.White.copy(alpha = 0.42f),
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
            .background(if (selected) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.045f))
            .border(1.dp, if (selected) accent else Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) accent else Color.White.copy(alpha = 0.72f), modifier = Modifier.size(21.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", tint = accent, modifier = Modifier.size(20.dp))
    }
}

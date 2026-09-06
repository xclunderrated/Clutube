package com.example.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.YouTubeRed

@Composable
fun AddMagnetDialog(
    onDismiss: () -> Unit,
    onAddMagnet: (magnetUri: String, customTitle: String?, directUrl: String?) -> Unit
) {
    val context = LocalContext.current
    var magnetInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ADD MAGNET / TORRENT",
                        style = MaterialTheme.typography.titleSmall,
                        color = YouTubeRed,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Magnet URI Input
                OutlinedTextField(
                    value = magnetInput,
                    onValueChange = { magnetInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("magnet_input_field"),
                    label = { Text("Magnet Link or Media URL") },
                    placeholder = { Text("magnet:?xt=urn:btih:...") },
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = clipboard.primaryClip
                            if (clip != null && clip.itemCount > 0) {
                                magnetInput = clip.getItemAt(0).text?.toString() ?: ""
                            }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = YouTubeRed)
                        }
                    },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YouTubeRed,
                        focusedLabelColor = YouTubeRed,
                        cursorColor = YouTubeRed
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Optional Custom Title Input
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title (Optional)") },
                    placeholder = { Text("e.g. My Download") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = YouTubeRed,
                        focusedLabelColor = YouTubeRed,
                        cursorColor = YouTubeRed
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Start Download Button
                Button(
                    onClick = {
                        val input = magnetInput.trim()
                        if (input.isBlank()) {
                            Toast.makeText(context, "Please enter a valid link", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true)) {
                            // Direct URL
                            val name = titleInput.ifBlank { input.substringAfterLast("/").substringBefore("?") }
                            val magnetFallback = "magnet:?xt=urn:btih:0000000000000000000000000000000000000000&dn=${java.net.URLEncoder.encode(name, "UTF-8")}"
                            onAddMagnet(magnetFallback, name, input)
                        } else {
                            onAddMagnet(input, titleInput.takeIf { it.isNotBlank() }, null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("submit_magnet_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YouTubeRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Download", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

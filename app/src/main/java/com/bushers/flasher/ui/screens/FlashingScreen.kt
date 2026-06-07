package com.bushers.flasher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bushers.flasher.theme.JetBrainsMono

@Composable
fun FlashingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Device Info Card
        ElevatedCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(6.dp, MaterialTheme.colorScheme.tertiaryContainer), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = "TARGET DEVICE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ESP32-S3 WROOM-1",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "CONNECTED",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Port",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "/dev/ttyUSB0",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Progress Indicator
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(192.dp)) {
                CircularProgressIndicator(
                    progress = { 0.68f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 8.dp
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "68%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "FLASHING...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Terminal Output Log
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "LIVE TERMINAL LOG",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = Color(0xFF000000), // Absolute pitch black for terminal
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TerminalLine("INFO", "Connecting to /dev/ttyUSB0...", Color(0xFFD7E2FF))
                    TerminalLine("INFO", "Chip is ESP32-S3 (revision v0.1)", Color(0xFFD7E2FF))
                    TerminalLine("INFO", "Features: WiFi, BLE", Color(0xFFD7E2FF))
                    TerminalLine("INFO", "MAC: 7c:df:a1:e0:1a:2b", Color(0xFFD7E2FF))
                    TerminalLine("INFO", "Uploading stub...", Color(0xFFD7E2FF))
                    TerminalLine("INFO", "Running stub...", Color(0xFFD7E2FF))
                    TerminalLine("INFO", "Changing baud rate to 460800", Color(0xFFD7E2FF))
                    TerminalLine("WARN", "Unexpected response from chip, retrying...", Color(0xFFFAD900))
                    TerminalLine("INFO", "Erasing flash (this may take a while)...", Color(0xFFD7E2FF))
                    TerminalLine("INFO", "Writing at 0x00010000... (24 %)", Color(0xFFD7E2FF))
                    TerminalLine("INFO", "Writing at 0x00014000... (48 %)", Color(0xFFD7E2FF))
                    TerminalLine("ERR", "Checksum mismatch at 0x00014000. Re-transmitting.", Color(0xFFBB121A))
                    TerminalLine("INFO", "Writing at 0x00014000... (48 %) - RETRY OK", Color(0xFFD7E2FF))
                    TerminalLine("INFO", "Writing at 0x00018000... (68 %)", Color(0xFFD7E2FF))
                }
            }
        }

        // Action Button
        Column(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { /* TODO */ },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ABORT FLASHING", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "Do not disconnect device while flashing is active.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun TerminalLine(tag: String, message: String, tagColor: Color) {
    Row {
        Text(
            text = tag,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
            color = tagColor
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono),
            color = Color(0xFFE0E3E8) // Light text for dark terminal
        )
    }
}

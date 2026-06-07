package com.bushers.flasher.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bushers.flasher.theme.JetBrainsMono

import androidx.compose.material3.ExtendedFloatingActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Search & Filter Area
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "DEVICE FILTER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("Search COM ports, MAC addresses...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.secondary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }

            // Connected Device Card
            DeviceCard(
                deviceName = "ESP32-S3-WROOM-1",
                icon = Icons.Default.Usb,
                statusText = "CONNECTED",
                statusColor = MaterialTheme.colorScheme.secondary,
                statusTextColor = MaterialTheme.colorScheme.onSecondary,
                info1Label = "PORT",
                info1Value = "COM4",
                info2Label = "MAC ADDRESS",
                info2Value = "34:85:18:01:A2:4B",
                isWarning = false,
                isOffline = false
            )

            // Warning Device Card
            val warningColor = Color(0xFFFAD900)
            val onWarningColor = Color(0xFF212529)
            DeviceCard(
                deviceName = "ESP32-C3-MINI-1",
                icon = Icons.Default.Wifi,
                statusText = "WEAK SIGNAL",
                statusColor = warningColor,
                statusTextColor = onWarningColor,
                info1Label = "IP ADDRESS",
                info1Value = "192.168.1.104",
                info2Label = "RSSI",
                info2Value = "-82 dBm",
                isWarning = true,
                isOffline = false
            )

            // Offline Device Card
            DeviceCard(
                deviceName = "Unknown ESP8266",
                icon = Icons.Default.UsbOff,
                statusText = "OFFLINE",
                statusColor = MaterialTheme.colorScheme.surfaceVariant,
                statusTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                info1Label = "PORT",
                info1Value = "COM3",
                info2Label = "LAST SEEN",
                info2Value = "10 mins ago",
                isWarning = false,
                isOffline = true
            )
            
            Spacer(modifier = Modifier.height(88.dp)) // For FAB
        }

        ExtendedFloatingActionButton(
            text = { Text("SCAN", style = MaterialTheme.typography.labelLarge) },
            icon = { Icon(Icons.Default.Radar, contentDescription = "Scan") },
            onClick = { /* TODO */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun DeviceCard(
    deviceName: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    statusText: String,
    statusColor: Color,
    statusTextColor: Color,
    info1Label: String,
    info1Value: String,
    info2Label: String,
    info2Value: String,
    isWarning: Boolean,
    isOffline: Boolean
) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.background(
                if (isOffline) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(statusColor)
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isOffline) MaterialTheme.colorScheme.onSurfaceVariant else statusColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isOffline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Surface(
                        color = statusColor,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = statusTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info1Label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = info1Value,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                            color = if (isOffline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = info2Label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = info2Value,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold),
                            color = if (isOffline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (isOffline) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("UNAVAILABLE", style = MaterialTheme.typography.labelMedium)
                        }
                    } else if (isWarning) {
                        OutlinedButton(
                            onClick = {},
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("SELECT", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Button(
                            onClick = {},
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("SELECT", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

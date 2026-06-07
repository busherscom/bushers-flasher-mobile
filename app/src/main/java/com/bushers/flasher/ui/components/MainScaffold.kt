package com.bushers.flasher.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.res.stringResource
import com.bushers.flasher.R

enum class BottomNavItem(val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Devices(R.string.devices, Icons.Default.Router),
    Flashing(R.string.flashing, Icons.Default.Bolt),
    About(R.string.about, Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    selectedTab: BottomNavItem,
    onTabSelected: (BottomNavItem) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val items = BottomNavItem.entries.toTypedArray()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.system_status),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = stringResource(R.string.memory),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    val title = stringResource(item.titleRes)
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = title) },
                        label = { Text(title, style = MaterialTheme.typography.labelMedium) },
                        selected = selectedTab == item,
                        onClick = { onTabSelected(item) }
                    )
                }
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

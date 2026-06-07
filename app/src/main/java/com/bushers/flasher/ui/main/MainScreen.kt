package com.bushers.flasher.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.bushers.flasher.ui.components.BottomNavItem
import com.bushers.flasher.ui.components.MainScaffold
import com.bushers.flasher.ui.screens.AboutScreen
import com.bushers.flasher.ui.screens.DevicesScreen
import com.bushers.flasher.ui.screens.FlashingScreen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith

import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FlasherViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(BottomNavItem.Devices) }

    MainScaffold(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    BottomNavItem.Devices -> {
                        DevicesScreen(
                            viewModel = viewModel,
                            onDeviceSelected = {
                                selectedTab = BottomNavItem.Flashing
                            }
                        )
                    }
                    BottomNavItem.Flashing -> {
                        FlashingScreen(viewModel)
                    }
                    BottomNavItem.About -> {
                        AboutScreen()
                    }
                }
            }
        }
    }
}

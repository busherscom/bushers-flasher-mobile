package com.bushers.flasher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.bushers.flasher.theme.BushersFlasherTheme

import androidx.activity.viewModels
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.bushers.flasher.ui.main.FlasherViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: FlasherViewModel by viewModels()

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.bushers.flasher.DEBUG_SCAN" -> {
                    Log.d("DebugHook", "Triggering Scan")
                    viewModel.scanDevices()
                }
                "com.bushers.flasher.DEBUG_SELECT_INDEX" -> {
                    val index = intent.getIntExtra("index", -1)
                    Log.d("DebugHook", "Selecting index $index")
                    viewModel.getDeviceByIndex(index)?.let { viewModel.selectDevice(it) }
                }
                "com.bushers.flasher.DEBUG_FLASH" -> {
                    Log.d("DebugHook", "Triggering Flash")
                    viewModel.startFlashing()
                }
                "com.bushers.flasher.DEBUG_MOCK_MODE" -> {
                    val enabled = intent.getBooleanExtra("enabled", false)
                    Log.d("DebugHook", "Setting Mock Mode: $enabled")
                    viewModel.setMockMode(enabled)
                }
                "com.bushers.flasher.DEBUG_RESTART" -> {
                    Log.d("DebugHook", "Restarting Process...")
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        val imageLoader = ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
        Coil.setImageLoader(imageLoader)

        val filter = IntentFilter().apply {
            addAction("com.bushers.flasher.DEBUG_SCAN")
            addAction("com.bushers.flasher.DEBUG_SELECT_INDEX")
            addAction("com.bushers.flasher.DEBUG_FLASH")
        }
        registerReceiver(debugReceiver, filter, RECEIVER_EXPORTED)

        enableEdgeToEdge()
        setContent {
            BushersFlasherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(debugReceiver) }
    }
}

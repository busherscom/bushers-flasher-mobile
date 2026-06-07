package com.bushers.flasher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.bushers.flasher.theme.BushersFlasherTheme
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
                    Log.d("DebugHook", "Selecting device at index $index")
                    viewModel.getDeviceByIndex(index)?.let { viewModel.selectDevice(it) }
                }
                "com.bushers.flasher.DEBUG_FLASH" -> {
                    Log.d("DebugHook", "Triggering Flash")
                    viewModel.startFlashing()
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

        // Register debug hook
        val filter = IntentFilter().apply {
            addAction("com.bushers.flasher.DEBUG_SCAN")
            addAction("com.bushers.flasher.DEBUG_SELECT_INDEX")
            addAction("com.bushers.flasher.DEBUG_FLASH")
        }
        ContextCompat.registerReceiver(this, debugReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

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
        try {
            unregisterReceiver(debugReceiver)
        } catch (e: Exception) {}
    }
}

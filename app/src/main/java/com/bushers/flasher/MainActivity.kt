package com.bushers.flasher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.bushers.flasher.theme.BushersFlasherTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    
    val imageLoader = ImageLoader.Builder(this)
        .components { add(SvgDecoder.Factory()) }
        .build()
    Coil.setImageLoader(imageLoader)

    enableEdgeToEdge()
    setContent {
      BushersFlasherTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }
}

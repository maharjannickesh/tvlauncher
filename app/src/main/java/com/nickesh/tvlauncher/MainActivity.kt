package com.nickesh.tvlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.nickesh.tvlauncher.ui.theme.AccentOptions
import com.nickesh.tvlauncher.ui.theme.Outfit
import com.nickesh.tvlauncher.ui.theme.TvLauncherTheme
import com.nickesh.tvlauncher.ui.theme.spx

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvLauncherTheme(accent = AccentOptions[0]) {
                Box(Modifier.fillMaxSize().background(Color(0xFF0B0E1A))) {
                    Text(
                        "TV Launcher",
                        color = Color.White,
                        fontSize = 96.spx(),
                        fontFamily = Outfit,
                        fontWeight = FontWeight.W200,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

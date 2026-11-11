package com.example.stopscrolling

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.stopscrolling.ui.navigation.AppNavHost
import com.example.stopscrolling.ui.theme.StopScrollingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StopScrollingTheme {
                AppNavHost()
            }
        }
    }
}
package com.indexdca.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data class Detail(val holdingId: String) : Screen
    data class Ai(val holdingId: String?) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppRoot() } }
    }
}

@Composable
fun AppRoot() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    when (val s = screen) {
        Screen.Home -> HomeScreen(
            onAdd = { screen = Screen.Search },
            onOpen = { id -> screen = Screen.Detail(id) },
            onAi = { screen = Screen.Ai(null) },
            onSettings = { screen = Screen.Settings }
        )
        Screen.Search -> SearchScreen(
            onBack = { screen = Screen.Home },
            onOpen = { id -> screen = Screen.Detail(id) }
        )
        is Screen.Detail -> DetailScreen(
            holdingId = s.holdingId,
            onBack = { screen = Screen.Home },
            onAi = { screen = Screen.Ai(s.holdingId) }
        )
        is Screen.Ai -> AiScreen(
            holdingId = s.holdingId,
            onBack = { screen = Screen.Home },
            onSettings = { screen = Screen.Settings }
        )
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
    }
}

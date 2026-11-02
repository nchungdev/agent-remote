package app.duinch.agentremote

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.duinch.agentremote.app.App

fun main() = application {
    val windowState = rememberWindowState(width = 440.dp, height = 800.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Agent Remote"
    ) {
        App()
    }
}

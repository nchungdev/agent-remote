package app.duinch.agentremote

import agent_remote.generated.resources.Res
import agent_remote.generated.resources.app_logo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.duinch.agentremote.app.App
import org.jetbrains.compose.resources.painterResource

fun main() = application {
    val windowState = rememberWindowState(width = 1120.dp, height = 760.dp)
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Agent Remote",
        icon = painterResource(Res.drawable.app_logo)
    ) {
        App()
    }
}

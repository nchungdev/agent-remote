package app.duinch.agentremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.duinch.agentremote.app.App
import app.duinch.agentremote.core.presentation.util.AppContextProvider
import app.duinch.agentremote.core.presentation.util.PlatformAlertNotifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        AppContextProvider.context = applicationContext
        setContent {
            App()
        }
    }

    override fun onStart() {
        super.onStart()
        PlatformAlertNotifier.isAppInForeground = true
    }

    override fun onStop() {
        PlatformAlertNotifier.isAppInForeground = false
        super.onStop()
    }
}

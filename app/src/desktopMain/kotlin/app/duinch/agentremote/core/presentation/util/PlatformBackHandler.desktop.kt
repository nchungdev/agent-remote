package app.duinch.agentremote.core.presentation.util

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop does not have system back gesture
}

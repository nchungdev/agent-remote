package app.duinch.agentremote.core.presentation.util

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS handles back navigation via gesture
}

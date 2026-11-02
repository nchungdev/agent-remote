package app.duinch.agentremote.core.presentation.util

import androidx.compose.runtime.Composable

interface PlatformQrScanner {
    val isSupported: Boolean
    fun startScan(onResult: (String) -> Unit)
}

@Composable
expect fun rememberPlatformQrScanner(): PlatformQrScanner

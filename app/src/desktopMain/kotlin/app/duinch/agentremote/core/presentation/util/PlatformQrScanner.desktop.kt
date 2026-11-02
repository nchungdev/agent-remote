package app.duinch.agentremote.core.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPlatformQrScanner(): PlatformQrScanner {
    return remember {
        object : PlatformQrScanner {
            override val isSupported: Boolean = false
            override fun startScan(onResult: (String) -> Unit) {}
        }
    }
}

package app.duinch.agentremote.core.presentation.util

import androidx.compose.runtime.Composable

@Composable
actual fun rememberPlatformFilePicker(
    onFilePicked: (fileName: String, bytes: ByteArray) -> Unit
): () -> Unit {
    return {}
}

@Composable
actual fun rememberPlatformVoiceInput(
    onTextRecognized: (String) -> Unit
): () -> Unit {
    return {}
}

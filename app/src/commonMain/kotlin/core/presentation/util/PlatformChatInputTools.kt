package app.duinch.agentremote.core.presentation.util

import androidx.compose.runtime.Composable

@Composable
expect fun rememberPlatformFilePicker(
    onFilePicked: (fileName: String, bytes: ByteArray) -> Unit
): () -> Unit

@Composable
expect fun rememberPlatformVoiceInput(
    onTextRecognized: (String) -> Unit
): () -> Unit

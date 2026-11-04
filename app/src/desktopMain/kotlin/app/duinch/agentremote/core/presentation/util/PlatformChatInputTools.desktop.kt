package app.duinch.agentremote.core.presentation.util

import androidx.compose.runtime.Composable
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberPlatformFilePicker(
    onFilePicked: (fileName: String, bytes: ByteArray) -> Unit
): () -> Unit {
    return {
        runCatching {
            val dialog = FileDialog(null as Frame?, "Chọn tệp đính kèm", FileDialog.LOAD)
            dialog.isVisible = true
            val dir = dialog.directory
            val file = dialog.file
            if (dir != null && file != null) {
                val f = File(dir, file)
                if (f.exists() && f.isFile) {
                    onFilePicked(f.name, f.readBytes())
                }
            }
        }
    }
}

@Composable
actual fun rememberPlatformVoiceInput(
    onTextRecognized: (String) -> Unit
): () -> Unit {
    return {}
}

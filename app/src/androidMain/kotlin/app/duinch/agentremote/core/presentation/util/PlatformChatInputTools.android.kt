package app.duinch.agentremote.core.presentation.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@Composable
actual fun rememberPlatformFilePicker(
    onFilePicked: (fileName: String, bytes: ByteArray) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val resolver = context.contentResolver
            var displayName = "attachment"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        displayName = cursor.getString(idx) ?: displayName
                    }
                }
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
            if (bytes.isNotEmpty()) {
                onFilePicked(displayName, bytes)
            }
        }
    }

    return {
        runCatching { launcher.launch("*/*") }
    }
}

@Composable
actual fun rememberPlatformVoiceInput(
    onTextRecognized: (String) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                onTextRecognized(spoken)
            }
        }
    }

    return {
        runCatching {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói lệnh điều khiển Agent...")
            }
            launcher.launch(intent)
        }
    }
}

package app.duinch.agentremote.features.updater.data

import android.content.Intent
import androidx.core.content.FileProvider
import app.duinch.agentremote.core.presentation.util.AppContextProvider
import java.io.File

actual class PlatformApkInstaller {
    actual fun saveApkBytes(bytes: ByteArray): String {
        val ctx = AppContextProvider.context ?: return ""
        return try {
            val file = File(ctx.cacheDir, "agent-remote-update.apk")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            ""
        }
    }

    actual fun installApk(filePath: String): Boolean {
        val ctx = AppContextProvider.context ?: return false
        return try {
            val file = File(filePath)
            if (!file.isFile) return false

            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

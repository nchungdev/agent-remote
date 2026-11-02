package app.duinch.agentremote.features.updater.data

actual class PlatformApkInstaller {
    actual fun saveApkBytes(bytes: ByteArray): String = "update_downloaded.apk"

    actual fun installApk(filePath: String): Boolean {
        // Desktop can open file location or download link
        return true
    }
}

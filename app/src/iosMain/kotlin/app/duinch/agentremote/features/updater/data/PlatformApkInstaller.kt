package app.duinch.agentremote.features.updater.data

actual class PlatformApkInstaller {
    actual fun saveApkBytes(bytes: ByteArray): String = ""

    actual fun installApk(filePath: String): Boolean {
        // iOS does not support APK installation
        return false
    }
}

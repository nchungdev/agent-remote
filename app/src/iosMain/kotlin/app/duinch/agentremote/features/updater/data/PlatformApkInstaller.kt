package app.duinch.agentremote.features.updater.data

actual class PlatformApkInstaller {
    actual fun installApk(filePath: String): Boolean {
        // iOS does not support APK installation
        return false
    }
}

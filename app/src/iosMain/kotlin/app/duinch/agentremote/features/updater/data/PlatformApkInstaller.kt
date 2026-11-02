package app.duinch.agentremote.features.updater.data

actual class PlatformApkInstaller {
    actual fun saveApkBytes(bytes: ByteArray, expectedSha256: String): String = ""

    actual fun installApk(filePath: String): Boolean = false
}

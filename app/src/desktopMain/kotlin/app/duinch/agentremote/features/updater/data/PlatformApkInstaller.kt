package app.duinch.agentremote.features.updater.data

import java.security.MessageDigest

actual class PlatformApkInstaller {
    actual fun saveApkBytes(bytes: ByteArray, expectedSha256: String): String {
        if (expectedSha256.isNotBlank()) {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val actualHex = digest.joinToString("") { "%02x".format(it) }
            if (!actualHex.equals(expectedSha256.trim(), ignoreCase = true)) {
                return ""
            }
        }
        return "update_downloaded.apk"
    }

    actual fun installApk(filePath: String): Boolean = true
}

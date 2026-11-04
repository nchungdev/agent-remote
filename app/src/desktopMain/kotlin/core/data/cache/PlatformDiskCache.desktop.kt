package app.duinch.agentremote.core.data.cache

import java.io.File

actual class PlatformDiskCache actual constructor() {
    private val cacheDir: File by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val dir = File(userHome, ".config/agent-remote/cache")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    actual fun get(key: String): String? {
        return try {
            val file = File(cacheDir, sanitizeKey(key))
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    actual fun put(key: String, value: String) {
        try {
            val file = File(cacheDir, sanitizeKey(key))
            file.writeText(value)
        } catch (_: Exception) {
        }
    }

    actual fun remove(key: String) {
        try {
            val file = File(cacheDir, sanitizeKey(key))
            if (file.exists()) file.delete()
        } catch (_: Exception) {
        }
    }

    actual fun clear() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    private fun sanitizeKey(key: String): String =
        key.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".json"
}

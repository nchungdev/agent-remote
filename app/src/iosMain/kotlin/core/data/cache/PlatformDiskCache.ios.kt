package app.duinch.agentremote.core.data.cache

actual class PlatformDiskCache actual constructor() {
    private val inMemory = mutableMapOf<String, String>()

    actual fun get(key: String): String? = inMemory[key]
    actual fun put(key: String, value: String) { inMemory[key] = value }
    actual fun remove(key: String) { inMemory.remove(key) }
    actual fun clear() { inMemory.clear() }
}

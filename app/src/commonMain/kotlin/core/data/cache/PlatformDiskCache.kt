package app.duinch.agentremote.core.data.cache

/**
 * Platform abstraction for fast, lightweight key-value disk caching.
 * Used for persistent L2 caching of conversations and engine catalogs.
 */
expect class PlatformDiskCache() {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
    fun clear()
}

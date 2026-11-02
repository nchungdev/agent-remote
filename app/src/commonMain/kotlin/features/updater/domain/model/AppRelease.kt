package app.duinch.agentremote.features.updater.domain.model

data class AppRelease(
    val tagName: String,
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String?,
    val publishedAt: String,
    val isNewerThanCurrent: Boolean
)

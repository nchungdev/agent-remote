package app.duinch.agentremote.core.domain.model

enum class AgentType(val displayName: String, val badge: String) {
    ANTIGRAVITY("Antigravity", "AGY"),
    CLAUDE_CODE("Claude Code", "CLAUDE"),
    CODEX("Codex", "CODEX"),
    MOCK("Mock Agent", "MOCK");

    companion object {
        fun fromString(value: String): AgentType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MOCK
        }
    }
}

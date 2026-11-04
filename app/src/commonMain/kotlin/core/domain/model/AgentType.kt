package app.duinch.agentremote.core.domain.model

enum class AgentType(val displayName: String, val badge: String) {
    ANTIGRAVITY("Antigravity", "AGY"),
    CLAUDE_CODE("Claude Code", "CLAUDE"),
    CODEX("Codex", "CODEX"),
    MOCK("Mock Agent", "MOCK");

    fun defaultModels(): List<AgentModel> {
        return when (this) {
            ANTIGRAVITY -> listOf(
                AgentModel("gemini-4-argon-medium", "Gemini 4 Argon (Medium)", "Mặc định (Khuyên dùng)"),
                AgentModel("gemini-4-argon-high", "Gemini 4 Argon (High)", "Suy luận nâng cao"),
                AgentModel("gemini-3.8-flash-high", "Gemini 3.8 Flash (High)", "Phản hồi siêu tốc"),
                AgentModel("claude-sonnet-5-5-medium", "Claude Sonnet 5.5", "Sonnet 5.5"),
                AgentModel("claude-opus-5-5-high", "Claude Opus 5.5", "Opus 5.5 Deep Thinking"),
                AgentModel("gpt-oss-120b-medium", "GPT-OSS 120B", "Mã nguồn mở")
            )
            CLAUDE_CODE -> listOf(
                AgentModel("claude-3-7-sonnet-thinking", "Claude 3.7 Sonnet Thinking", "Thinking mở rộng"),
                AgentModel("claude-3-7-sonnet", "Claude 3.7 Sonnet", "Mặc định (Sonnet)"),
                AgentModel("claude-3-5-sonnet", "Claude 3.5 Sonnet", "Ổn định"),
                AgentModel("claude-3-5-haiku", "Claude 3.5 Haiku", "Siêu tốc"),
                AgentModel("claude-3-opus", "Claude 3 Opus", "Lập trình chuyên sâu")
            )
            CODEX -> listOf(
                AgentModel("o3-mini", "o3-mini", "Reasoning tối ưu"),
                AgentModel("o1", "o1", "Khuyên dùng lập trình"),
                AgentModel("gpt-4o", "GPT-4o", "Omni cân bằng")
            )
            MOCK -> listOf(
                AgentModel("mock-fast", "Mock Fast", "Thử nghiệm siêu tốc"),
                AgentModel("mock-thinking", "Mock Thinking", "Mô phỏng suy luận"),
                AgentModel("mock-tool-approval", "Mock Tool Approval", "Kiểm thử cấp quyền")
            )
        }
    }

    fun permissionModes(): List<PermissionMode> {
        return when (this) {
            ANTIGRAVITY -> listOf(
                PermissionMode("plan", "Request Review", "Every command requires approval.", PermissionIconType.EYE),
                PermissionMode("accept-edits", "Default", "Ask before sensitive operations.", PermissionIconType.LOCK),
                PermissionMode("auto", "Auto (Preview)", "Automatically decide if commands run.", PermissionIconType.SHIELD),
                PermissionMode("turbo", "Turbo", "Run all commands without restrictions.", PermissionIconType.TURBO)
            )
            CLAUDE_CODE -> listOf(
                PermissionMode("plan", "Plan Mode", "Read-only analysis & planning.", PermissionIconType.EYE),
                PermissionMode("default", "Default", "Ask before edits & bash commands.", PermissionIconType.LOCK),
                PermissionMode("acceptEdits", "Accept Edits", "Auto-accept file edits, ask before bash.", PermissionIconType.SHIELD),
                PermissionMode("bypassPermissions", "Bypass", "Run all tools without permission prompts.", PermissionIconType.TURBO)
            )
            CODEX -> listOf(
                PermissionMode("read-only", "Read Only", "Inspect files only, no edits or commands.", PermissionIconType.EYE),
                PermissionMode("workspace-write", "Default", "Edit workspace files, ask on request.", PermissionIconType.LOCK),
                PermissionMode("approve-for-me", "Auto Review", "Automatic review inside workspace sandbox.", PermissionIconType.SHIELD),
                PermissionMode("full-access", "Full Access", "Run commands without sandboxing or prompts.", PermissionIconType.TURBO)
            )
            MOCK -> listOf(
                PermissionMode("ask", "Request Review", "Every command requires approval.", PermissionIconType.EYE),
                PermissionMode("auto", "Turbo", "Run all commands without restrictions.", PermissionIconType.TURBO)
            )
        }
    }

    fun defaultPermissionMode(): PermissionMode {
        val modes = permissionModes()
        return modes.firstOrNull { it.id == "accept-edits" || it.id == "default" || it.id == "workspace-write" }
            ?: modes.first()
    }

    companion object {
        fun fromString(value: String): AgentType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: MOCK
        }
    }
}


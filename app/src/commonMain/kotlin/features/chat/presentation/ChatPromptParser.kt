package app.duinch.agentremote.features.chat.presentation

import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.features.chat.presentation.contract.AttachedFileItem

object ChatPromptParser {

    data class ParsedPrompt(
        val isClearCommand: Boolean = false,
        val consultTarget: AgentType? = null,
        val consultQuery: String? = null,
        val formattedText: String = ""
    )

    fun parse(rawText: String, attachments: List<AttachedFileItem>, defaultAgent: AgentType): ParsedPrompt {
        val trimmed = rawText.trim()
        if (trimmed.equals("/clear", ignoreCase = true) || trimmed.equals("/new", ignoreCase = true)) {
            return ParsedPrompt(isClearCommand = true)
        }

        val consultRegex = Regex("""^@(\w+):btw\s+(.*)$""", RegexOption.DOT_MATCHES_ALL)
        val consultMatch = consultRegex.find(trimmed)
        if (consultMatch != null) {
            val agentAlias = consultMatch.groupValues[1].lowercase()
            val query = consultMatch.groupValues[2].trim()
            val target = when (agentAlias) {
                "codex" -> AgentType.CODEX
                "claude", "claudecode" -> AgentType.CLAUDE_CODE
                "antigravity", "agy" -> AgentType.ANTIGRAVITY
                else -> defaultAgent
            }
            return ParsedPrompt(consultTarget = target, consultQuery = query, formattedText = trimmed)
        }

        val attachmentSuffix = if (attachments.isNotEmpty()) {
            val paths = attachments.joinToString(", ") { "${it.fileName} (${it.remotePath})" }
            if (trimmed.isEmpty()) "Phân tích tệp đính kèm: $paths" else "\n\n[Tệp đính kèm: $paths]"
        } else ""

        return ParsedPrompt(formattedText = trimmed + attachmentSuffix)
    }
}

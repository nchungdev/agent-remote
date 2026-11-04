package app.duinch.agentremote.core.domain.model

data class ConversationTokenUsage(
    val inputTokens: Int = 0,
    val cacheReadTokens: Int = 0,
    val outputTokens: Int = 0,
    val totalTokens: Int = 0,
    val contextLimit: Int = 1_000_000,
    val fiveHourLimitPercent: Int = 14,
    val fiveHourResetTime: String = "53m",
    val weeklyLimitPercent: Int = 51,
    val weeklyResetTime: String = "5d 10h"
) {
    val usagePercentage: Float
        get() = if (contextLimit > 0) (totalTokens.toFloat() / contextLimit).coerceIn(0f, 1f) else 0f

    val remainingTokens: Int
        get() = (contextLimit - totalTokens).coerceAtLeast(0)

    val isWarning: Boolean
        get() = usagePercentage >= 0.75f

    val isDanger: Boolean
        get() = usagePercentage >= 0.90f

    fun formattedTotal(): String = formatTokenCount(totalTokens)
    fun formattedLimit(): String = formatTokenCount(contextLimit)
    fun formattedInput(): String = formatTokenCount(inputTokens)
    fun formattedCache(): String = formatTokenCount(cacheReadTokens)
    fun formattedOutput(): String = formatTokenCount(outputTokens)
    fun formattedRemaining(): String = formatTokenCount(remainingTokens)

    private fun formatTokenCount(tokens: Int): String {
        return when {
            tokens >= 1_000_000 -> {
                val m = tokens / 1_000_000.0
                if (tokens % 1_000_000 == 0) "${tokens / 1_000_000}M" else "${(tokens / 100_000) / 10.0}M"
            }
            tokens >= 1_000 -> "${tokens / 1_000}k"
            else -> tokens.toString()
        }
    }
}

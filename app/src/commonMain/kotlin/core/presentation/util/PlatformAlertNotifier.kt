package app.duinch.agentremote.core.presentation.util

expect object PlatformAlertNotifier {
    fun notifyConnectionChange(title: String, message: String)
    fun notifyAgentApproval(toolName: String, description: String)
}

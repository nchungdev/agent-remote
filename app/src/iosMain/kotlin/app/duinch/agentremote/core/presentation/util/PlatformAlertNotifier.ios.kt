package app.duinch.agentremote.core.presentation.util

actual object PlatformAlertNotifier {
    actual fun notifyConnectionChange(title: String, message: String) {
        println("[ConnectionAlert] $title: $message")
    }

    actual fun notifyAgentApproval(toolName: String, description: String) {
        println("[ApprovalAlert] $toolName: $description")
    }
}

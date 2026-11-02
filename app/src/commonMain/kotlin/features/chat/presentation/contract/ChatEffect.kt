package app.duinch.agentremote.features.chat.presentation.contract

sealed interface ChatEffect {
    data object ScrollToBottom : ChatEffect
    data class ShowSnackbar(val message: String) : ChatEffect
    data object TriggerHapticFeedback : ChatEffect
}

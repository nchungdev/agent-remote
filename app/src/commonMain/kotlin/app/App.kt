package app.duinch.agentremote.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.data.factory.createPlatformAgentGatewayClient
import app.duinch.agentremote.features.chat.presentation.ChatScreen
import app.duinch.agentremote.core.presentation.util.PlatformBackHandler
import app.duinch.agentremote.features.chat.presentation.ChatViewModel
import app.duinch.agentremote.features.chat.presentation.components.ConversationDrawerContent
import app.duinch.agentremote.features.chat.presentation.contract.ChatIntent
import app.duinch.agentremote.features.connect.presentation.ConnectScreen
import app.duinch.agentremote.features.engine.presentation.EngineSelectorScreen
import app.duinch.agentremote.features.settings.presentation.SettingsScreen
import app.duinch.agentremote.features.settings.presentation.SettingsViewModel
import app.duinch.agentremote.features.updater.data.GitHubAppUpdateRepository
import kotlinx.coroutines.launch

enum class AppScreen {
    CONNECT,
    SELECT_ENGINE,
    CHAT,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val client = remember { createPlatformAgentGatewayClient() }
    val chatViewModel = remember { ChatViewModel(client) }
    val updateRepository = remember { GitHubAppUpdateRepository() }
    val settingsViewModel = remember { SettingsViewModel(updateRepository, client) }

    val chatState by chatViewModel.uiState.collectAsState()

    var currentScreen by remember { mutableStateOf(AppScreen.CONNECT) }
    var previousScreen by remember { mutableStateOf(AppScreen.CONNECT) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Auto navigate from CONNECT to SELECT_ENGINE once connection is established
    LaunchedEffect(chatState.connectionStatus) {
        if (chatState.connectionStatus is ConnectionStatus.Connected && currentScreen == AppScreen.CONNECT) {
            currentScreen = AppScreen.SELECT_ENGINE
        }
    }

    // Handle system back gestures
    PlatformBackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    PlatformBackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.SETTINGS) {
        currentScreen = previousScreen
    }

    PlatformBackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.CHAT) {
        currentScreen = AppScreen.SELECT_ENGINE
    }

    PlatformBackHandler(enabled = !drawerState.isOpen && currentScreen == AppScreen.SELECT_ENGINE) {
        currentScreen = AppScreen.CONNECT
    }

    val darkThemeColors = darkColorScheme(
        primary = Color(0xFFCC785C),
        onPrimary = Color.Black,
        surface = Color(0xFF141416),
        onSurface = Color(0xFFF4F4F5),
        surfaceContainer = Color(0xFF1E1E22),
        surfaceContainerHigh = Color(0xFF26262B),
        background = Color(0xFF141416),
        onBackground = Color(0xFFF4F4F5),
        error = Color(0xFFEF4444)
    )

    MaterialTheme(colorScheme = darkThemeColors) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactScreen = maxWidth < 600.dp
            val isExpandedTablet = maxWidth >= 760.dp
            var isTabletSidebarOpen by remember { mutableStateOf(true) }

            val drawerModifier = if (isCompactScreen) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.width(340.dp)
            }

            val drawerComposable: @Composable (() -> Unit) -> Unit = { onClose ->
                ConversationDrawerContent(
                    selectedAgent = chatState.selectedAgent,
                    activeConversationId = chatState.activeConversationId,
                    catalog = chatState.catalogs[chatState.selectedAgent],
                    connectionStatus = chatState.connectionStatus,
                    gatewayUrl = chatState.gatewayUrl,
                    onSelectConversation = { conv ->
                        chatViewModel.processIntent(
                            ChatIntent.SelectConversation(
                                conversationId = conv.id,
                                title = conv.title,
                                agentType = chatState.selectedAgent,
                                workspace = conv.workspace
                            )
                        )
                        currentScreen = AppScreen.CHAT
                        if (!isExpandedTablet) onClose()
                    },
                    onNewConversation = {
                        chatViewModel.processIntent(ChatIntent.NewConversation)
                        currentScreen = AppScreen.CHAT
                        if (!isExpandedTablet) onClose()
                    },
                    onRefreshCatalog = {
                        if (!chatState.connectionStatus.isConnected && chatState.gatewayUrl.isNotBlank()) {
                            chatViewModel.processIntent(ChatIntent.Connect(chatState.gatewayUrl))
                        }
                        chatViewModel.processIntent(ChatIntent.RefreshCatalog)
                    },
                    onOpenEngineSelector = {
                        currentScreen = AppScreen.SELECT_ENGINE
                        onClose()
                    },
                    onOpenConnect = {
                        currentScreen = AppScreen.CONNECT
                        onClose()
                    },
                    onOpenSettings = {
                        previousScreen = currentScreen
                        currentScreen = AppScreen.SETTINGS
                        onClose()
                    },
                    onCloseDrawer = onClose,
                    modifier = Modifier.fillMaxSize()
                )
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = currentScreen == AppScreen.CHAT && !isExpandedTablet,
                drawerContent = {
                    Surface(
                        modifier = drawerModifier.fillMaxHeight(),
                        color = Color(0xFF141416),
                        contentColor = Color(0xFFF4F4F5),
                        shape = if (isCompactScreen) RectangleShape else RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                    ) {
                        drawerComposable { coroutineScope.launch { drawerState.close() } }
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF121215)),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentMaxWidth = if (isCompactScreen) Modifier.fillMaxSize() else Modifier.widthIn(max = 720.dp).fillMaxSize()

                    when (currentScreen) {
                        AppScreen.CONNECT -> {
                            ConnectScreen(
                                currentHost = chatState.gatewayUrl,
                                connectionStatus = chatState.connectionStatus,
                                onConnect = { host ->
                                    chatViewModel.processIntent(ChatIntent.UrlChanged(host))
                                    chatViewModel.processIntent(ChatIntent.Connect(host))
                                },
                                onDisconnect = {
                                    chatViewModel.processIntent(ChatIntent.Disconnect)
                                    chatViewModel.processIntent(ChatIntent.UrlChanged(""))
                                },
                                onOpenSettings = {
                                    previousScreen = currentScreen
                                    currentScreen = AppScreen.SETTINGS
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        AppScreen.SELECT_ENGINE -> {
                            EngineSelectorScreen(
                                currentAgent = chatState.selectedAgent,
                                catalogs = chatState.catalogs,
                                onSelectEngine = { agent ->
                                    chatViewModel.processIntent(ChatIntent.SelectAgent(agent))
                                    currentScreen = AppScreen.CHAT
                                },
                                onOpenSettings = {
                                    previousScreen = currentScreen
                                    currentScreen = AppScreen.SETTINGS
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        AppScreen.CHAT -> {
                            if (isExpandedTablet) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    if (isTabletSidebarOpen) {
                                        Box(modifier = Modifier.width(300.dp).fillMaxHeight()) {
                                            drawerComposable { isTabletSidebarOpen = false }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .width(1.dp)
                                                .fillMaxHeight()
                                                .background(Color(0xFF22222A))
                                        )
                                    }
                                    ChatScreen(
                                        viewModel = chatViewModel,
                                        onOpenDrawer = { isTabletSidebarOpen = !isTabletSidebarOpen },
                                        onOpenSettings = {
                                            previousScreen = currentScreen
                                            currentScreen = AppScreen.SETTINGS
                                        },
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            } else {
                                ChatScreen(
                                    viewModel = chatViewModel,
                                    onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                                    onOpenSettings = {
                                        previousScreen = currentScreen
                                        currentScreen = AppScreen.SETTINGS
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        AppScreen.SETTINGS -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { currentScreen = previousScreen },
                                onOpenConnect = {
                                    previousScreen = currentScreen
                                    currentScreen = AppScreen.CONNECT
                                },
                                onOpenEngineSelector = {
                                    previousScreen = currentScreen
                                    currentScreen = AppScreen.SELECT_ENGINE
                                },
                                onSelectEngine = { engine ->
                                    chatViewModel.processIntent(ChatIntent.SelectAgent(engine))
                                },
                                currentAgent = chatState.selectedAgent,
                                currentHost = chatState.gatewayUrl,
                                connectionStatus = chatState.connectionStatus,
                                modifier = contentMaxWidth
                            )
                        }
                    }
                }
            }
        }
}
}

@Composable
private fun DrawerConnectionDot(status: ConnectionStatus) {
    val color = when (status) {
        is ConnectionStatus.Connected -> Color(0xFF22C55E)
        ConnectionStatus.Connecting, is ConnectionStatus.Reconnecting -> Color(0xFFF59E0B)
        ConnectionStatus.Disconnected, is ConnectionStatus.Error -> Color(0xFFDC2626)
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}

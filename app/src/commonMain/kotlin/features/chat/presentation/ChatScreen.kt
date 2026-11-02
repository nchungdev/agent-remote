package app.duinch.agentremote.features.chat.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.presentation.util.isKeyboardVisible
import app.duinch.agentremote.features.chat.presentation.components.ApprovalCard
import app.duinch.agentremote.features.chat.presentation.components.ChatInputBar
import app.duinch.agentremote.features.chat.presentation.components.ChatMessageBubble
import app.duinch.agentremote.features.chat.presentation.contract.ChatEffect
import app.duinch.agentremote.features.chat.presentation.contract.ChatIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val isImeVisible = isKeyboardVisible()
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ChatEffect.ScrollToBottom -> {
                    if (state.messages.isNotEmpty()) {
                        listState.animateScrollToItem(state.messages.size - 1)
                    }
                }
                is ChatEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                ChatEffect.TriggerHapticFeedback -> Unit
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF141416),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.activeConversationTitle ?: state.selectedAgent.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                ),
                                color = Color(0xFFF4F4F5),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            ConnectionDot(status = state.connectionStatus)
                        }
                        val subtitle = if (state.activeConversationId != null) {
                            "${state.selectedAgent.displayName} • ${state.selectedModel?.displayName ?: "Mặc định"}"
                        } else {
                            state.selectedModel?.displayName ?: state.selectedAgent.displayName
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp
                            ),
                            color = Color(0xFFA1A1AA),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu chính",
                            tint = Color(0xFFE4E4E7)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Cài đặt",
                            tint = Color(0xFFA1A1AA)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF141416)
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // Approval Card if waiting for permission
                state.pendingApproval?.let { req ->
                    ApprovalCard(
                        request = req,
                        onApprove = { viewModel.processIntent(ChatIntent.ApproveTool(req.callId, true)) },
                        onReject = { viewModel.processIntent(ChatIntent.ApproveTool(req.callId, false)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                ChatInputBar(
                    inputText = state.inputText,
                    onInputChanged = { viewModel.processIntent(ChatIntent.InputChanged(it)) },
                    onSend = { viewModel.processIntent(ChatIntent.SendPrompt) },
                    onCancel = { viewModel.processIntent(ChatIntent.CancelTask) },
                    isBusy = state.agentState.isBusy,
                    canSend = state.canSendPrompt,
                    selectedAgent = state.selectedAgent,
                    selectedModel = state.selectedModel,
                    availableModels = state.availableModels,
                    onModelSelected = { viewModel.processIntent(ChatIntent.SelectModel(it)) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.messages.isEmpty()) {
                val modelName = state.selectedModel?.displayName ?: state.availableModels.firstOrNull()?.displayName ?: "Online"
                ClaudeEmptyState(
                    title = state.activeConversationTitle ?: "Sẵn sàng hỗ trợ bạn",
                    subtitle = if (state.activeConversationId != null) {
                        "Tiếp tục phiên làm việc • ${state.selectedAgent.displayName}"
                    } else {
                        "Chỉ đạo ${state.selectedAgent.displayName} • $modelName"
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = state.messages, key = { it.id }) { message ->
                        ChatMessageBubble(message = message)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClaudeEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Terracotta 8-spoke Claude spark
            Canvas(modifier = Modifier.size(56.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = size.minDimension / 2.2f
                val innerRadius = radius * 0.42f
                val strokeWidth = 3.5.dp.toPx()
                val color = Color(0xFFCC785C)

                for (i in 0 until 8) {
                    val angle = (i * 45f) * (kotlin.math.PI / 180f).toFloat()
                    val cos = kotlin.math.cos(angle)
                    val sin = kotlin.math.sin(angle)
                    drawLine(
                        color = color,
                        start = Offset(center.x + innerRadius * cos, center.y + innerRadius * sin),
                        end = Offset(center.x + radius * cos, center.y + radius * sin),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 20.sp
                ),
                color = Color(0xFFF4F4F5),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = Color(0xFF71717A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConnectionDot(status: ConnectionStatus) {
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

package app.duinch.agentremote.features.chat.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.domain.model.MessageRole
import app.duinch.agentremote.core.presentation.components.ArcReactorEmblem
import app.duinch.agentremote.core.presentation.util.isKeyboardVisible
import app.duinch.agentremote.core.presentation.util.rememberPlatformFilePicker
import app.duinch.agentremote.core.presentation.util.rememberPlatformVoiceInput
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

    val currentModelName = state.selectedModel?.displayName
        ?: state.availableModels.firstOrNull()?.displayName
        ?: state.selectedAgent.defaultModels().firstOrNull()?.displayName
        ?: "Mặc định"

    val launchFilePicker = rememberPlatformFilePicker { fileName, bytes ->
        viewModel.processIntent(ChatIntent.AttachFile(fileName, bytes))
    }
    val launchVoiceInput = rememberPlatformVoiceInput { spokenText ->
        viewModel.processIntent(ChatIntent.AppendVoiceText(spokenText))
    }
    val workspaceLabel = state.activeWorkspace?.substringAfterLast('/')?.takeIf { it.isNotBlank() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF121215),
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
                                color = Color(0xFFF5F4F0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            ConnectionDot(status = state.connectionStatus)
                        }
                        val subLabel = if (workspaceLabel != null) {
                            "${state.selectedAgent.displayName} • $workspaceLabel"
                        } else {
                            "${state.selectedAgent.displayName} • $currentModelName"
                        }
                        Text(
                            text = subLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                            color = Color(0xFF9CA3AF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Outlined.Menu,
                            contentDescription = "Mở danh sách dự án",
                            tint = Color(0xFFE4E4E7)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.processIntent(ChatIntent.NewConversation) }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "Tạo phiên mới",
                            tint = Color(0xFFD4785A)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Cài đặt",
                            tint = Color(0xFFA1A1AA)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121215)
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxWidth()
                ) {
                    state.pendingApproval?.let { req ->
                        ApprovalCard(
                            request = req,
                            onApprove = { viewModel.processIntent(ChatIntent.ApproveTool(req.callId, true)) },
                            onReject = { viewModel.processIntent(ChatIntent.ApproveTool(req.callId, false)) },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
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
                        onModelSelected = { viewModel.processIntent(ChatIntent.SelectModel(it)) },
                        selectedPermission = state.selectedPermission,
                        availablePermissions = state.availablePermissions,
                        onPermissionSelected = { viewModel.processIntent(ChatIntent.SelectPermission(it)) },
                        attachedFiles = state.attachedFiles,
                        isUploadingAttachment = state.isUploadingAttachment,
                        onRemoveAttachment = { viewModel.processIntent(ChatIntent.RemoveAttachment(it)) },
                        availableProjects = state.catalogs[state.selectedAgent]?.projects.orEmpty(),
                        onProjectMentionSelected = { proj ->
                            viewModel.processIntent(ChatIntent.SelectWorkspace(proj.folders.firstOrNull()))
                        },
                        onAttachClick = launchFilePicker,
                        onVoiceClick = launchVoiceInput
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            if (state.messages.isEmpty()) {
                SignatureEmptyHero(
                    title = state.activeConversationTitle ?: "Sẵn sàng điều phối tác vụ",
                    subtitle = "${state.selectedAgent.displayName} • $currentModelName",
                    onSuggestionClick = { suggestion ->
                        viewModel.processIntent(ChatIntent.InputChanged(suggestion))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                @OptIn(ExperimentalFoundationApi::class)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 800.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.messages.forEachIndexed { index, message ->
                        if (message.role == MessageRole.USER) {
                            stickyHeader(key = "user_${message.id}_$index") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF141416))
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    ChatMessageBubble(message = message)
                                }
                            }
                        } else {
                            item(key = "asst_${message.id}_$index") {
                                ChatMessageBubble(message = message)
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp)
            )
        }
    }
}

@Composable
private fun SignatureEmptyHero(
    title: String,
    subtitle: String,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = remember {
        listOf(
            "Kiểm tra git status & các thay đổi gần đây",
            "Chạy bộ unit test và báo cáo kết quả",
            "Phân tích kiến trúc và đề xuất tối ưu"
        )
    }

    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Tony Stark Mark I / III Classic Circular Arc Reactor Emblem
            ArcReactorEmblem(
                modifier = Modifier.size(86.dp),
                animated = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                ),
                color = Color(0xFFF5F4F0),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = Color(0xFF8E8E98),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                suggestions.forEach { text ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1A1A20),
                        border = BorderStroke(1.dp, Color(0xFF2A2A34)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSuggestionClick(text) }
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFFD4D4D8),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionDot(status: ConnectionStatus) {
    val color = when (status) {
        is ConnectionStatus.Connected -> Color(0xFF22C55E)
        ConnectionStatus.Connecting, is ConnectionStatus.Reconnecting -> Color(0xFFF59E0B)
        ConnectionStatus.Disconnected, is ConnectionStatus.Error -> Color(0xFFEF4444)
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, CircleShape)
    )
}

package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.core.domain.model.EngineCatalog
import app.duinch.agentremote.core.domain.model.EngineConversation
import app.duinch.agentremote.core.presentation.components.ArcReactorEmblem

@Composable
fun ConversationDrawerContent(
    selectedAgent: AgentType,
    activeConversationId: String?,
    catalog: EngineCatalog?,
    connectionStatus: ConnectionStatus,
    gatewayUrl: String,
    onSelectConversation: (EngineConversation) -> Unit,
    onNewConversation: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEngineSelector: (() -> Unit)? = null,
    onOpenConnect: (() -> Unit)? = null,
    onCloseDrawer: (() -> Unit)? = null,
    onImportCliSession: ((AgentType, String) -> Unit)? = null,
    onSelectAgentChild: ((AgentType) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var collapsedProjects by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showImportCliDialog by remember { mutableStateOf(false) }

    val projects = catalog?.projects.orEmpty()
    val conversations = catalog?.conversations.orEmpty()
    val projectGroups = remember(projects, conversations, activeConversationId) {
        groupConversationsByProject(projects, conversations, activeConversationId)
    }

    val displayGroups = remember(projectGroups, searchQuery) {
        if (searchQuery.isBlank()) {
            projectGroups
        } else {
            val q = searchQuery.trim().lowercase()
            projectGroups.mapNotNull { group ->
                val nameMatch = group.name.lowercase().contains(q)
                val matchedConvs = group.conversations.filter {
                    it.title.lowercase().contains(q) || it.workspace?.lowercase()?.contains(q) == true
                }
                if (nameMatch || matchedConvs.isNotEmpty()) {
                    group.copy(
                        conversations = if (matchedConvs.isNotEmpty()) matchedConvs else group.conversations
                    )
                } else null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(Color(0xFF141416))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // App header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArcReactorEmblem(
                        modifier = Modifier.size(26.dp),
                        animated = true
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = "Agent Remote",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (connectionStatus.isConnected) Color(0xFF22C55E) else Color(0xFFEF4444)
                            )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefreshCatalog,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Làm mới danh sách",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Cài đặt",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (onCloseDrawer != null) {
                        IconButton(
                            onClick = onCloseDrawer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Đóng menu",
                                tint = Color(0xFFA1A1AA),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = if (connectionStatus.isConnected) "Host: $gatewayUrl" else "Chưa kết nối Gateway",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = Color(0xFF71717A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            // New Conversation & Import CLI Session Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).clickable { onNewConversation() },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E1E22),
                    border = BorderStroke(1.dp, Color(0xFF2E2E34))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = Color(0xFFCC785C), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tạo phiên mới", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp), color = Color(0xFFE5E7EB), maxLines = 1)
                    }
                }
                Surface(
                    modifier = Modifier.clickable { showImportCliDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1B1C22),
                    border = BorderStroke(1.dp, Color(0xFF2E2E34))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Nhập CLI", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = Color(0xFF9CA3AF))
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF212126))

        // Section header: Projects / Filter / Engine
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Projects",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = Color(0xFFF4F4F5)
                )
                catalog?.let { cat ->
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF212126)
                    ) {
                        Text(
                            text = "${cat.totalConversations} phiên",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = Color(0xFFA1A1AA),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterList,
                        contentDescription = "Lọc phiên",
                        tint = if (isSearchActive || searchQuery.isNotBlank()) Color(0xFFCC785C) else Color(0xFF71717A),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Inline search bar if filter is active
        AnimatedVisibility(visible = isSearchActive) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E1E22),
                border = BorderStroke(1.dp, Color(0xFF2E2E34))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = Color(0xFF71717A),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontSize = 12.5.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFFCC785C)),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Lọc theo dự án, phiên...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF52525B),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Xóa lọc",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }
        }

        // Project Grouped Conversation List
        if (displayGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "Không tìm thấy dự án hoặc phiên phù hợp"
                    else if (connectionStatus.isConnected) "Chưa có phiên làm việc nào"
                    else "Kết nối để xem các phiên",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF71717A)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                displayGroups.forEach { group ->
                    val isCollapsed = if (searchQuery.isNotBlank()) false else group.id in collapsedProjects

                    item(key = "proj_${group.id}") {
                        ProjectHeaderRow(
                            name = group.name,
                            conversationCount = group.conversations.size,
                            isCollapsed = isCollapsed,
                            onToggleCollapse = {
                                collapsedProjects = if (isCollapsed) {
                                    collapsedProjects - group.id
                                } else {
                                    collapsedProjects + group.id
                                }
                            }
                        )
                    }

                    if (!isCollapsed) {
                        items(
                            items = group.conversations,
                            key = { conv -> "conv_${group.id}_${conv.id}" }
                        ) { conv ->
                            val isSelected = conv.id == activeConversationId
                            ParentSessionDrawerItem(
                                conversation = conv,
                                isSelected = isSelected,
                                currentWriterAgent = selectedAgent,
                                onSelectSession = { onSelectConversation(conv) },
                                onSelectAgentChild = { agent -> onSelectAgentChild?.invoke(agent) }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF212126))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            DrawerNavigationRow(
                icon = Icons.Outlined.Settings,
                label = "Cài đặt & Cập nhật",
                onClick = onOpenSettings
            )
        }
    }

    if (showImportCliDialog) {
        ImportCliSessionDialog(
            initialAgent = selectedAgent,
            onDismiss = { showImportCliDialog = false },
            onImport = { agent, nativeId ->
                showImportCliDialog = false
                onImportCliSession?.invoke(agent, nativeId)
            }
        )
    }
}

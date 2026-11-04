package app.duinch.agentremote.features.engine.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.EngineCatalog

data class EngineItem(
    val type: AgentType,
    val title: String,
    val subtitle: String,
    val badge: String,
    val accentColor: Color,
    val icon: ImageVector
)

val availableEngines = listOf(
    EngineItem(
        type = AgentType.ANTIGRAVITY,
        title = "Antigravity",
        subtitle = "Tác vụ đa công cụ, tư duy sâu, workspace tương tác và quản lý subagents",
        badge = "Khuyên dùng",
        accentColor = Color(0xFF22C55E),
        icon = Icons.Outlined.AutoAwesome
    ),
    EngineItem(
        type = AgentType.CLAUDE_CODE,
        title = "Claude Code",
        subtitle = "Anthropic CLI agent trực tiếp thao tác code và dòng lệnh máy chủ",
        badge = "Anthropic",
        accentColor = Color(0xFFCC785C),
        icon = Icons.Outlined.Bolt
    ),
    EngineItem(
        type = AgentType.CODEX,
        title = "Codex",
        subtitle = "Lập trình tự động và kiểm thử giải pháp nhanh qua terminal",
        badge = "Console",
        accentColor = Color(0xFF38BDF8),
        icon = Icons.Outlined.Terminal
    ),
    EngineItem(
        type = AgentType.MOCK,
        title = "Mock Agent",
        subtitle = "Giả lập kiểm thử kết nối Gateway an toàn không tiêu hao tài nguyên",
        badge = "Test",
        accentColor = Color(0xFFA1A1AA),
        icon = Icons.Outlined.BugReport
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineSelectorScreen(
    currentAgent: AgentType,
    catalogs: Map<AgentType, EngineCatalog> = emptyMap(),
    onSelectEngine: (AgentType) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF141416),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF141416))) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Agent Remote",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = Color(0xFFF4F4F5),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Chọn Coding Engine",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                                color = Color(0xFFA1A1AA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Cài đặt",
                                tint = Color(0xFFE4E4E7),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF141416))
                )
                HorizontalDivider(color = Color(0xFF22222A), thickness = 0.5.dp)
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141416))
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            val isMultiColumn = maxWidth >= 840.dp
            val contentMaxWidth = if (isMultiColumn) 1040.dp else 580.dp

            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Lựa chọn AI Coding Engine",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = Color(0xFFF4F4F5)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Lựa chọn AI Coding Agent bạn muốn điều khiển trên Host",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = Color(0xFF71717A)
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (isMultiColumn) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        availableEngines.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                pair.forEach { engine ->
                                    val isSelected = engine.type == currentAgent
                                    val engineModels = catalogs[engine.type]?.models.orEmpty()
                                    val modelsText = if (engineModels.isNotEmpty()) {
                                        "Models: " + engineModels.joinToString(", ") { it.displayName }
                                    } else {
                                        "Mô hình: Tự động tải từ máy Mac khi kết nối"
                                    }

                                    EngineCardItem(
                                        engine = engine,
                                        isSelected = isSelected,
                                        modelsText = modelsText,
                                        onSelect = { onSelectEngine(engine.type) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        availableEngines.forEach { engine ->
                            val isSelected = engine.type == currentAgent
                            val engineModels = catalogs[engine.type]?.models.orEmpty()
                            val modelsText = if (engineModels.isNotEmpty()) {
                                "Models: " + engineModels.joinToString(", ") { it.displayName }
                            } else {
                                "Mô hình: Tự động tải từ máy Mac khi kết nối"
                            }

                            EngineCardItem(
                                engine = engine,
                                isSelected = isSelected,
                                modelsText = modelsText,
                                onSelect = { onSelectEngine(engine.type) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun EngineCardItem(
    engine: EngineItem,
    isSelected: Boolean,
    modelsText: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) Color(0xFF1E2024) else Color(0xFF1C1C20),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) engine.accentColor else Color(0xFF2E2E34)
        ),
        modifier = modifier.clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(engine.accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = engine.icon,
                    contentDescription = engine.title,
                    tint = engine.accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = engine.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = Color(0xFFF4F4F5)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = engine.accentColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = engine.badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = engine.accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = engine.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = modelsText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = engine.accentColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = if (isSelected) engine.accentColor else Color(0xFF52525B),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

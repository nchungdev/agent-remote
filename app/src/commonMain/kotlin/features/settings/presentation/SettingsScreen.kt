package app.duinch.agentremote.features.settings.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ConnectionStatus
import app.duinch.agentremote.features.settings.presentation.components.EngineBentoGrid
import app.duinch.agentremote.features.settings.presentation.components.SettingsNavigationRow
import app.duinch.agentremote.features.settings.presentation.components.SettingsSwitchRow
import app.duinch.agentremote.features.settings.presentation.components.UpdateCard
import app.duinch.agentremote.features.settings.presentation.contract.SettingsIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: (() -> Unit)? = null,
    onOpenConnect: (() -> Unit)? = null,
    onOpenEngineSelector: (() -> Unit)? = null,
    onSelectEngine: ((AgentType) -> Unit)? = null,
    currentAgent: AgentType? = null,
    currentHost: String? = null,
    connectionStatus: ConnectionStatus? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val isConnected = connectionStatus?.isConnected == true
    val activeHostLabel = when {
        isConnected && !currentHost.isNullOrBlank() -> "Đang kết nối • $currentHost"
        !currentHost.isNullOrBlank() -> "Đã cấu hình • $currentHost"
        else -> "Chưa cấu hình máy chủ • Nhấn để thiết lập"
    }
    val statusDotColor = when (connectionStatus) {
        is ConnectionStatus.Connected -> Color(0xFF22C55E)
        ConnectionStatus.Connecting, is ConnectionStatus.Reconnecting -> Color(0xFFF59E0B)
        else -> Color(0xFF71717A)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF141416),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cài đặt",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = Color(0xFFF4F4F5)
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại",
                                tint = Color(0xFFE4E4E7)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF141416))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Group 1: Đường truyền & Máy chủ
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("ĐƯỜNG TRUYỀN & MÁY CHỦ")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1E22),
                    border = BorderStroke(1.dp, Color(0xFF2E2E34)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsNavigationRow(
                            icon = Icons.Default.Hub,
                            title = "Quản lý máy chủ kết nối",
                            subtitle = activeHostLabel,
                            statusDotColor = statusDotColor,
                            badgeText = if (isConnected) "ĐÃ NỐI" else null,
                            badgeColor = Color(0xFF22C55E),
                            onClick = { onOpenConnect?.invoke() }
                        )

                        HorizontalDivider(color = Color(0xFF2E2E34), thickness = 0.5.dp)

                        SettingsSwitchRow(
                            icon = Icons.Default.Sync,
                            title = "Tự động kết nối lại",
                            subtitle = "Khôi phục phiên làm việc ngay khi mạng Wi-Fi hoặc P2P ổn định trở lại",
                            checked = state.autoReconnect,
                            onCheckedChange = {
                                viewModel.processIntent(SettingsIntent.ToggleAutoReconnect(it))
                            }
                        )
                    }
                }
            }

            // Group 2: AI Coding Engine
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("CODING ENGINE MẶC ĐỊNH", noPadding = true)
                    if (onOpenEngineSelector != null) {
                        Text(
                            text = "Xem chi tiết →",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFCC785C),
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.clickable { onOpenEngineSelector() }
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1E22),
                    border = BorderStroke(1.dp, Color(0xFF2E2E34)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        EngineBentoGrid(
                            selectedAgent = currentAgent ?: state.defaultAgent,
                            onSelectAgent = { agent ->
                                viewModel.processIntent(SettingsIntent.SelectDefaultAgent(agent))
                                onSelectEngine?.invoke(agent)
                            }
                        )
                    }
                }
            }

            // Group 3: Thông báo & Phê duyệt
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("THÔNG BÁO & PHÊ DUYỆT")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1E22),
                    border = BorderStroke(1.dp, Color(0xFF2E2E34)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsSwitchRow(
                            icon = Icons.Default.VerifiedUser,
                            title = "Cảnh báo duyệt quyền thực thi",
                            subtitle = "Hiển thị thông báo tức thì khi Agent chờ phê duyệt chạy lệnh hoặc sửa tệp",
                            checked = state.notifyAgentApproval,
                            onCheckedChange = {
                                viewModel.processIntent(SettingsIntent.ToggleNotifyAgentApproval(it))
                            }
                        )

                        HorizontalDivider(color = Color(0xFF2E2E34), thickness = 0.5.dp)

                        SettingsSwitchRow(
                            icon = Icons.Default.NotificationsActive,
                            title = "Thông báo trạng thái kết nối",
                            subtitle = "Báo khi máy chủ kết nối thành công hoặc bị ngắt đường truyền đột ngột",
                            checked = state.notifyConnectionChange,
                            onCheckedChange = {
                                viewModel.processIntent(SettingsIntent.ToggleNotifyConnectionChange(it))
                            }
                        )
                    }
                }
            }

            // Group 4: Thông tin & Cập nhật ứng dụng
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("THÔNG TIN & CẬP NHẬT")
                UpdateCard(
                    currentVersion = state.currentVersionName,
                    currentCode = state.currentVersionCode,
                    repoName = "${state.githubRepoOwner}/${state.githubRepoName}",
                    status = state.updateStatus,
                    onCheckUpdate = { viewModel.processIntent(SettingsIntent.CheckUpdate) },
                    onDownloadAndInstall = { viewModel.processIntent(SettingsIntent.DownloadAndInstallUpdate) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    noPadding: Boolean = false
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            letterSpacing = 0.6.sp
        ),
        color = Color(0xFFCC785C),
        modifier = if (noPadding) Modifier else Modifier.padding(start = 4.dp)
    )
}

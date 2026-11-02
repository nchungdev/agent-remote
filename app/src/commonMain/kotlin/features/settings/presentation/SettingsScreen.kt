package app.duinch.agentremote.features.settings.presentation

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.ConnectionStatus
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF141416),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cài đặt & Cập nhật",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: Server & Network
            SectionTitle("MÁY CHỦ & ĐƯỜNG TRUYỀN (HOST GATEWAY)")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E22),
                border = BorderStroke(1.dp, Color(0xFF2E2E34)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Địa chỉ Gateway Host:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            softWrap = false
                        )
                        if (connectionStatus != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            if (connectionStatus.isConnected) Color(0xFF22C55E) else Color(0xFFEF4444),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (connectionStatus.isConnected) "Đã kết nối" else "Mất kết nối",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = if (connectionStatus.isConnected) Color(0xFF22C55E) else Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = state.gatewayHost,
                        onValueChange = { viewModel.processIntent(SettingsIntent.UpdateGatewayHost(it)) },
                        singleLine = true,
                        colors = inputColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.processIntent(SettingsIntent.TestPing) },
                            enabled = !state.isPinging,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF26262B),
                                contentColor = Color(0xFFE4E4E7),
                                disabledContainerColor = Color(0xFF1E1E22)
                            )
                        ) {
                            Text(
                                if (state.isPinging) "Đang ping..." else "Kiểm tra Ping",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        if (onOpenConnect != null) {
                            Button(
                                onClick = onOpenConnect,
                                modifier = Modifier.height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFCC785C),
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    "Đổi Host / QR →",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        state.pingMs?.let { latency ->
                            Text(
                                text = "${latency}ms",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (latency < 100) Color(0xFF22C55E) else Color(0xFFF59E0B),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Section 2: Coding Engine Selection
            SectionTitle("CODING ENGINE")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E22),
                border = BorderStroke(1.dp, Color(0xFF2E2E34)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Chọn Engine điều khiển:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            softWrap = false
                        )
                        if (onOpenEngineSelector != null) {
                            Text(
                                text = "Xem chi tiết →",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFCC785C),
                                    fontSize = 11.5.sp
                                ),
                                modifier = Modifier.clickable { onOpenEngineSelector() }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AgentType.entries.forEach { agent ->
                            val isSelected = agent == (currentAgent ?: state.defaultAgent)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.processIntent(SettingsIntent.SelectDefaultAgent(agent))
                                    onSelectEngine?.invoke(agent)
                                },
                                modifier = Modifier.height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                label = {
                                    Text(
                                        agent.displayName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFCC785C),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF26262B),
                                    labelColor = Color(0xFFCBD5E1)
                                )
                            )
                        }
                    }
                }
            }

            // Section 3: App Update (GitHub Releases OTA)
            SectionTitle("BẢN CẬP NHẬT")
            UpdateCard(
                currentVersion = state.currentVersionName,
                currentCode = state.currentVersionCode,
                repoName = "${state.githubRepoOwner}/${state.githubRepoName}",
                status = state.updateStatus,
                onCheckUpdate = { viewModel.processIntent(SettingsIntent.CheckUpdate) },
                onDownloadAndInstall = { viewModel.processIntent(SettingsIntent.DownloadAndInstallUpdate) }
            )

            // Section 4: System Info
            SectionTitle("THÔNG TIN HỆ THỐNG")
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E22),
                border = BorderStroke(1.dp, Color(0xFF2E2E34)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoRow("Mô hình", "Clean Arch + MVI UDF")
                    InfoRow("Nền tảng", "Compose Multiplatform / KMP")
                    InfoRow("Giao thức", "P2P WebRTC / WebSocket")
                    InfoRow("Tài khoản", "Subscription (0 API Key)")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp
        ),
        color = Color(0xFF94A3B8),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = Color(0xFF94A3B8),
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            color = Color(0xFFF4F4F5),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun inputColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFFCC785C),
    unfocusedBorderColor = Color(0xFF38383F),
    focusedTextColor = Color(0xFFF4F4F5),
    unfocusedTextColor = Color(0xFFF4F4F5),
    focusedContainerColor = Color(0xFF141416),
    unfocusedContainerColor = Color(0xFF141416),
    cursorColor = Color(0xFFCC785C)
)

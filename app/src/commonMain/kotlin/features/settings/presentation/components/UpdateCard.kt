package app.duinch.agentremote.features.settings.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.features.updater.domain.model.UpdateStatus

@Composable
fun UpdateCard(
    currentVersion: String,
    currentCode: Int,
    repoName: String,
    status: UpdateStatus,
    onCheckUpdate: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155)),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GitHub Release OTA",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "Kho: $repoName",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Text(
                        text = "v$currentVersion (code $currentCode)",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        color = Color(0xFF22C55E),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (status) {
                UpdateStatus.Idle -> {
                    Text(
                        text = "Chưa kiểm tra cập nhật gần đây.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                UpdateStatus.Checking -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFF22C55E))
                        Text(
                            text = "Đang kiểm tra bản phát hành trên GitHub...",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                is UpdateStatus.UpToDate -> {
                    Text(
                        text = "✓ Đang sử dụng phiên bản mới nhất (${status.currentVersion})",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFF22C55E),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                is UpdateStatus.Available -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Có bản cập nhật mới: ${status.release.tagName}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFFF59E0B),
                                maxLines = 1,
                                softWrap = false
                            )
                            if (status.release.releaseNotes.isNotBlank()) {
                                Text(
                                    text = status.release.releaseNotes,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 3,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                is UpdateStatus.Downloading -> {
                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                        Row {
                            Text("Đang tải APK...", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = Color(0xFF94A3B8), maxLines = 1, softWrap = false)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${(status.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = Color(0xFF22C55E),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            color = Color(0xFF22C55E),
                            trackColor = Color(0xFF334155)
                        )
                    }
                }

                is UpdateStatus.ReadyToInstall -> {
                    Text(
                        text = "✓ Tệp APK đã tải xong! Bấm cài đặt để cập nhật.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFF22C55E),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                is UpdateStatus.Error -> {
                    Text(
                        text = "⚠️ ${status.message}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = Color(0xFFEF4444),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCheckUpdate,
                    enabled = !status.isChecking && !status.isDownloading,
                    modifier = Modifier.weight(1f).height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Text("Kiểm tra bản mới", style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                }

                if (status is UpdateStatus.Available || status is UpdateStatus.ReadyToInstall) {
                    Button(
                        onClick = onDownloadAndInstall,
                        enabled = !status.isDownloading,
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                    ) {
                        Text(
                            text = if (status is UpdateStatus.ReadyToInstall) "Cài đặt ngay" else "Tải & Cài APK",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

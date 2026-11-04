package app.duinch.agentremote.features.settings.presentation.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val isHighlight = status is UpdateStatus.Available || status is UpdateStatus.ReadyToInstall
    val borderColor = when {
        isHighlight -> Color(0xFF22C55E)
        status is UpdateStatus.Error -> Color(0xFFEF4444).copy(alpha = 0.6f)
        else -> Color(0xFF2E2E34)
    }

    val targetProgress = (status as? UpdateStatus.Downloading)?.progress ?: 0f
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing),
        label = "otaProgress"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E22),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isHighlight || status is UpdateStatus.UpToDate) {
                                Color(0xFF22C55E).copy(alpha = 0.14f)
                            } else {
                                Color(0xFFCC785C).copy(alpha = 0.14f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (status is UpdateStatus.UpToDate) {
                            Icons.Outlined.CheckCircle
                        } else {
                            Icons.Outlined.SystemUpdateAlt
                        },
                        contentDescription = null,
                        tint = if (isHighlight || status is UpdateStatus.UpToDate) {
                            Color(0xFF22C55E)
                        } else {
                            Color(0xFFCC785C)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Agent Remote",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp
                            ),
                            color = Color(0xFFF4F4F5),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF141416),
                            border = BorderStroke(1.dp, Color(0xFF2E2E34))
                        ) {
                            Text(
                                text = "v$currentVersion (Build $currentCode)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFFA1A1AA),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    when (status) {
                        UpdateStatus.Idle -> {
                            Text(
                                text = "Cập nhật trực tiếp qua GitHub Releases",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        UpdateStatus.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFCC785C)
                                )
                                Text(
                                    text = "Đang kiểm tra phiên bản mới...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        is UpdateStatus.UpToDate -> {
                            Text(
                                text = "Đã là phiên bản mới nhất (${status.currentVersion})",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFF22C55E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        is UpdateStatus.Available -> {
                            Text(
                                text = "Có bản cập nhật mới: ${status.release.tagName}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF22C55E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        is UpdateStatus.Downloading -> {
                            Text(
                                text = "Đang tải gói cài đặt... ${(status.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFFCC785C),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        is UpdateStatus.ReadyToInstall -> {
                            Text(
                                text = "Đã tải xong! Nhấn Cài đặt ngay để cập nhật.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFF22C55E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        is UpdateStatus.Error -> {
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFFEF4444),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (status is UpdateStatus.Available && status.release.releaseNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF141416),
                    border = BorderStroke(1.dp, Color(0xFF2E2E34)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = status.release.releaseNotes,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                        color = Color(0xFFCBD5E1),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (status is UpdateStatus.Downloading) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFFCC785C),
                    trackColor = Color(0xFF26262B),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onCheckUpdate,
                    enabled = !status.isChecking && !status.isDownloading,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF4F4F5)),
                    border = BorderStroke(1.dp, Color(0xFF3F3F46))
                ) {
                    Text(
                        text = "Kiểm tra bản mới",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (status is UpdateStatus.Available || status is UpdateStatus.ReadyToInstall) {
                    Button(
                        onClick = onDownloadAndInstall,
                        enabled = !status.isDownloading,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E),
                            contentColor = Color(0xFF0A0A0C)
                        )
                    ) {
                        Text(
                            text = if (status is UpdateStatus.ReadyToInstall) "Cài đặt ngay" else "Tải & Cài APK",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

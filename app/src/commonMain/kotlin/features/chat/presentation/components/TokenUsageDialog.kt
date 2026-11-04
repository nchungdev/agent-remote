package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.duinch.agentremote.core.domain.model.ConversationTokenUsage

@Composable
internal fun CompactTokenGaugeBadge(
    tokenUsage: ConversationTokenUsage?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usage = tokenUsage ?: ConversationTokenUsage()
    val ctxUsedFraction = usage.usagePercentage
    val fiveHourUsedFraction = ((100 - usage.fiveHourLimitPercent) / 100f).coerceIn(0f, 1f)
    val weeklyUsedFraction = ((100 - usage.weeklyLimitPercent) / 100f).coerceIn(0f, 1f)

    val ctxColor = when {
        ctxUsedFraction >= 0.90f -> Color(0xFFE05252)
        ctxUsedFraction >= 0.70f -> Color(0xFFE5A93C)
        else -> Color(0xFF4EBA74)
    }
    val fiveHourColor = when {
        fiveHourUsedFraction >= 0.90f -> Color(0xFFE05252)
        fiveHourUsedFraction >= 0.70f -> Color(0xFFE5A93C)
        else -> Color(0xFF4EBA74)
    }
    val weeklyColor = when {
        weeklyUsedFraction >= 0.90f -> Color(0xFFE05252)
        weeklyUsedFraction >= 0.70f -> Color(0xFFE5A93C)
        else -> Color(0xFF4EBA74)
    }

    Surface(
        shape = RoundedCornerShape(7.dp),
        color = Color(0xFF1E1F24),
        border = BorderStroke(0.6.dp, Color(0xFF2C2D35)),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cột 1: window context used/total
            VerticalUsageBar(fraction = ctxUsedFraction, color = ctxColor)
            // Cột 2: usage 5-hours used/total
            VerticalUsageBar(fraction = fiveHourUsedFraction, color = fiveHourColor)
            // Cột 3: usage weekly used/total
            VerticalUsageBar(fraction = weeklyUsedFraction, color = weeklyColor)
        }
    }
}

@Composable
private fun VerticalUsageBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(4.5.dp)
            .height(14.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(Color(0xFF2B2D36)),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fraction.coerceIn(0.10f, 1.0f))
                .clip(RoundedCornerShape(1.5.dp))
                .background(color)
        )
    }
}

@Composable
internal fun TokenUsageDetailDialog(
    modelName: String,
    tokenUsage: ConversationTokenUsage,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF18191E),
            border = BorderStroke(1.dp, Color(0xFF2E2F38)),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Usage",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = Color(0xFFF3F4F6)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Đóng",
                            tint = Color(0xFF8A8D96),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Section 1: Gemini Models
                UsageQuotaGroup(
                    title = "Gemini Models",
                    rows = listOf(
                        QuotaRowData("Weekly limit", tokenUsage.weeklyLimitPercent, tokenUsage.weeklyResetTime),
                        QuotaRowData("5h limit", tokenUsage.fiveHourLimitPercent, tokenUsage.fiveHourResetTime)
                    )
                )

                HorizontalDivider(color = Color(0xFF26272F), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

                // Section 2: Claude and GPT models
                UsageQuotaGroup(
                    title = "Claude and GPT models",
                    rows = listOf(
                        QuotaRowData("Weekly limit", 0, "13m"),
                        QuotaRowData("5h limit", 100, "4h 59m")
                    )
                )

                HorizontalDivider(color = Color(0xFF26272F), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

                // Section 3: Gemini 4 Argon
                UsageQuotaGroup(
                    title = "Gemini 4 Argon",
                    rows = listOf(
                        QuotaRowData("Weekly limit", 1, "5d 12h")
                    )
                )

                HorizontalDivider(color = Color(0xFF26272F), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 10.dp))

                // Section 4: Current Conversation Context
                Text(
                    text = "Ngữ cảnh hội thoại ($modelName)",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = Color(0xFFF3F4F6),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                val pct = tokenUsage.usagePercentage
                val pctDisplay = ((pct * 1000).toInt() / 10.0).toString()
                val gaugeColor = when {
                    pct >= 0.90f -> Color(0xFFE05252)
                    pct >= 0.75f -> Color(0xFFE5A93C)
                    else -> Color(0xFF4EBA74)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "${tokenUsage.formattedTotal()} / ${tokenUsage.formattedLimit()}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp
                        ),
                        color = Color(0xFFD4D6DC)
                    )
                    Text(
                        text = "$pctDisplay% ngữ cảnh",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp
                        ),
                        color = gaugeColor
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = gaugeColor,
                    trackColor = Color(0xFF282932)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniMetricCard(label = "Prompt", value = tokenUsage.formattedInput(), modifier = Modifier.weight(1f))
                    MiniMetricCard(label = "Cache Read", value = tokenUsage.formattedCache(), badge = "⚡ Cache", modifier = Modifier.weight(1f))
                    MiniMetricCard(label = "Output", value = tokenUsage.formattedOutput(), modifier = Modifier.weight(1f))
                    MiniMetricCard(label = "Còn lại", value = tokenUsage.formattedRemaining(), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class QuotaRowData(
    val label: String,
    val percentLeft: Int,
    val resetTime: String
)

@Composable
private fun UsageQuotaGroup(
    title: String,
    rows: List<QuotaRowData>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            ),
            color = Color(0xFFF3F4F6),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        rows.forEach { item ->
            val gaugeColor = when {
                item.percentLeft <= 10 -> Color(0xFFE05252)
                item.percentLeft <= 30 -> Color(0xFFE5A93C)
                else -> Color(0xFF4EBA74)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                    color = Color(0xFF8A8D96)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${item.percentLeft}% left",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            ),
                            color = Color(0xFFF3F4F6)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Canvas(modifier = Modifier.size(12.dp)) {
                            val strokeWidth = 2.dp.toPx()
                            drawCircle(color = Color(0xFF33353E), style = Stroke(strokeWidth))
                            if (item.percentLeft > 0) {
                                drawArc(
                                    color = gaugeColor,
                                    startAngle = -90f,
                                    sweepAngle = (item.percentLeft / 100f * 360f),
                                    useCenter = false,
                                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Resets in ${item.resetTime}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = Color(0xFF71717A)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMetricCard(
    label: String,
    value: String,
    badge: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF202128),
        border = BorderStroke(0.5.dp, Color(0xFF2C2D35)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            Text(
                text = badge ?: label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    color = if (badge != null) Color(0xFF4EBA74) else Color(0xFF8A8D96)
                ),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = Color(0xFFF3F4F6),
                maxLines = 1
            )
        }
    }
}

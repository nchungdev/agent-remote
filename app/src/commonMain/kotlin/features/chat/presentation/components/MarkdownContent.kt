package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface ContentSegment {
    data class Text(val text: String) : ContentSegment
    data class Code(val language: String, val code: String) : ContentSegment
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val segments = remember(content) { parseMarkdownSegments(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        segments.forEach { segment ->
            when (segment) {
                is ContentSegment.Text -> {
                    if (segment.text.isNotBlank()) {
                        RichTextParagraphs(text = segment.text)
                    }
                }
                is ContentSegment.Code -> {
                    CodeBlockCard(language = segment.language, code = segment.code)
                }
            }
        }
    }
}

@Composable
private fun RichTextParagraphs(text: String) {
    val lines = remember(text) { text.lines() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { rawLine ->
            val trimmed = rawLine.trimStart()
            when {
                trimmed.isEmpty() -> Spacer(modifier = Modifier.height(2.dp))
                trimmed.startsWith("### ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("### ")),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 22.sp
                        ),
                        color = Color(0xFFF5F4F0),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("## ")),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 24.sp
                        ),
                        color = Color(0xFFF5F4F0),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("# ")),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp
                        ),
                        color = Color(0xFFF5F4F0),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.5.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFD4785A),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = formatInlineMarkdown(trimmed.substring(2)),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.5.sp,
                                lineHeight = 24.sp,
                                letterSpacing = 0.15.sp
                            ),
                            color = Color(0xFFECEBE6)
                        )
                    }
                }
                else -> {
                    Text(
                        text = formatInlineMarkdown(rawLine),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.5.sp,
                            lineHeight = 24.sp,
                            letterSpacing = 0.15.sp
                        ),
                        color = Color(0xFFECEBE6)
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(language: String, code: String) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val codeLines = remember(code) { code.lines() }
    val lineCount = codeLines.size
    val lineNumbersText = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0E0E12),
        border = BorderStroke(1.dp, Color(0xFF282832))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Code block top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF17171E))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = Color(0xFF252530)
                    ) {
                        Text(
                            text = language.ifBlank { "code" }.lowercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            ),
                            color = Color(0xFFD4785A),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$lineCount dòng",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF71717A)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isCopied) Color(0xFF142E1F) else Color(0xFF23232C),
                    border = BorderStroke(
                        0.5.dp,
                        if (isCopied) Color(0xFF22C55E).copy(alpha = 0.5f) else Color(0xFF343440)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(code))
                            isCopied = true
                            coroutineScope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Sao chép mã nguồn",
                            tint = if (isCopied) Color(0xFF22C55E) else Color(0xFFA1A1AA),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isCopied) "Đã chép" else "Sao chép",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (isCopied) Color(0xFF22C55E) else Color(0xFFD4D4D8)
                        )
                    }
                }
            }

            // Code body with line numbers gutter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // Line numbers column
                Text(
                    text = lineNumbersText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.End
                    ),
                    color = Color(0xFF52525B),
                    modifier = Modifier
                        .background(Color(0xFF121217))
                        .padding(horizontal = 10.dp, vertical = 12.dp)
                )

                // Gutter divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF22222A))
                )

                // Horizontally scrollable code
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp
                        ),
                        color = Color(0xFFE4E4E7)
                    )
                }
            }
        }
    }
}

private fun parseMarkdownSegments(content: String): List<ContentSegment> {
    val segments = mutableListOf<ContentSegment>()
    val lines = content.lines()
    var inCodeBlock = false
    var currentLang = ""
    val buffer = StringBuilder()

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                segments.add(ContentSegment.Code(currentLang, buffer.toString().trimEnd('\n')))
                buffer.clear()
                inCodeBlock = false
                currentLang = ""
            } else {
                if (buffer.isNotEmpty()) {
                    segments.add(ContentSegment.Text(buffer.toString().trimEnd('\n')))
                    buffer.clear()
                }
                currentLang = trimmed.removePrefix("```").trim()
                inCodeBlock = true
            }
        } else {
            buffer.append(line).append('\n')
        }
    }

    if (buffer.isNotEmpty()) {
        if (inCodeBlock) {
            segments.add(ContentSegment.Code(currentLang, buffer.toString().trimEnd('\n')))
        } else {
            segments.add(ContentSegment.Text(buffer.toString().trimEnd('\n')))
        }
    }

    return segments
}

private fun formatInlineMarkdown(raw: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("""(`[^`]+`|\*\*[^*]+\*\*)""")
        val matches = regex.findAll(raw)

        for (match in matches) {
            if (match.range.first > cursor) {
                append(raw.substring(cursor, match.range.first))
            }
            val token = match.value
            when {
                token.startsWith("`") && token.endsWith("`") -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp,
                            background = Color(0xFF25252E),
                            color = Color(0xFFEDA68A)
                        )
                    )
                    append(" ${token.removeSurrounding("`")} ")
                    pop()
                }
                token.startsWith("**") && token.endsWith("**") -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFF8F8F5)))
                    append(token.removeSurrounding("**"))
                    pop()
                }
                else -> append(token)
            }
            cursor = match.range.last + 1
        }

        if (cursor < raw.length) {
            append(raw.substring(cursor))
        }
    }
}

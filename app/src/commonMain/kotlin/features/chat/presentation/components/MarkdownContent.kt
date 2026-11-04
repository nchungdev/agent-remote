package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface ContentSegment {
    data class Text(val text: String) : ContentSegment
    data class Code(val language: String, val code: String) : ContentSegment
    data class Thinking(val thought: String) : ContentSegment
    data class ToolTrace(val tools: List<String>) : ContentSegment
}

@Composable
fun MarkdownContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val segments = remember(content) { parseMarkdownSegments(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                is ContentSegment.Thinking -> {
                    ThinkingCard(thought = segment.thought)
                }
                is ContentSegment.ToolTrace -> {
                    ToolTraceCard(items = segment.tools)
                }
            }
        }
    }
}

@Composable
private fun RichTextParagraphs(text: String) {
    val lines = remember(text) { text.lines() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { rawLine ->
            val trimmed = rawLine.trimStart()
            when {
                trimmed.isEmpty() -> Spacer(modifier = Modifier.height(2.dp))
                trimmed.startsWith("### ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("### ")),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 20.sp
                        ),
                        color = Color(0xFFF5F4F0),
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("## ")),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 21.sp
                        ),
                        color = Color(0xFFF5F4F0),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = formatInlineMarkdown(trimmed.removePrefix("# ")),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 23.sp
                        ),
                        color = Color(0xFFF5F4F0),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFFD4785A),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = formatInlineMarkdown(trimmed.substring(2)),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                                letterSpacing = 0.1.sp
                            ),
                            color = Color(0xFFECEBE6)
                        )
                    }
                }
                else -> {
                    Text(
                        text = formatInlineMarkdown(rawLine),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            lineHeight = 20.sp,
                            letterSpacing = 0.1.sp
                        ),
                        color = Color(0xFFECEBE6)
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
    val textBuffer = StringBuilder()

    for (line in lines) {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                segments.add(ContentSegment.Code(currentLang, textBuffer.toString().trimEnd('\n')))
                textBuffer.clear()
                inCodeBlock = false
                currentLang = ""
            } else {
                if (textBuffer.isNotEmpty()) {
                    segments.addAll(parseSpecialSegments(textBuffer.toString().trimEnd('\n')))
                    textBuffer.clear()
                }
                currentLang = trimmed.removePrefix("```").trim()
                inCodeBlock = true
            }
        } else {
            textBuffer.append(line).append('\n')
        }
    }

    if (textBuffer.isNotEmpty()) {
        if (inCodeBlock) {
            segments.add(ContentSegment.Code(currentLang, textBuffer.toString().trimEnd('\n')))
        } else {
            segments.addAll(parseSpecialSegments(textBuffer.toString().trimEnd('\n')))
        }
    }

    return segments
}

private fun parseSpecialSegments(text: String): List<ContentSegment> {
    val result = mutableListOf<ContentSegment>()
    val thinkRegex = Regex("""(?s)<think>(.*?)</think>""")
    var cursor = 0
    val matches = thinkRegex.findAll(text).toList()

    if (matches.isNotEmpty()) {
        for (m in matches) {
            val before = text.substring(cursor, m.range.first).trim()
            if (before.isNotEmpty()) {
                result.addAll(parseToolTraceSegment(before))
            }
            val thought = m.groupValues[1].trim()
            if (thought.isNotEmpty()) {
                result.add(ContentSegment.Thinking(thought))
            }
            cursor = m.range.last + 1
        }
        val after = text.substring(cursor).trim()
        if (after.isNotEmpty()) {
            result.addAll(parseToolTraceSegment(after))
        }
    } else {
        result.addAll(parseToolTraceSegment(text))
    }
    return result
}

private fun parseToolTraceSegment(text: String): List<ContentSegment> {
    if (!text.contains("🔧 *Thực thi công cụ:*")) {
        return listOf(ContentSegment.Text(text))
    }
    val result = mutableListOf<ContentSegment>()
    val lines = text.lines()
    val textBuffer = StringBuilder()
    val toolItems = mutableListOf<String>()
    var inToolTrace = false

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed == "🔧 *Thực thi công cụ:*") {
            if (textBuffer.isNotBlank()) {
                result.add(ContentSegment.Text(textBuffer.toString().trimEnd()))
                textBuffer.clear()
            }
            inToolTrace = true
        } else if (inToolTrace) {
            if (trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*")) {
                toolItems.add(trimmed)
            } else if (trimmed.isEmpty()) {
                // keep reading tool items
            } else {
                if (toolItems.isNotEmpty()) {
                    result.add(ContentSegment.ToolTrace(toolItems.toList()))
                    toolItems.clear()
                }
                inToolTrace = false
                textBuffer.append(line).append('\n')
            }
        } else {
            textBuffer.append(line).append('\n')
        }
    }

    if (inToolTrace && toolItems.isNotEmpty()) {
        result.add(ContentSegment.ToolTrace(toolItems.toList()))
    }
    if (textBuffer.isNotBlank()) {
        result.add(ContentSegment.Text(textBuffer.toString().trimEnd()))
    }
    return result
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
                            fontSize = 12.sp,
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

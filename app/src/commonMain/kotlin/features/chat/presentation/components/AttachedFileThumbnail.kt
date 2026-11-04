package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.features.chat.presentation.contract.AttachedFileItem

@Composable
internal fun AttachedFilesPreviewStrip(
    attachedFiles: List<AttachedFileItem>,
    isUploadingAttachment: Boolean,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachedFiles.isEmpty() && !isUploadingAttachment) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        attachedFiles.forEach { item ->
            AttachedThumbnailCard(
                item = item,
                onRemove = { onRemoveAttachment(item.remotePath.ifBlank { item.fileName }) }
            )
        }

        if (isUploadingAttachment) {
            UploadingThumbnailCard()
        }
    }
}

@Composable
private fun AttachedThumbnailCard(
    item: AttachedFileItem,
    onRemove: () -> Unit
) {
    val ext = item.fileName.substringAfterLast('.', "").uppercase().take(4)
    val isImage = ext in listOf("PNG", "JPG", "JPEG", "WEBP", "GIF", "SVG")
    val isCode = ext in listOf("KT", "TS", "JS", "PY", "RS", "GO", "JSON", "MD", "SH")

    Box(
        modifier = Modifier.size(56.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1E2026),
            border = BorderStroke(1.dp, Color(0xFF333540))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        isImage -> Icons.Outlined.Image
                        isCode -> Icons.Outlined.Code
                        else -> Icons.Outlined.Description
                    },
                    contentDescription = null,
                    tint = if (isImage) Color(0xFFCC785C) else Color(0xFF9EA0A8),
                    modifier = Modifier.size(20.dp)
                )

                if (ext.isNotEmpty()) {
                    Text(
                        text = ext,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = Color(0xFF8E909A),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Circular Close button at top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C2E38))
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Gỡ tệp",
                tint = Color(0xFFD4D4D8),
                modifier = Modifier.size(10.dp)
            )
        }
    }
}

@Composable
private fun UploadingThumbnailCard() {
    Surface(
        modifier = Modifier.size(56.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1E2026),
        border = BorderStroke(1.dp, Color(0xFF333540))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color(0xFFCC785C)
            )
        }
    }
}

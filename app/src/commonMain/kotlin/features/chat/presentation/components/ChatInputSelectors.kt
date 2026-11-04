package app.duinch.agentremote.features.chat.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duinch.agentremote.core.domain.model.AgentModel
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.PermissionMode

@Composable
internal fun ModelSelectorDropdown(
    selectedAgent: AgentType,
    activeModel: AgentModel?,
    modelsToDisplay: List<AgentModel>,
    chipFontSize: TextUnit,
    onModelSelected: (AgentModel) -> Unit,
    onViewUsage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showModelMenu by remember { mutableStateOf(false) }
    val rawName = activeModel?.displayName ?: "Model"
    val (baseName, variantName) = splitModelName(rawName)

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (showModelMenu) Color(0xFF23242A) else Color.Transparent)
                .clickable { showModelMenu = true }
                .padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = baseName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = chipFontSize
                ),
                color = Color(0xFFC8CAD0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (variantName != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = variantName,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = chipFontSize
                    ),
                    color = Color(0xFF7D7F87),
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = if (showModelMenu) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                contentDescription = "Chọn Model",
                tint = Color(0xFF7D7F87),
                modifier = Modifier.size(13.dp)
            )
        }

        DropdownMenu(
            expanded = showModelMenu,
            onDismissRequest = { showModelMenu = false },
            modifier = Modifier
                .background(Color(0xFF18191E))
                .border(1.dp, Color(0xFF2C2D35), RoundedCornerShape(12.dp))
                .widthIn(min = 240.dp, max = 300.dp)
        ) {
            Text(
                text = "MODEL • ${selectedAgent.displayName.uppercase()}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.7.sp
                ),
                color = Color(0xFF8A8D96),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
            HorizontalDivider(color = Color(0xFF26272F), thickness = 0.5.dp)

            modelsToDisplay.forEach { model ->
                val isSelected = model.id == activeModel?.id
                ModelMenuItem(
                    model = model,
                    isSelected = isSelected,
                    onClick = {
                        onModelSelected(model)
                        showModelMenu = false
                    }
                )
            }

            HorizontalDivider(color = Color(0xFF26272F), thickness = 0.5.dp)
            ViewUsageMenuItem(
                onClick = {
                    showModelMenu = false
                    onViewUsage()
                }
            )
        }
    }
}

@Composable
internal fun PermissionSelectorDropdown(
    activePermission: PermissionMode,
    permissionsToDisplay: List<PermissionMode>,
    chipFontSize: TextUnit,
    isCompact: Boolean,
    onPermissionSelected: (PermissionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPermissionMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        if (isCompact) {
            Box(
                modifier = Modifier
                    .requiredSize(28.dp)
                    .clip(CircleShape)
                    .background(if (showPermissionMenu) Color(0xFF23242A) else Color.Transparent, CircleShape)
                    .clickable { showPermissionMenu = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = permissionOutlinedVector(activePermission.iconType),
                    contentDescription = activePermission.label,
                    tint = Color(0xFF9EA0A8),
                    modifier = Modifier.size(15.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (showPermissionMenu) Color(0xFF23242A) else Color.Transparent)
                    .clickable { showPermissionMenu = true }
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = permissionOutlinedVector(activePermission.iconType),
                    contentDescription = null,
                    tint = Color(0xFF9EA0A8),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = activePermission.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = chipFontSize
                    ),
                    color = Color(0xFFC8CAD0),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(3.dp))
                Icon(
                    imageVector = if (showPermissionMenu) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Chế độ quyền",
                    tint = Color(0xFF7D7F87),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showPermissionMenu,
            onDismissRequest = { showPermissionMenu = false },
            modifier = Modifier
                .background(Color(0xFF18191E))
                .border(1.dp, Color(0xFF2C2D35), RoundedCornerShape(12.dp))
                .widthIn(min = 315.dp, max = 355.dp)
        ) {
            permissionsToDisplay.forEach { mode ->
                val isSelected = mode.id == activePermission.id
                PermissionMenuItem(
                    mode = mode,
                    isSelected = isSelected,
                    onClick = {
                        onPermissionSelected(mode)
                        showPermissionMenu = false
                    }
                )
            }
        }
    }
}

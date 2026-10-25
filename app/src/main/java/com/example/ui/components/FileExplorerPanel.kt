package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FileNode
import com.example.ui.theme.IdeAccent
import com.example.ui.theme.IdeBorder
import com.example.ui.theme.IdePrimary
import com.example.ui.theme.IdeSidebar
import com.example.ui.theme.IdeSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun FileExplorerPanel(
    projectName: String,
    fileTree: List<FileNode>,
    activeFilePath: String,
    onFileSelected: (String) -> Unit,
    onToggleInclusion: (String) -> Unit,
    onImportZipClick: () -> Unit,
    onImportGitHubClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(IdeSidebar)
                .testTag("file_explorer_panel")
        ) {
            // Explorer Header (matched to 38dp tab strip height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(IdeSurface)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = projectName.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onImportGitHubClick,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("import_github_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Import GitHub Repo",
                            tint = IdeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onImportZipClick,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("import_zip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Import ZIP",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(IdeBorder)
            )

            // File Tree List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                items(fileTree, key = { it.id }) { node ->
                    FileTreeNodeItem(
                        node = node,
                        depth = 0,
                        activeFilePath = activeFilePath,
                        onFileSelected = onFileSelected,
                        onToggleInclusion = onToggleInclusion
                    )
                }
            }
        }

        // Right vertical divider separating sidebar from editor
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(IdeBorder)
        )
    }
}

@Composable
private fun FileTreeNodeItem(
    node: FileNode,
    depth: Int,
    activeFilePath: String,
    onFileSelected: (String) -> Unit,
    onToggleInclusion: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    val isSelected = !node.isDirectory && node.relativePath == activeFilePath

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .background(
                    if (isSelected) IdePrimary.copy(alpha = 0.22f) else Color.Transparent
                )
                .clickable {
                    if (node.isDirectory) {
                        isExpanded = !isExpanded
                    } else {
                        onFileSelected(node.relativePath)
                    }
                }
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active file left indicator bar
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(30.dp)
                        .background(IdeAccent)
                )
            } else {
                Spacer(modifier = Modifier.width(2.dp))
            }

            Spacer(modifier = Modifier.width((8 + depth * 12).dp))

            if (node.isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = IdeAccent,
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(16.dp))
                val icon = when (node.extension.lowercase()) {
                    "kt", "java" -> Icons.Default.Code
                    "md", "txt" -> Icons.Default.Description
                    else -> Icons.Default.Code
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) IdeAccent else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (isSelected) IdeAccent else if (node.isIncluded) TextPrimary else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Inclusion checkbox
            Checkbox(
                checked = node.isIncluded,
                onCheckedChange = { onToggleInclusion(node.id) },
                colors = CheckboxDefaults.colors(
                    checkedColor = IdePrimary,
                    uncheckedColor = TextMuted,
                    checkmarkColor = TextPrimary
                ),
                modifier = Modifier
                    .size(20.dp)
                    .testTag("include_checkbox_${node.name}")
            )
        }

        if (node.isDirectory) {
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    node.children.forEach { child ->
                        FileTreeNodeItem(
                            node = child,
                            depth = depth + 1,
                            activeFilePath = activeFilePath,
                            onFileSelected = onFileSelected,
                            onToggleInclusion = onToggleInclusion
                        )
                    }
                }
            }
        }
    }
}

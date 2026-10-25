package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.FileNode
import com.example.data.model.Project
import com.example.ui.theme.IdeAccent
import com.example.ui.theme.IdeBorderSubtle
import com.example.ui.theme.IdePrimary
import com.example.ui.theme.IdeSurface
import com.example.ui.theme.IdeSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ProjectSourceCard(
    project: Project?,
    fileTree: List<FileNode>,
    onToggleFileInclusion: (String) -> Unit,
    onToggleAllFiles: (Boolean) -> Unit,
    onImportGitHubClick: () -> Unit,
    onImportZipClick: () -> Unit,
    onResetSample: () -> Unit,
    modifier: Modifier = Modifier
) {
    var allSelected by remember { mutableStateOf(true) }

    fun countIncluded(nodes: List<FileNode>): Int {
        var count = 0
        for (node in nodes) {
            if (!node.isDirectory && node.isIncluded) count++
            if (node.isDirectory) count += countIncluded(node.children)
        }
        return count
    }

    val includedCount = remember(fileTree) { countIncluded(fileTree) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(IdeSurface.copy(alpha = 0.55f))
            .padding(14.dp)
            .testTag("project_source_card")
    ) {
        // Clean Header: Title gets max space + Minimalist icon action buttons on right (GitHub original icon & ZIP icon)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(IdeSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Source,
                        contentDescription = null,
                        tint = IdeAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = project?.name ?: "No Project Loaded",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$includedCount file(s) marked for PC typing",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = IdeAccent
                    )
                }
            }

            // Compact Icon Actions (GitHub icon, ZIP icon, and Reset) without verbose text taking space
            Row(verticalAlignment = Alignment.CenterVertically) {
                // GitHub Original Icon Button
                IconButton(
                    onClick = onImportGitHubClick,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(IdeSurfaceVariant)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github),
                        contentDescription = "Import GitHub Repository",
                        tint = IdeAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // ZIP Archive Icon Button
                IconButton(
                    onClick = onImportZipClick,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(IdeSurfaceVariant)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_zip),
                        contentDescription = "Import ZIP Archive",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Reset Sample Project Button
                IconButton(
                    onClick = onResetSample,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(IdeSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset to Sample Project",
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = IdeBorderSubtle, thickness = 1.dp)
        Spacer(modifier = Modifier.height(6.dp))

        // Subheader: Quick Multi-Select Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FILES TO TYPE INTO PC",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = TextMuted
            )

            TextButton(
                onClick = {
                    allSelected = !allSelected
                    onToggleAllFiles(allSelected)
                },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (allSelected) "Deselect All" else "Select All",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = IdeAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Flat File Tree List without nested bordered boxes
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .padding(vertical = 2.dp)
        ) {
            items(fileTree, key = { it.id }) { node ->
                FileSelectorItem(
                    node = node,
                    depth = 0,
                    onToggleFileInclusion = onToggleFileInclusion
                )
            }
        }
    }
}

@Composable
private fun FileSelectorItem(
    node: FileNode,
    depth: Int,
    onToggleFileInclusion: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clickable {
                    if (node.isDirectory) {
                        isExpanded = !isExpanded
                    } else {
                        onToggleFileInclusion(node.id)
                    }
                }
                .padding(start = (6 + depth * 14).dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.isDirectory) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
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
                    tint = if (node.isIncluded) IdeAccent else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = node.name,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontFamily = if (!node.isDirectory) FontFamily.Monospace else FontFamily.Default
                ),
                color = if (node.isIncluded) TextPrimary else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (!node.isDirectory && node.sizeBytes > 0) {
                Text(
                    text = "${node.sizeBytes} B",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = TextMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Checkbox(
                checked = node.isIncluded,
                onCheckedChange = { onToggleFileInclusion(node.id) },
                colors = CheckboxDefaults.colors(
                    checkedColor = IdePrimary,
                    uncheckedColor = TextMuted,
                    checkmarkColor = TextPrimary
                ),
                modifier = Modifier.size(20.dp)
            )
        }

        if (node.isDirectory) {
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    node.children.forEach { child ->
                        FileSelectorItem(
                            node = child,
                            depth = depth + 1,
                            onToggleFileInclusion = onToggleFileInclusion
                        )
                    }
                }
            }
        }
    }
}

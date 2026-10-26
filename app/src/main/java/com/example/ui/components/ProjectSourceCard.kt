package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
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
    onSelectCodeOnly: () -> Unit = {},
    onImportGitHubClick: () -> Unit,
    onImportZipClick: () -> Unit,
    onResetSample: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var globalExpandTrigger by remember { mutableStateOf<Boolean?>(null) }

    fun collectAllFiles(nodes: List<FileNode>, out: MutableList<FileNode>) {
        for (node in nodes) {
            if (!node.isDirectory) out.add(node)
            else collectAllFiles(node.children, out)
        }
    }

    val allFiles = remember(fileTree) {
        val list = mutableListOf<FileNode>()
        collectAllFiles(fileTree, list)
        list
    }

    val totalFilesCount = allFiles.size
    val includedFilesCount = allFiles.count { it.isIncluded }
    val totalBytes = allFiles.filter { it.isIncluded }.sumOf { it.sizeBytes }
    val selectionRatio = if (totalFilesCount > 0) includedFilesCount.toFloat() / totalFilesCount else 0f

    fun filterNodes(nodes: List<FileNode>, query: String): List<FileNode> {
        if (query.isBlank()) return nodes
        val q = query.trim().lowercase()

        return nodes.mapNotNull { node ->
            if (node.isDirectory) {
                val filteredChildren = filterNodes(node.children, query)
                if (filteredChildren.isNotEmpty() || node.name.lowercase().contains(q)) {
                    node.copy(children = filteredChildren)
                } else {
                    null
                }
            } else {
                if (node.name.lowercase().contains(q) || node.relativePath.lowercase().contains(q)) {
                    node
                } else {
                    null
                }
            }
        }
    }

    val displayedTree = remember(fileTree, searchQuery) {
        filterNodes(fileTree, searchQuery)
    }

    Surface(
        color = IdeSurface.copy(alpha = 0.8f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, IdeBorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .testTag("project_source_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Minimal Project Identity & Action Bar
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
                            .clip(RoundedCornerShape(6.dp))
                            .background(IdeAccent.copy(alpha = 0.12f)),
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
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (project?.isBundledSample == true) "Sample Project • $totalFilesCount files" else "Custom Workspace • $totalFilesCount files",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }

                // Minimal Compact Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onImportGitHubClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(IdeSurfaceVariant.copy(alpha = 0.6f))
                            .testTag("github_import_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_github),
                            contentDescription = "GitHub",
                            tint = IdeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onImportZipClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(IdeSurfaceVariant.copy(alpha = 0.6f))
                            .testTag("zip_import_button")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_zip),
                            contentDescription = "ZIP",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onResetSample,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(IdeSurfaceVariant.copy(alpha = 0.6f))
                            .testTag("reset_sample_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selection Overview Bar with Progress Line
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(IdeSurfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$includedFilesCount of $totalFilesCount files selected",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                        color = if (includedFilesCount > 0) IdeAccent else TextMuted
                    )

                    val formattedBytes = when {
                        totalBytes >= 1024 * 1024 -> String.format("%.1f MB", totalBytes / (1024.0 * 1024.0))
                        totalBytes >= 1024 -> String.format("%.1f KB", totalBytes / 1024.0)
                        else -> "$totalBytes B"
                    }

                    Text(
                        text = formattedBytes,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.5.sp),
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { selectionRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp)),
                    color = IdeAccent,
                    trackColor = IdeSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Filter files...",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IdeAccent,
                    unfocusedBorderColor = IdeBorderSubtle,
                    focusedContainerColor = IdeSurfaceVariant.copy(alpha = 0.2f),
                    unfocusedContainerColor = IdeSurfaceVariant.copy(alpha = 0.1f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("file_search_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val allSelected = includedFilesCount == totalFilesCount && totalFilesCount > 0
                QuickActionChip(
                    label = if (allSelected) "Deselect All" else "Select All",
                    icon = Icons.Default.Check,
                    isActive = allSelected,
                    onClick = { onToggleAllFiles(!allSelected) }
                )

                QuickActionChip(
                    label = "Code Only",
                    icon = Icons.Default.Code,
                    isActive = false,
                    onClick = onSelectCodeOnly
                )

                QuickActionChip(
                    label = "Expand All",
                    icon = Icons.Default.UnfoldMore,
                    isActive = false,
                    onClick = { globalExpandTrigger = true }
                )

                QuickActionChip(
                    label = "Collapse All",
                    icon = Icons.Default.UnfoldLess,
                    isActive = false,
                    onClick = { globalExpandTrigger = false }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = IdeBorderSubtle, thickness = 1.dp)
            Spacer(modifier = Modifier.height(4.dp))

            // Minimal File Tree
            if (displayedTree.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No files match '$searchQuery'" else "Empty workspace tree",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .animateContentSize()
                        .testTag("file_tree_list")
                ) {
                    items(displayedTree, key = { it.id }) { rootNode ->
                        MinimalTreeNodeItem(
                            node = rootNode,
                            depth = 0,
                            searchActive = searchQuery.isNotBlank(),
                            globalExpand = globalExpandTrigger,
                            onToggleFileInclusion = onToggleFileInclusion
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) IdeAccent.copy(alpha = 0.18f) else IdeSurfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) IdeAccent else TextSecondary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.sp
                ),
                color = if (isActive) IdeAccent else TextSecondary
            )
        }
    }
}

private fun computeTriState(node: FileNode): ToggleableState {
    if (!node.isDirectory) {
        return if (node.isIncluded) ToggleableState.On else ToggleableState.Off
    }
    val allFiles = mutableListOf<FileNode>()
    fun walk(n: FileNode) {
        if (!n.isDirectory) allFiles.add(n)
        else n.children.forEach { walk(it) }
    }
    walk(node)
    if (allFiles.isEmpty()) {
        return if (node.isIncluded) ToggleableState.On else ToggleableState.Off
    }
    val included = allFiles.count { it.isIncluded }
    return when (included) {
        0 -> ToggleableState.Off
        allFiles.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
}

private fun countDescendantFiles(node: FileNode): Pair<Int, Int> {
    var total = 0
    var inc = 0
    fun walk(n: FileNode) {
        if (!n.isDirectory) {
            total++
            if (n.isIncluded) inc++
        } else {
            n.children.forEach { walk(it) }
        }
    }
    walk(node)
    return Pair(inc, total)
}

@Composable
private fun MinimalTreeNodeItem(
    node: FileNode,
    depth: Int,
    searchActive: Boolean,
    globalExpand: Boolean?,
    onToggleFileInclusion: (String) -> Unit
) {
    var isExpanded by remember(searchActive, globalExpand) {
        mutableStateOf(
            if (searchActive) true
            else globalExpand ?: (depth < 2)
        )
    }

    val triState = remember(node) { computeTriState(node) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable {
                    if (node.isDirectory) {
                        isExpanded = !isExpanded
                    } else {
                        onToggleFileInclusion(node.id)
                    }
                }
                .padding(start = (depth * 12).dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (depth > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(IdeBorderSubtle)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (node.isDirectory) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { isExpanded = !isExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                    contentDescription = null,
                    tint = IdeAccent,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp
                    ),
                    color = if (triState != ToggleableState.Off) TextPrimary else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val (incCount, totalCount) = countDescendantFiles(node)
                if (totalCount > 0) {
                    Text(
                        text = "$incCount/$totalCount",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (incCount > 0) IdeAccent else TextMuted
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                TriStateCheckbox(
                    state = triState,
                    onClick = { onToggleFileInclusion(node.id) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = IdePrimary,
                        checkmarkColor = TextPrimary
                    ),
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("folder_checkbox_${node.name}")
                )
            } else {
                Spacer(modifier = Modifier.width(22.dp))

                val (fileColor, extLabel) = when (node.extension.lowercase()) {
                    "kt", "java" -> Pair(Color(0xFFA855F7), node.extension.uppercase())
                    "xml", "gradle" -> Pair(Color(0xFFF59E0B), node.extension.uppercase())
                    "json", "yaml", "yml", "toml" -> Pair(Color(0xFF3B82F6), node.extension.uppercase())
                    "md", "txt" -> Pair(Color(0xFF14B8A6), "DOC")
                    else -> Pair(TextMuted, node.extension.take(3).uppercase().ifEmpty { "FILE" })
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(fileColor.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = extLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = fileColor
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = if (node.isIncluded) FontWeight.Medium else FontWeight.Normal
                    ),
                    color = if (node.isIncluded) TextPrimary else TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (node.sizeBytes > 0) {
                    val sizeText = when {
                        node.sizeBytes >= 1024 -> String.format("%.1fK", node.sizeBytes / 1024.0)
                        else -> "${node.sizeBytes}B"
                    }
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp
                        ),
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Checkbox(
                    checked = node.isIncluded,
                    onCheckedChange = { onToggleFileInclusion(node.id) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = IdePrimary,
                        uncheckedColor = TextMuted,
                        checkmarkColor = TextPrimary
                    ),
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("file_checkbox_${node.name}")
                )
            }
        }

        if (node.isDirectory) {
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    node.children.forEach { child ->
                        MinimalTreeNodeItem(
                            node = child,
                            depth = depth + 1,
                            searchActive = searchActive,
                            globalExpand = globalExpand,
                            onToggleFileInclusion = onToggleFileInclusion
                        )
                    }
                }
            }
        }
    }
}

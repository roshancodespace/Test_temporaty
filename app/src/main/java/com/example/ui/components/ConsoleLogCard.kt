package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LogEntry
import com.example.data.model.LogLevel
import com.example.ui.theme.IdeAccent
import com.example.ui.theme.IdeBackground
import com.example.ui.theme.IdeBorderSubtle
import com.example.ui.theme.IdeSurface
import com.example.ui.theme.IdeSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ConsoleLogCard(
    logs: List<LogEntry>,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val latestLog = logs.firstOrNull()

    Surface(
        color = IdeSurface.copy(alpha = 0.8f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, IdeBorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .testTag("console_log_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Header / Collapsed Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(IdeAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = IdeAccent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    if (!isExpanded && latestLog != null) {
                        Text(
                            text = "${latestLog.timestamp} ${latestLog.message}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "Activity Log (${logs.size})",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            ),
                            color = TextPrimary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isExpanded && logs.isNotEmpty()) {
                        IconButton(
                            onClick = onClearLogs,
                            modifier = Modifier.size(28.dp).testTag("clear_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear logs",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Expanded Console Stream
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = IdeBorderSubtle, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(IdeBackground)
                            .padding(8.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Text(
                                text = "No events logged yet.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = TextMuted,
                                modifier = Modifier.padding(4.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(logs, key = { it.id }) { log ->
                                    LogItemRow(log = log)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(log: LogEntry) {
    val (tagColor, tagText) = when (log.level) {
        LogLevel.INFO -> Pair(IdeAccent, "INFO")
        LogLevel.SUCCESS -> Pair(StatusSuccess, "OK")
        LogLevel.WARN -> Pair(StatusWarning, "WARN")
        LogLevel.ERROR -> Pair(StatusError, "ERR")
        LogLevel.KEYSTROKE -> Pair(IdeAccent, "KEY")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = log.timestamp,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            ),
            color = TextMuted
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "[$tagText]",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = tagColor
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            color = TextPrimary
        )
    }
}

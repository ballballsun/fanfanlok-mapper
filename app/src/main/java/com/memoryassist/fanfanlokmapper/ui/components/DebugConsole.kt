package com.memoryassist.fanfanlokmapper.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memoryassist.fanfanlokmapper.utils.Logger
import kotlinx.coroutines.launch

/**
 * Debug console component for displaying logs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsole(
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    var logs by remember { mutableStateOf(Logger.logEntries) }
    var selectedLogLevel by remember { mutableStateOf<Logger.LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    var showTimestamps by remember { mutableStateOf(true) }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    
    // Update logs periodically
    LaunchedEffect(Unit) {
        while (true) {
            logs = Logger.logEntries
            if (autoScroll && logs.isNotEmpty()) {
                listState.animateScrollToItem(logs.size - 1)
            }
            kotlinx.coroutines.delay(500)
        }
    }
    
    val consoleHeight by animateDpAsState(
        targetValue = if (isExpanded) 400.dp else 200.dp,
        animationSpec = tween(300),
        label = "console height"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(consoleHeight),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            DebugConsoleHeader(
                isExpanded = isExpanded,
                onExpandedChange = { expanded ->
                    onExpandedChange?.invoke(expanded)
                },
                logCount = logs.size,
                onClear = {
                    Logger.clearLogs()
                    logs = emptyList()
                },
                onExport = {
                    val logsText = Logger.getFormattedLogs()
                    clipboardManager.setText(AnnotatedString(logsText))
                }
            )
            
            // Controls
            DebugConsoleControls(
                selectedLogLevel = selectedLogLevel,
                onLogLevelSelected = { selectedLogLevel = it },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                autoScroll = autoScroll,
                onAutoScrollChange = { autoScroll = it },
                showTimestamps = showTimestamps,
                onShowTimestampsChange = { showTimestamps = it }
            )
            
            Divider(color = Color(0xFF3E3E3E))
            
            // Log list
            val filteredLogs = logs.filter { log ->
                (selectedLogLevel == null || log.level == selectedLogLevel) &&
                (searchQuery.isEmpty() || log.message.contains(searchQuery, ignoreCase = true))
            }
            
            if (filteredLogs.isEmpty()) {
                EmptyLogState(
                    hasFilters = selectedLogLevel != null || searchQuery.isNotEmpty()
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = filteredLogs,
                        key = { "${it.timestamp}_${it.message.hashCode()}" }
                    ) { log ->
                        LogEntry(
                            log = log,
                            showTimestamp = showTimestamps,
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString("[${log.timestamp}] ${log.level.displayName}: ${log.message}")
                                )
                            }
                        )
                    }
                    
                    // Add padding at the end
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * Debug console header
 */
@Composable
private fun DebugConsoleHeader(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    logCount: Int,
    onClear: () -> Unit,
    onExport: () -> Unit
) {
    Surface(
        color = Color(0xFF2D2D2D),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Debug Console",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
                
                Text(
                    text = "Debug Console",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                
                Badge(
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ) {
                    Text(
                        text = logCount.toString(),
                        fontSize = 10.sp
                    )
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onExport,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export Logs",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Logs",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                IconButton(
                    onClick = { onExpandedChange(!isExpanded) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Debug console controls
 */
@Composable
private fun DebugConsoleControls(
    selectedLogLevel: Logger.LogLevel?,
    onLogLevelSelected: (Logger.LogLevel?) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    showTimestamps: Boolean,
    onShowTimestampsChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF252525))
            .padding(8.dp)
    ) {
        // Log level filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = selectedLogLevel == null,
                onClick = { onLogLevelSelected(null) },
                label = { Text("All", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF4CAF50),
                    selectedLabelColor = Color.White,
                    containerColor = Color(0xFF3E3E3E),
                    labelColor = Color.Gray
                ),
                modifier = Modifier.height(28.dp)
            )
            
            Logger.LogLevel.values().forEach { level ->
                FilterChip(
                    selected = selectedLogLevel == level,
                    onClick = { 
                        onLogLevelSelected(if (selectedLogLevel == level) null else level)
                    },
                    label = { 
                        Text(
                            text = level.displayName,
                            fontSize = 12.sp,
                            color = if (selectedLogLevel == level) Color.White else level.color
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = level.color.copy(alpha = 0.3f),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF3E3E3E),
                        labelColor = level.color
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Search and options
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { 
                    Text(
                        "Search logs...",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                modifier = Modifier.size(14.dp),
                                tint = Color.Gray
                            )
                        }
                    }
                } else null,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 12.sp,
                    color = Color.White
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4CAF50),
                    unfocusedBorderColor = Color(0xFF3E3E3E),
                    cursorColor = Color(0xFF4CAF50)
                )
            )
            
            // Toggle buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ToggleButton(
                    checked = autoScroll,
                    onCheckedChange = onAutoScrollChange,
                    icon = Icons.Default.UnfoldMore,
                    tooltip = "Auto Scroll"
                )
                
                ToggleButton(
                    checked = showTimestamps,
                    onCheckedChange = onShowTimestampsChange,
                    icon = Icons.Default.Schedule,
                    tooltip = "Show Timestamps"
                )
            }
        }
    }
}

/**
 * Individual log entry
 */
@Composable
private fun LogEntry(
    log: Logger.LogEntry,
    showTimestamp: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF2A2A2A))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Log level indicator
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .background(log.level.color, RoundedCornerShape(2.dp))
        )
        
        // Timestamp
        if (showTimestamp) {
            Text(
                text = log.timestamp,
                fontSize = 11.sp,
                color = Color(0xFF808080),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(80.dp)
            )
        }
        
        // Level badge
        Text(
            text = log.level.displayName,
            fontSize = 11.sp,
            color = log.level.color,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(50.dp)
        )
        
        // Message
        Text(
            text = log.message,
            fontSize = 12.sp,
            color = Color(0xFFE0E0E0),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Toggle button for console options
 */
@Composable
private fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String
) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = if (checked) Color(0xFF4CAF50) else Color.Gray,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Empty state for logs
 */
@Composable
private fun EmptyLogState(
    hasFilters: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (hasFilters) Icons.Default.SearchOff else Icons.Default.Article,
                contentDescription = null,
                tint = Color(0xFF808080),
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (hasFilters) {
                    "No logs match the current filters"
                } else {
                    "No logs yet"
                },
                color = Color(0xFF808080),
                fontSize = 14.sp
            )
        }
    }
}
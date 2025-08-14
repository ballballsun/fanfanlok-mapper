package com.memoryassist.fanfanlokmapper.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memoryassist.fanfanlokmapper.ui.components.*
import com.memoryassist.fanfanlokmapper.utils.Logger
import com.memoryassist.fanfanlokmapper.viewmodel.MainViewModel
import com.memoryassist.fanfanlokmapper.viewmodel.NavigationState
import kotlinx.coroutines.launch

/**
 * Main screen of the FanFanLok Mapper application
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToProcessing: (Uri) -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val processingHistory by viewModel.processingHistory.collectAsStateWithLifecycle()
    val showDebugConsole by viewModel.showDebugConsole.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "FanFanLok Mapper",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Processing History"
                        )
                    }
                    IconButton(onClick = { viewModel.toggleDebugConsole() }) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Debug Console",
                            tint = if (showDebugConsole) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedImageUri != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        selectedImageUri?.let { uri ->
                            onNavigateToProcessing(uri)
                            Logger.logUserInteraction("Process button clicked")
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Process Image"
                        )
                    },
                    text = { Text("Process Image") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Welcome card
                    WelcomeCard(
                        totalProcessed = uiState.totalProcessed,
                        lastProcessedTime = uiState.lastProcessedTime
                    )
                    
                    // Image picker
                    ImagePicker(
                        onImageSelected = { uri ->
                            selectedImageUri = uri
                            if (appSettings.autoProcessOnSelect) {
                                onNavigateToProcessing(uri)
                            }
                        },
                        onMultipleImagesSelected = null, // Single image only for now
                        allowMultiple = false
                    )
                    
                    // Quick stats
                    if (uiState.totalProcessed > 0) {
                        QuickStatsCard(
                            successRate = uiState.successRate,
                            averageTime = uiState.averageProcessingTime,
                            cacheSize = uiState.cacheSize
                        )
                    }
                    
                    // Recent processing (if any)
                    if (processingHistory.isNotEmpty()) {
                        RecentProcessingCard(
                            recentItems = processingHistory.take(3),
                            onItemClick = { item ->
                                // Could reload the result if cached
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Loading result from history..."
                                    )
                                }
                            },
                            onViewAll = { showHistoryDialog = true }
                        )
                    }
                }
                
                // Debug console (if enabled)
                AnimatedVisibility(
                    visible = showDebugConsole,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    DebugConsole(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        isExpanded = false
                    )
                }
            }
        }
    }
    
    // Dialogs
    if (showHistoryDialog) {
        ProcessingHistoryDialog(
            history = processingHistory,
            onDismiss = { showHistoryDialog = false },
            onItemSelected = { item ->
                // Handle history item selection
                showHistoryDialog = false
            },
            onClearHistory = {
                viewModel.clearHistory()
                showHistoryDialog = false
            }
        )
    }
    
    if (showSettingsDialog) {
        SettingsDialog(
            appSettings = appSettings,
            onSettingsChanged = { updatedSettings ->
                viewModel.updateAppSettings { updatedSettings }
            },
            onDismiss = { showSettingsDialog = false }
        )
    }
    
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

/**
 * Welcome card with app info and stats
 */
@Composable
private fun WelcomeCard(
    totalProcessed: Int,
    lastProcessedTime: Long?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Dashboard,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Memory Card Detection",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Detect and map card positions in a 4×6 grid",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            if (totalProcessed > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = totalProcessed.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Images Processed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (lastProcessedTime != null) {
                        val timeAgo = getTimeAgo(lastProcessedTime)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = timeAgo,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Last Processed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick statistics card
 */
@Composable
private fun QuickStatsCard(
    successRate: Float,
    averageTime: Long,
    cacheSize: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.CheckCircle,
                value = "${successRate.toInt()}%",
                label = "Success Rate"
            )
            StatItem(
                icon = Icons.Default.Timer,
                value = "${averageTime}ms",
                label = "Avg. Time"
            )
            StatItem(
                icon = Icons.Default.Storage,
                value = cacheSize,
                label = "Cache"
            )
        }
    }
}

/**
 * Individual stat item
 */
@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Recent processing card
 */
@Composable
private fun RecentProcessingCard(
    recentItems: List<com.memoryassist.fanfanlokmapper.data.models.ProcessingHistoryEntry>,
    onItemClick: (com.memoryassist.fanfanlokmapper.data.models.ProcessingHistoryEntry) -> Unit,
    onViewAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Processing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                TextButton(onClick = onViewAll) {
                    Text("View All")
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            recentItems.forEach { item ->
                ProcessingHistoryItem(
                    item = item,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

/**
 * Processing history item
 */
@Composable
private fun ProcessingHistoryItem(
    item: com.memoryassist.fanfanlokmapper.data.models.ProcessingHistoryEntry,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName.take(20) + if (item.fileName.length > 20) "..." else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = item.formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${item.detectedCards} cards",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (item.isSuccessful) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Text(
                    text = "${item.processingTimeMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Helper function to format time ago
 */
private fun getTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}
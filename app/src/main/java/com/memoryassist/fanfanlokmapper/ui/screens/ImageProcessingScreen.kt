package com.memoryassist.fanfanlokmapper.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import com.memoryassist.fanfanlokmapper.data.models.ExportSettings
import com.memoryassist.fanfanlokmapper.data.models.OverlaySettings
import com.memoryassist.fanfanlokmapper.ui.components.*
import com.memoryassist.fanfanlokmapper.viewmodel.*
import kotlinx.coroutines.launch

/**
 * Image processing screen with detection overlays
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageProcessingScreen(
    imageUri: Uri,
    onNavigateBack: () -> Unit,
    onExportClick: () -> Unit,
    viewModel: ImageProcessingViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val detectionResult by viewModel.detectionResult.collectAsStateWithLifecycle()
    val cardPositions by viewModel.cardPositions.collectAsStateWithLifecycle()
    val processingProgress by viewModel.processingProgress.collectAsStateWithLifecycle()
    val detectionConfig by viewModel.detectionConfig.collectAsStateWithLifecycle()
    val processedImageBitmap by viewModel.processedImageBitmap.collectAsStateWithLifecycle()
    
    val overlaySettings by mainViewModel.overlaySettings.collectAsStateWithLifecycle()
    val exportSettings by mainViewModel.exportSettings.collectAsStateWithLifecycle()
    
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Process image on first load
    LaunchedEffect(imageUri) {
        if (uiState.selectedImageUri != imageUri) {
            viewModel.processImage(imageUri)
        }
    }
    
    // Handle messages
    LaunchedEffect(Unit) {
        launch {
            viewModel.errorMessage.collect { message ->
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "Dismiss",
                    duration = SnackbarDuration.Long
                )
            }
        }
        
        launch {
            viewModel.successMessage.collect { message ->
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }
    
    Scaffold(
        topBar = {
            ProcessingTopBar(
                processingState = processingState,
                onNavigateBack = onNavigateBack,
                onSettingsClick = { /* Open settings */ },
                onHelpClick = { /* Show help */ }
            )
        },
        bottomBar = {
            ProcessingBottomBar(
                isProcessing = uiState.isProcessing,
                hasResults = cardPositions.isNotEmpty(),
                onReprocess = { viewModel.reprocessCurrentImage() },
                onExport = onExportClick,
                onClear = { viewModel.clearAllCards() },
                exportSettings = exportSettings
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Progress indicator
                AnimatedVisibility(
                    visible = uiState.isProcessing,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    ProcessingProgressBar(
                        progress = processingProgress,
                        stage = uiState.processingStage,
                        onCancel = { viewModel.cancelProcessing() }
                    )
                }
                
                // Main content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Display processed image or original with overlays
                    ProcessedImageDisplay(
                        originalImageUri = imageUri,
                        processedBitmap = processedImageBitmap,
                        cardPositions = cardPositions,
                        overlaySettings = overlaySettings,
                        imageMetadata = uiState.imageMetadata,
                        onCardRemoved = { card ->
                            viewModel.removeCard(card)
                        },
                        onRegenerateImage = {
                            viewModel.generateProcessedImage(
                                showConfidence = overlaySettings.showConfidence,
                                showGridPosition = overlaySettings.showGridPosition
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Floating action buttons
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Toggle processed image display
                        SmallFloatingActionButton(
                            onClick = {
                                if (processedImageBitmap != null) {
                                    viewModel.clearProcessedImage()
                                } else {
                                    viewModel.generateProcessedImage(
                                        showConfidence = overlaySettings.showConfidence,
                                        showGridPosition = overlaySettings.showGridPosition
                                    )
                                }
                            },
                            containerColor = if (processedImageBitmap != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ) {
                            Icon(
                                imageVector = if (processedImageBitmap != null) {
                                    Icons.Default.Visibility
                                } else {
                                    Icons.Default.VisibilityOff
                                },
                                contentDescription = "Toggle Processed Image"
                            )
                        }
                        
                        // Toggle confidence display
                        SmallFloatingActionButton(
                            onClick = {
                                mainViewModel.updateOverlaySettings {
                                    withConfidence(!showConfidence)
                                }
                                // Regenerate processed image if it exists
                                if (processedImageBitmap != null) {
                                    viewModel.generateProcessedImage(
                                        showConfidence = !overlaySettings.showConfidence,
                                        showGridPosition = overlaySettings.showGridPosition
                                    )
                                }
                            },
                            containerColor = if (overlaySettings.showConfidence) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Percent,
                                contentDescription = "Toggle Confidence"
                            )
                        }
                        
                        // Toggle grid position display
                        SmallFloatingActionButton(
                            onClick = {
                                mainViewModel.updateOverlaySettings {
                                    withGridLines(!showGridLines)
                                }
                                // Regenerate processed image if it exists
                                if (processedImageBitmap != null) {
                                    viewModel.generateProcessedImage(
                                        showConfidence = overlaySettings.showConfidence,
                                        showGridPosition = !overlaySettings.showGridLines
                                    )
                                }
                            },
                            containerColor = if (overlaySettings.showGridLines) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Grid3x3,
                                contentDescription = "Toggle Grid"
                            )
                        }
                        
                        SmallFloatingActionButton(
                            onClick = { viewModel.restoreAllCards() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Restore All"
                            )
                        }
                    }
                }
                
                // Results summary
                AnimatedVisibility(
                    visible = detectionResult != null,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    detectionResult?.let { result ->
                        ResultsSummaryCard(
                            detectionResult = result,
                            removedCount = uiState.removedCardsCount,
                            processingTime = uiState.lastProcessingTime,
                            onToggleDetails = { /* Show detailed results */ }
                        )
                    }
                }
            }
            
            // Config preset buttons
            if (processingState == ProcessingState.Idle) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConfigPreset.values().forEach { preset ->
                        ElevatedFilterChip(
                            selected = false,
                            onClick = { viewModel.usePresetConfig(preset) },
                            label = { Text(preset.name) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top app bar for processing screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProcessingTopBar(
    processingState: ProcessingState,
    onNavigateBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Image Processing")
                
                // Processing state indicator
                when (processingState) {
                    is ProcessingState.Loading,
                    is ProcessingState.Detecting,
                    is ProcessingState.Filtering,
                    is ProcessingState.Mapping -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    is ProcessingState.Success -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    is ProcessingState.Failed -> {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    else -> {}
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            }
            IconButton(onClick = onHelpClick) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Help"
                )
            }
        }
    )
}

/**
 * Bottom bar with processing actions
 */
@Composable
private fun ProcessingBottomBar(
    isProcessing: Boolean,
    hasResults: Boolean,
    onReprocess: () -> Unit,
    onExport: () -> Unit,
    onClear: () -> Unit,
    exportSettings: ExportSettings
) {
    BottomAppBar {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Reprocess button
            TextButton(
                onClick = onReprocess,
                enabled = !isProcessing
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reprocess",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reprocess")
            }
            
            // Export button
            Button(
                onClick = onExport,
                enabled = hasResults && !isProcessing
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "Export",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export")
            }
            
            // Clear button
            TextButton(
                onClick = onClear,
                enabled = hasResults && !isProcessing,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clear")
            }
        }
    }
}

/**
 * Processing progress bar
 */
@Composable
private fun ProcessingProgressBar(
    progress: Float,
    stage: String?,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stage ?: "Processing...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Image display with card overlays
 */
@Composable
private fun ImageWithOverlays(
    imageUri: Uri,
    cardPositions: List<CardPosition>,
    overlaySettings: OverlaySettings,
    onCardRemoved: (CardPosition) -> Unit,
    imageMetadata: com.memoryassist.fanfanlokmapper.domain.repository.ImageMetadata? = null,
    modifier: Modifier = Modifier
) {
    var imageSize by remember { mutableStateOf(Pair(0, 0)) }
    var actualImageBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    
    Box(modifier = modifier) {
        // Display image
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageUri)
                .build(),
            contentDescription = "Processed Image",
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    // Calculate actual image display bounds considering ContentScale.Fit
                    val containerWidth = coordinates.size.width.toFloat()
                    val containerHeight = coordinates.size.height.toFloat()
                    
                    if (imageSize.first > 0 && imageSize.second > 0) {
                        val imageAspectRatio = imageSize.first.toFloat() / imageSize.second.toFloat()
                        val containerAspectRatio = containerWidth / containerHeight
                        
                        val (displayWidth, displayHeight, offsetX, offsetY) = if (imageAspectRatio > containerAspectRatio) {
                            // Image is wider - fit to container width
                            val displayWidth = containerWidth
                            val displayHeight = containerWidth / imageAspectRatio
                            val offsetY = (containerHeight - displayHeight) / 2f
                            Tuple4(displayWidth, displayHeight, 0f, offsetY)
                        } else {
                            // Image is taller - fit to container height
                            val displayHeight = containerHeight
                            val displayWidth = containerHeight * imageAspectRatio
                            val offsetX = (containerWidth - displayWidth) / 2f
                            Tuple4(displayWidth, displayHeight, offsetX, 0f)
                        }
                        
                        actualImageBounds = androidx.compose.ui.geometry.Rect(
                            offset = Offset(offsetX, offsetY),
                            size = Size(displayWidth, displayHeight)
                        )
                    }
                },
            contentScale = ContentScale.Fit,
            onSuccess = {
                // Get image dimensions from metadata if available
                imageSize = imageMetadata?.let { metadata ->
                    metadata.width to metadata.height
                } ?: (1000 to 1000) // Default size if metadata not available
            }
        )
        
        // Overlay detected cards
        if (imageSize.first > 0 && imageSize.second > 0 && actualImageBounds != androidx.compose.ui.geometry.Rect.Zero) {
            CardOverlay(
                cardPositions = cardPositions,
                imageWidth = imageSize.first,
                imageHeight = imageSize.second,
                actualImageBounds = actualImageBounds,
                onCardRemoved = onCardRemoved,
                showConfidence = overlaySettings.showConfidence,
                showGridPosition = overlaySettings.showGridPosition,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Helper data class for tuple
private data class Tuple4<T>(val first: T, val second: T, val third: T, val fourth: T)

/**
 * Results summary card
 */
@Composable
private fun ResultsSummaryCard(
    detectionResult: com.memoryassist.fanfanlokmapper.data.models.DetectionResult,
    removedCount: Int,
    processingTime: Long,
    onToggleDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                    text = "Detection Results",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onToggleDetails) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Toggle Details"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ResultStat(
                    label = "Detected",
                    value = detectionResult.totalCardsDetected.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                
                ResultStat(
                    label = "Valid",
                    value = detectionResult.validCardsCount.toString(),
                    color = MaterialTheme.colorScheme.tertiary
                )
                
                ResultStat(
                    label = "Removed",
                    value = removedCount.toString(),
                    color = MaterialTheme.colorScheme.error
                )
                
                ResultStat(
                    label = "Time",
                    value = "${processingTime}ms",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/**
 * Individual result statistic
 */
@Composable
private fun ResultStat(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Component that displays either the processed image with rectangles or original with overlays
 */
@Composable
private fun ProcessedImageDisplay(
    originalImageUri: Uri,
    processedBitmap: Bitmap?,
    cardPositions: List<CardPosition>,
    overlaySettings: OverlaySettings,
    imageMetadata: com.memoryassist.fanfanlokmapper.domain.repository.ImageMetadata?,
    onCardRemoved: (CardPosition) -> Unit,
    onRegenerateImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (processedBitmap != null) {
            // Display processed image with rectangles drawn directly
            Image(
                bitmap = processedBitmap.asImageBitmap(),
                contentDescription = "Processed Image with Detections",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            // Show info text
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = "Processed Image View\n${cardPositions.size} detections shown",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Fallback to original image with overlays
            ImageWithOverlays(
                imageUri = originalImageUri,
                cardPositions = cardPositions,
                overlaySettings = overlaySettings,
                imageMetadata = imageMetadata,
                onCardRemoved = onCardRemoved,
                modifier = Modifier.fillMaxSize()
            )
            
            // Show generate button if we have detections
            if (cardPositions.isNotEmpty()) {
                FloatingActionButton(
                    onClick = onRegenerateImage,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Generate Processed Image"
                    )
                }
            }
        }
    }
}
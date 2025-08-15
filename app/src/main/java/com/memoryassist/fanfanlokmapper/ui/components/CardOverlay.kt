package com.memoryassist.fanfanlokmapper.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import com.memoryassist.fanfanlokmapper.utils.Constants
import com.memoryassist.fanfanlokmapper.utils.Logger
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Overlay component that draws green rectangles over detected cards
 */
@Composable
fun CardOverlay(
    cardPositions: List<CardPosition>,
    imageWidth: Int,
    imageHeight: Int,
    onCardRemoved: (CardPosition) -> Unit,
    actualImageBounds: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect.Zero,
    onCardSelected: ((CardPosition) -> Unit)? = null,
    showConfidence: Boolean = true,
    showGridPosition: Boolean = false,
    modifier: Modifier = Modifier
) {
    var selectedCard by remember { mutableStateOf<CardPosition?>(null) }
    var longPressedCard by remember { mutableStateOf<CardPosition?>(null) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    
    val density = LocalDensity.current
    
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cardPositions) {
                    detectTapGestures(
                        onTap = { offset ->
                            // Find card at tap position
                            val tappedCard = findCardAtPosition(
                                offset,
                                cardPositions,
                                actualImageBounds,
                                imageWidth.toFloat(),
                                imageHeight.toFloat()
                            )
                            
                            tappedCard?.let {
                                selectedCard = if (selectedCard == it) null else it
                                onCardSelected?.invoke(it)
                                Logger.logUserInteraction("Card tapped", "Card ${it.id}")
                            }
                        },
                        onLongPress = { offset ->
                            // Find card at long press position
                            val pressedCard = findCardAtPosition(
                                offset,
                                cardPositions,
                                actualImageBounds,
                                imageWidth.toFloat(),
                                imageHeight.toFloat()
                            )
                            
                            pressedCard?.let {
                                longPressedCard = it
                                showRemoveDialog = true
                                Logger.logUserInteraction("Card long-pressed", "Card ${it.id}")
                            }
                        }
                    )
                }
        ) {
            // Draw all card overlays
            cardPositions.forEach { card ->
                if (!card.isManuallyRemoved) {
                    drawCardOverlay(
                        card = card,
                        actualImageBounds = actualImageBounds,
                        imageWidth = imageWidth.toFloat(),
                        imageHeight = imageHeight.toFloat(),
                        isSelected = card == selectedCard,
                        isLongPressed = card == longPressedCard,
                        showConfidence = showConfidence,
                        showGridPosition = showGridPosition
                    )
                }
            }
            
            // Draw grid lines if enabled
            if (showGridPosition) {
                drawGridLines(
                    rows = Constants.GRID_ROWS,
                    columns = Constants.GRID_COLUMNS,
                    actualImageBounds = actualImageBounds
                )
            }
        }
        
        // Card info panel for selected card
        selectedCard?.let { card ->
            CardInfoPanel(
                card = card,
                onDismiss = { selectedCard = null },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
    
    // Remove confirmation dialog
    if (showRemoveDialog && longPressedCard != null) {
        RemoveCardDialog(
            card = longPressedCard!!,
            onConfirm = {
                onCardRemoved(longPressedCard!!)
                longPressedCard = null
                showRemoveDialog = false
                Logger.logUserInteraction("Card removed", "Card ${longPressedCard?.id}")
            },
            onDismiss = {
                longPressedCard = null
                showRemoveDialog = false
            }
        )
    }
}

/**
 * Draw a single card overlay
 */
private fun DrawScope.drawCardOverlay(
    card: CardPosition,
    actualImageBounds: androidx.compose.ui.geometry.Rect,
    imageWidth: Float,
    imageHeight: Float,
    isSelected: Boolean,
    isLongPressed: Boolean,
    showConfidence: Boolean,
    showGridPosition: Boolean
) {
    // Use actual image bounds if available, otherwise fall back to full canvas
    val imageBounds = if (actualImageBounds != androidx.compose.ui.geometry.Rect.Zero) {
        actualImageBounds
    } else {
        androidx.compose.ui.geometry.Rect(
            offset = Offset.Zero,
            size = Size(size.width, size.height)
        )
    }
    
    // Calculate scaled position within the actual image display area
    val scaleX = imageBounds.width / imageWidth
    val scaleY = imageBounds.height / imageHeight
    
    val scaledLeft = imageBounds.left + (card.left * scaleX)
    val scaledTop = imageBounds.top + (card.top * scaleY)
    val scaledWidth = card.width * scaleX
    val scaledHeight = card.height * scaleY
    
    // Determine color based on state
    val strokeColor = when {
        isLongPressed -> Color.Red
        isSelected -> Color.Yellow
        else -> Color.Green
    }
    
    val strokeWidth = when {
        isSelected || isLongPressed -> 4.dp.toPx()
        else -> Constants.OVERLAY_STROKE_WIDTH.dp.toPx()
    }
    
    // Draw rectangle with rounded corners
    drawRoundRect(
        color = strokeColor.copy(alpha = 0.2f),
        topLeft = Offset(scaledLeft, scaledTop),
        size = Size(scaledWidth, scaledHeight),
        cornerRadius = CornerRadius(Constants.OVERLAY_CORNER_RADIUS.dp.toPx())
    )
    
    drawRoundRect(
        color = strokeColor,
        topLeft = Offset(scaledLeft, scaledTop),
        size = Size(scaledWidth, scaledHeight),
        cornerRadius = CornerRadius(Constants.OVERLAY_CORNER_RADIUS.dp.toPx()),
        style = Stroke(
            width = strokeWidth,
            pathEffect = if (isLongPressed) {
                PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f)
            } else null
        )
    )
    
    // Draw center crosshair for selected cards
    if (isSelected) {
        val centerX = scaledLeft + scaledWidth / 2
        val centerY = scaledTop + scaledHeight / 2
        val crosshairSize = 10.dp.toPx()
        
        drawLine(
            color = strokeColor,
            start = Offset(centerX - crosshairSize, centerY),
            end = Offset(centerX + crosshairSize, centerY),
            strokeWidth = 2.dp.toPx()
        )
        
        drawLine(
            color = strokeColor,
            start = Offset(centerX, centerY - crosshairSize),
            end = Offset(centerX, centerY + crosshairSize),
            strokeWidth = 2.dp.toPx()
        )
    }
    
    // Draw labels
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            color = strokeColor.toArgb()
            textSize = 12.sp.toPx()
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        
        // Draw confidence percentage
        if (showConfidence) {
            val confidenceText = "${(card.confidence * 100).toInt()}%"
            canvas.nativeCanvas.drawText(
                confidenceText,
                scaledLeft + 4.dp.toPx(),
                scaledTop + 14.dp.toPx(),
                paint
            )
        }
        
        // Draw grid position
        if (showGridPosition && card.hasValidGridPosition) {
            val gridText = "${card.gridRow + 1},${card.gridColumn + 1}"
            val textWidth = paint.measureText(gridText)
            canvas.nativeCanvas.drawText(
                gridText,
                scaledLeft + scaledWidth - textWidth - 4.dp.toPx(),
                scaledTop + scaledHeight - 4.dp.toPx(),
                paint
            )
        }
    }
}

/**
 * Draw grid lines for reference
 */
private fun DrawScope.drawGridLines(
    rows: Int,
    columns: Int,
    actualImageBounds: androidx.compose.ui.geometry.Rect
) {
    // Use actual image bounds if available, otherwise fall back to full canvas
    val imageBounds = if (actualImageBounds != androidx.compose.ui.geometry.Rect.Zero) {
        actualImageBounds
    } else {
        androidx.compose.ui.geometry.Rect(
            offset = Offset.Zero,
            size = Size(size.width, size.height)
        )
    }
    
    val cellWidth = imageBounds.width / columns
    val cellHeight = imageBounds.height / rows
    
    val strokeColor = Color.Gray.copy(alpha = 0.3f)
    val strokeWidth = 1.dp.toPx()
    
    // Draw vertical lines
    for (i in 1 until columns) {
        val x = imageBounds.left + (i * cellWidth)
        drawLine(
            color = strokeColor,
            start = Offset(x, imageBounds.top),
            end = Offset(x, imageBounds.bottom),
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
        )
    }
    
    // Draw horizontal lines
    for (i in 1 until rows) {
        val y = imageBounds.top + (i * cellHeight)
        drawLine(
            color = strokeColor,
            start = Offset(imageBounds.left, y),
            end = Offset(imageBounds.right, y),
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
        )
    }
}

/**
 * Find card at given position
 */
private fun findCardAtPosition(
    offset: Offset,
    cards: List<CardPosition>,
    actualImageBounds: androidx.compose.ui.geometry.Rect,
    imageWidth: Float,
    imageHeight: Float
): CardPosition? {
    // Use actual image bounds if available
    val imageBounds = if (actualImageBounds != androidx.compose.ui.geometry.Rect.Zero) {
        actualImageBounds
    } else {
        // If no bounds provided, assume full canvas (fallback)
        return null
    }
    
    val scaleX = imageBounds.width / imageWidth
    val scaleY = imageBounds.height / imageHeight
    
    return cards.firstOrNull { card ->
        if (card.isManuallyRemoved) return@firstOrNull false
        
        val scaledLeft = imageBounds.left + (card.left * scaleX)
        val scaledTop = imageBounds.top + (card.top * scaleY)
        val scaledRight = imageBounds.left + (card.right * scaleX)
        val scaledBottom = imageBounds.top + (card.bottom * scaleY)
        
        offset.x >= scaledLeft && 
        offset.x <= scaledRight && 
        offset.y >= scaledTop && 
        offset.y <= scaledBottom
    }
}

/**
 * Information panel for selected card
 */
@Composable
private fun CardInfoPanel(
    card: CardPosition,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                    text = "Card #${card.id}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoColumn(
                    label = "Position",
                    value = "(${card.roundedCenterX}, ${card.roundedCenterY})"
                )
                
                InfoColumn(
                    label = "Size",
                    value = "${card.width.toInt()}×${card.height.toInt()}"
                )
                
                InfoColumn(
                    label = "Grid",
                    value = if (card.hasValidGridPosition) {
                        "(${card.gridRow + 1}, ${card.gridColumn + 1})"
                    } else "N/A"
                )
                
                InfoColumn(
                    label = "Confidence",
                    value = "${(card.confidence * 100).toInt()}%"
                )
            }
        }
    }
}

/**
 * Information column for card details
 */
@Composable
private fun InfoColumn(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Dialog for confirming card removal
 */
@Composable
private fun RemoveCardDialog(
    card: CardPosition,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = "Remove Card Detection?")
        },
        text = {
            Column {
                Text(
                    text = "Are you sure you want to remove this card detection?"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Card #${card.id}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Position: (${card.roundedCenterX}, ${card.roundedCenterY})",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (card.hasValidGridPosition) {
                            Text(
                                text = "Grid: Row ${card.gridRow + 1}, Column ${card.gridColumn + 1}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Overlay statistics bar
 */
@Composable
fun OverlayStatistics(
    totalCards: Int,
    validCards: Int,
    removedCards: Int,
    averageConfidence: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.GridOn,
                label = "Total",
                value = totalCards.toString(),
                color = MaterialTheme.colorScheme.primary
            )
            
            StatItem(
                icon = Icons.Default.CheckCircle,
                label = "Valid",
                value = validCards.toString(),
                color = Color(0xFF4CAF50)
            )
            
            StatItem(
                icon = Icons.Default.Cancel,
                label = "Removed",
                value = removedCards.toString(),
                color = MaterialTheme.colorScheme.error
            )
            
            StatItem(
                icon = Icons.Default.Analytics,
                label = "Confidence",
                value = "${(averageConfidence * 100).toInt()}%",
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

/**
 * Individual statistic item
 */
@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
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
 * Overlay controls for adjusting display options
 */
@Composable
fun OverlayControls(
    showConfidence: Boolean,
    onShowConfidenceChange: (Boolean) -> Unit,
    showGridPosition: Boolean,
    onShowGridPositionChange: (Boolean) -> Unit,
    showGridLines: Boolean,
    onShowGridLinesChange: (Boolean) -> Unit,
    onClearAll: () -> Unit,
    onRestoreAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Overlay Options",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Toggle options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Confidence",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = showConfidence,
                    onCheckedChange = onShowConfidenceChange
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Grid Position",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = showGridPosition,
                    onCheckedChange = onShowGridPositionChange
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show Grid Lines",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = showGridLines,
                    onCheckedChange = onShowGridLinesChange
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClearAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All")
                }
                
                Button(
                    onClick = onRestoreAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Restore",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore All")
                }
            }
        }
    }
}
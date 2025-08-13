package com.memoryassist.fanfanlokmapper.data.export

import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import com.memoryassist.fanfanlokmapper.data.models.DetectionResult
import com.memoryassist.fanfanlokmapper.data.models.ExportData
import com.memoryassist.fanfanlokmapper.data.models.SimpleCoordinate
import com.memoryassist.fanfanlokmapper.utils.Constants
import com.memoryassist.fanfanlokmapper.utils.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles exporting detection results to various JSON formats
 */
@Singleton
class JsonExporter @Inject constructor() {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    
    /**
     * Export detection results to JSON file
     */
    fun exportToJson(
        detectionResult: DetectionResult,
        outputFile: File,
        format: ExportFormat = ExportFormat.SIMPLE
    ): Result<File> {
        return try {
            val jsonContent = when (format) {
                ExportFormat.SIMPLE -> createSimpleJson(detectionResult)
                ExportFormat.DETAILED -> createDetailedJson(detectionResult)
                ExportFormat.COMPACT -> createCompactJson(detectionResult)
                ExportFormat.GRID -> createGridJson(detectionResult)
                ExportFormat.AUTOMATION -> createAutomationJson(detectionResult)
            }
            
            // Ensure parent directory exists
            outputFile.parentFile?.mkdirs()
            
            // Write to file
            FileWriter(outputFile).use { writer ->
                writer.write(jsonContent)
            }
            
            Logger.logExport(outputFile.name, detectionResult.validCardsCount)
            Result.success(outputFile)
            
        } catch (e: Exception) {
            Logger.error("Failed to export JSON", e)
            Result.failure(e)
        }
    }
    
    /**
     * Export to multiple formats at once
     */
    fun exportToMultipleFormats(
        detectionResult: DetectionResult,
        outputDirectory: File,
        formats: List<ExportFormat> = ExportFormat.values().toList()
    ): Map<ExportFormat, Result<File>> {
        val results = mutableMapOf<ExportFormat, Result<File>>()
        val timestamp = dateFormat.format(Date())
        
        formats.forEach { format ->
            val filename = "cards_${format.name.lowercase()}_$timestamp.json"
            val outputFile = File(outputDirectory, filename)
            results[format] = exportToJson(detectionResult, outputFile, format)
        }
        
        return results
    }
    
    /**
     * Create simple JSON with just coordinates
     */
    private fun createSimpleJson(detectionResult: DetectionResult): String {
        val simpleData = SimpleExport(
            cardPositions = detectionResult.getValidCards().map { card ->
                SimpleCoordinate(
                    id = card.id,
                    centerX = card.roundedCenterX,
                    centerY = card.roundedCenterY
                )
            }
        )
        
        return json.encodeToString(simpleData)
    }
    
    /**
     * Create detailed JSON with all card information
     */
    private fun createDetailedJson(detectionResult: DetectionResult): String {
        val detailedData = DetailedExport(
            metadata = ExportMetadata(
                totalCards = detectionResult.validCardsCount,
                imageWidth = detectionResult.imageWidth,
                imageHeight = detectionResult.imageHeight,
                processingTimeMs = detectionResult.processingTimeMs,
                averageConfidence = detectionResult.averageConfidence,
                gridCompleteness = detectionResult.gridCompleteness,
                timestamp = System.currentTimeMillis(),
                exportFormat = "detailed"
            ),
            cards = detectionResult.getValidCards().map { card ->
                DetailedCard(
                    id = card.id,
                    centerX = card.centerX,
                    centerY = card.centerY,
                    width = card.width,
                    height = card.height,
                    confidence = card.confidence,
                    gridRow = card.gridRow,
                    gridColumn = card.gridColumn,
                    bounds = CardBounds(
                        left = card.left,
                        top = card.top,
                        right = card.right,
                        bottom = card.bottom
                    )
                )
            }
        )
        
        return json.encodeToString(detailedData)
    }
    
    /**
     * Create compact JSON (minimal formatting)
     */
    private fun createCompactJson(detectionResult: DetectionResult): String {
        val compactData = CompactExport(
            cards = detectionResult.getValidCards().map { card ->
                listOf(card.roundedCenterX, card.roundedCenterY)
            }
        )
        
        // Use compact JSON format (no pretty printing)
        val compactJson = Json {
            prettyPrint = false
        }
        
        return compactJson.encodeToString(compactData)
    }
    
    /**
     * Create grid-based JSON representation
     */
    private fun createGridJson(detectionResult: DetectionResult): String {
        val grid = Array(Constants.GRID_ROWS) { 
            Array<GridCell?>(Constants.GRID_COLUMNS) { null } 
        }
        
        // Fill grid with detected cards
        detectionResult.getValidCards().forEach { card ->
            if (card.hasValidGridPosition) {
                grid[card.gridRow][card.gridColumn] = GridCell(
                    x = card.roundedCenterX,
                    y = card.roundedCenterY,
                    confidence = card.confidence
                )
            }
        }
        
        val gridData = GridExport(
            rows = Constants.GRID_ROWS,
            columns = Constants.GRID_COLUMNS,
            totalDetected = detectionResult.validCardsCount,
            grid = grid.map { row ->
                row.map { cell ->
                    cell ?: GridCell(x = -1, y = -1, confidence = 0f)
                }
            }
        )
        
        return json.encodeToString(gridData)
    }
    
    /**
     * Create JSON optimized for automation tools
     */
    private fun createAutomationJson(detectionResult: DetectionResult): String {
        val automationData = AutomationExport(
            version = "1.0",
            screenResolution = ScreenResolution(
                width = detectionResult.imageWidth,
                height = detectionResult.imageHeight
            ),
            cardGrid = CardGrid(
                rows = Constants.GRID_ROWS,
                columns = Constants.GRID_COLUMNS,
                totalCards = Constants.TOTAL_CARDS
            ),
            detectedCards = detectionResult.getValidCards().map { card ->
                AutomationCard(
                    index = card.gridRow * Constants.GRID_COLUMNS + card.gridColumn,
                    gridPosition = GridPosition(card.gridRow, card.gridColumn),
                    clickPoint = ClickPoint(
                        x = card.roundedCenterX,
                        y = card.roundedCenterY
                    ),
                    bounds = BoundingBox(
                        x = card.left.toInt(),
                        y = card.top.toInt(),
                        width = card.width.toInt(),
                        height = card.height.toInt()
                    ),
                    confidence = card.confidence
                )
            }.sortedBy { it.index },
            clickSequence = generateClickSequence(detectionResult),
            statistics = AutomationStatistics(
                detectedCount = detectionResult.validCardsCount,
                missingCount = Constants.TOTAL_CARDS - detectionResult.validCardsCount,
                averageConfidence = detectionResult.averageConfidence,
                processingTimeMs = detectionResult.processingTimeMs
            )
        )
        
        return json.encodeToString(automationData)
    }
    
    /**
     * Generate optimal click sequence for automation
     */
    private fun generateClickSequence(detectionResult: DetectionResult): List<ClickInstruction> {
        val cards = detectionResult.getValidCards()
            .sortedWith(compareBy({ it.gridRow }, { it.gridColumn }))
        
        return cards.mapIndexed { index, card ->
            ClickInstruction(
                step = index + 1,
                x = card.roundedCenterX,
                y = card.roundedCenterY,
                delayMs = 100, // Default delay between clicks
                description = "Card at row ${card.gridRow + 1}, column ${card.gridColumn + 1}"
            )
        }
    }
    
    /**
     * Create custom JSON based on template
     */
    fun createCustomJson(
        detectionResult: DetectionResult,
        template: ExportTemplate
    ): String {
        val cards = detectionResult.getValidCards()
        
        val customData = mutableMapOf<String, Any>()
        
        // Add requested fields
        if (template.includeMetadata) {
            customData["metadata"] = mapOf(
                "totalCards" to detectionResult.validCardsCount,
                "imageSize" to "${detectionResult.imageWidth}x${detectionResult.imageHeight}",
                "timestamp" to System.currentTimeMillis()
            )
        }
        
        if (template.includeCoordinates) {
            customData["coordinates"] = cards.map { card ->
                if (template.useRoundedCoordinates) {
                    mapOf("x" to card.roundedCenterX, "y" to card.roundedCenterY)
                } else {
                    mapOf("x" to card.centerX, "y" to card.centerY)
                }
            }
        }
        
        if (template.includeGridPositions) {
            customData["gridPositions"] = cards.map { card ->
                mapOf("row" to card.gridRow, "column" to card.gridColumn)
            }
        }
        
        if (template.includeConfidence) {
            customData["confidence"] = cards.map { it.confidence }
        }
        
        return json.encodeToString(customData)
    }
    
    /**
     * Validate JSON export before writing
     */
    fun validateExport(jsonContent: String): Result<Unit> {
        return try {
            // Try to parse the JSON to ensure it's valid
            json.parseToJsonElement(jsonContent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Invalid JSON format: ${e.message}"))
        }
    }
}

/**
 * Different export formats available
 */
enum class ExportFormat {
    SIMPLE,      // Just coordinates
    DETAILED,    // Full card information
    COMPACT,     // Minimal size
    GRID,        // Grid-based layout
    AUTOMATION   // Optimized for automation tools
}

/**
 * Template for custom JSON export
 */
data class ExportTemplate(
    val includeMetadata: Boolean = true,
    val includeCoordinates: Boolean = true,
    val includeGridPositions: Boolean = false,
    val includeConfidence: Boolean = false,
    val useRoundedCoordinates: Boolean = true
)

// Data classes for different export formats

@Serializable
data class SimpleExport(
    val cardPositions: List<SimpleCoordinate>
)

@Serializable
data class DetailedExport(
    val metadata: ExportMetadata,
    val cards: List<DetailedCard>
)

@Serializable
data class DetailedCard(
    val id: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
    val gridRow: Int,
    val gridColumn: Int,
    val bounds: CardBounds
)

@Serializable
data class CardBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

@Serializable
data class CompactExport(
    val cards: List<List<Int>>
)

@Serializable
data class GridExport(
    val rows: Int,
    val columns: Int,
    val totalDetected: Int,
    val grid: List<List<GridCell>>
)

@Serializable
data class GridCell(
    val x: Int,
    val y: Int,
    val confidence: Float
)

@Serializable
data class AutomationExport(
    val version: String,
    val screenResolution: ScreenResolution,
    val cardGrid: CardGrid,
    val detectedCards: List<AutomationCard>,
    val clickSequence: List<ClickInstruction>,
    val statistics: AutomationStatistics
)

@Serializable
data class ScreenResolution(
    val width: Int,
    val height: Int
)

@Serializable
data class CardGrid(
    val rows: Int,
    val columns: Int,
    val totalCards: Int
)

@Serializable
data class AutomationCard(
    val index: Int,
    val gridPosition: GridPosition,
    val clickPoint: ClickPoint,
    val bounds: BoundingBox,
    val confidence: Float
)

@Serializable
data class GridPosition(
    val row: Int,
    val column: Int
)

@Serializable
data class ClickPoint(
    val x: Int,
    val y: Int
)

@Serializable
data class BoundingBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

@Serializable
data class ClickInstruction(
    val step: Int,
    val x: Int,
    val y: Int,
    val delayMs: Int,
    val description: String
)

@Serializable
data class AutomationStatistics(
    val detectedCount: Int,
    val missingCount: Int,
    val averageConfidence: Float,
    val processingTimeMs: Long
)

@Serializable
data class ExportMetadata(
    val totalCards: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val processingTimeMs: Long,
    val averageConfidence: Float,
    val gridCompleteness: Float,
    val timestamp: Long,
    val exportFormat: String
)
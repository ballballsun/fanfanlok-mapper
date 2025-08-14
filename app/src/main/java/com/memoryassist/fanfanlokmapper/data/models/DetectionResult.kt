package com.memoryassist.fanfanlokmapper.data.models

import kotlinx.serialization.Serializable

/**
 * Comprehensive result wrapper for card detection operations
 */
@Serializable
data class DetectionResult(
    val cardPositions: List<CardPosition>,
    val processingTimeMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val isSuccessful: Boolean,
    val errorMessage: String? = null,
    val metadata: DetectionMetadata = DetectionMetadata()
) {
    // Computed properties for analysis
    val totalCardsDetected: Int get() = cardPositions.size
    val validCardsCount: Int get() = cardPositions.count { !it.isManuallyRemoved }
    val removedCardsCount: Int get() = cardPositions.count { it.isManuallyRemoved }
    val averageConfidence: Float get() = if (cardPositions.isNotEmpty()) {
        cardPositions.map { it.confidence }.average().toFloat()
    } else 0f
    
    val gridCompleteness: Float get() = validCardsCount.toFloat() / com.memoryassist.fanfanlokmapper.utils.Constants.TOTAL_CARDS
    val isGridComplete: Boolean get() = validCardsCount >= com.memoryassist.fanfanlokmapper.utils.Constants.MIN_CARDS_FOR_VALID_GRID
    
    /**
     * Get cards organized by grid position
     */
    fun getCardsByGrid(): Array<Array<CardPosition?>> {
        val grid = Array(com.memoryassist.fanfanlokmapper.utils.Constants.GRID_ROWS) { 
            Array<CardPosition?>(com.memoryassist.fanfanlokmapper.utils.Constants.GRID_COLUMNS) { null } 
        }
        
        cardPositions.filter { it.hasValidGridPosition && !it.isManuallyRemoved }
            .forEach { card ->
                grid[card.gridRow][card.gridColumn] = card
            }
        
        return grid
    }
    
    /**
     * Get only valid (non-removed) card positions
     */
    fun getValidCards(): List<CardPosition> {
        return cardPositions.filter { !it.isManuallyRemoved }
    }
    
    /**
     * Get cards sorted by confidence (highest first)
     */
    fun getCardsByConfidence(): List<CardPosition> {
        return cardPositions.sortedByDescending { it.confidence }
    }
    
    /**
     * Convert to export format for external programs
     */
    fun toExportFormat(): ExportData {
        return ExportData(
            cardPositions = getValidCards().map { it.toSimpleCoordinate() },
            metadata = ExportMetadata(
                totalCards = validCardsCount,
                gridRows = com.memoryassist.fanfanlokmapper.utils.Constants.GRID_ROWS,
                gridColumns = com.memoryassist.fanfanlokmapper.utils.Constants.GRID_COLUMNS,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                processingTimeMs = processingTimeMs,
                averageConfidence = averageConfidence,
                timestamp = System.currentTimeMillis()
            )
        )
    }
    
    /**
     * Create a copy with a card removed
     */
    fun withCardRemoved(cardId: Int): DetectionResult {
        val updatedCards = cardPositions.map { card ->
            if (card.id == cardId) card.asRemoved() else card
        }
        return copy(cardPositions = updatedCards)
    }
    
    /**
     * Create a copy with additional cards
     */
    fun withAdditionalCards(newCards: List<CardPosition>): DetectionResult {
        return copy(cardPositions = cardPositions + newCards)
    }
    
    companion object {
        /**
         * Create a successful detection result
         */
        fun success(
            cards: List<CardPosition>,
            processingTime: Long,
            imageWidth: Int,
            imageHeight: Int,
            metadata: DetectionMetadata = DetectionMetadata()
        ): DetectionResult {
            return DetectionResult(
                cardPositions = cards,
                processingTimeMs = processingTime,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                isSuccessful = true,
                metadata = metadata
            )
        }
        
        /**
         * Create a failed detection result
         */
        fun failure(
            error: String,
            processingTime: Long = 0,
            imageWidth: Int = 0,
            imageHeight: Int = 0,
            partialCards: List<CardPosition> = emptyList()
        ): DetectionResult {
            return DetectionResult(
                cardPositions = partialCards,
                processingTimeMs = processingTime,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                isSuccessful = false,
                errorMessage = error
            )
        }
    }
}

/**
 * Additional metadata about the detection process
 */
@Serializable
data class DetectionMetadata(
    val algorithmUsed: String = "Canny Edge + Contour Analysis",
    val preprocessingSteps: List<String> = emptyList(),
    val detectionParameters: Map<String, String> = emptyMap(),
    val qualityScore: Float = 0f,
    val detectedEdges: Int = 0,
    val filteredContours: Int = 0,
    val gridAnalysisScore: Float = 0f
)


package com.memoryassist.fanfanlokmapper.utils

import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import kotlin.math.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GridMapper @Inject constructor() {
    
    /**
     * Map detected cards to a 4x6 grid structure using clustering
     */
    fun mapToGrid(cardPositions: List<CardPosition>): List<CardPosition> {
        if (cardPositions.size < Constants.MIN_CARDS_FOR_VALID_GRID) {
            Logger.warning("Insufficient cards for grid mapping: ${cardPositions.size}")
            return assignGridPositionsFallback(cardPositions)
        }
        
        // Step 1: Identify rows using Y-coordinate clustering
        val rowClusters = clusterByCoordinate(
            cardPositions,
            { it.centerY },
            Constants.GRID_ROWS
        )
        
        // Step 2: Within each row, identify columns using X-coordinate clustering
        val gridMappedCards = mutableListOf<CardPosition>()
        
        rowClusters.forEachIndexed { rowIndex, rowCards ->
            val columnClusters = clusterByCoordinate(
                rowCards,
                { it.centerX },
                Constants.GRID_COLUMNS
            )
            
            columnClusters.forEachIndexed { colIndex, colCards ->
                // Assign grid position to each card in this cell
                colCards.forEach { card ->
                    gridMappedCards.add(
                        card.withGridPosition(rowIndex, colIndex)
                    )
                }
            }
        }
        
        // Validate grid completeness
        val gridAnalysis = analyzeGridCompleteness(gridMappedCards)
        Logger.logGridAnalysis(
            Constants.TOTAL_CARDS,
            gridMappedCards.size,
            gridAnalysis.isValid
        )
        
        return gridMappedCards
    }
    
    /**
     * Cluster cards by a coordinate (X or Y) using k-means clustering
     */
    private fun clusterByCoordinate(
        cards: List<CardPosition>,
        coordinateSelector: (CardPosition) -> Float,
        numClusters: Int
    ): List<List<CardPosition>> {
        if (cards.isEmpty()) return emptyList()
        
        // Sort cards by the selected coordinate
        val sortedCards = cards.sortedBy(coordinateSelector)
        
        // If we have fewer cards than clusters, return what we have
        if (cards.size <= numClusters) {
            return cards.map { listOf(it) }
        }
        
        // Use adaptive clustering based on gaps
        val clusters = adaptiveCluster(sortedCards, coordinateSelector, numClusters)
        
        // If adaptive clustering didn't produce enough clusters, use k-means
        if (clusters.size != numClusters) {
            return kMeansCluster(cards, coordinateSelector, numClusters)
        }
        
        return clusters
    }
    
    /**
     * Adaptive clustering based on natural gaps in coordinates
     */
    private fun adaptiveCluster(
        sortedCards: List<CardPosition>,
        coordinateSelector: (CardPosition) -> Float,
        targetClusters: Int
    ): List<List<CardPosition>> {
        val clusters = mutableListOf<MutableList<CardPosition>>()
        clusters.add(mutableListOf(sortedCards[0]))
        
        // Calculate median gap for threshold
        val gaps = mutableListOf<Float>()
        for (i in 1 until sortedCards.size) {
            val gap = coordinateSelector(sortedCards[i]) - coordinateSelector(sortedCards[i - 1])
            gaps.add(gap)
        }
        
        val medianGap = gaps.sorted()[gaps.size / 2]
        val gapThreshold = medianGap * 2.5 // Gaps 2.5x larger than median indicate cluster boundaries
        
        // Group cards into clusters based on gaps
        for (i in 1 until sortedCards.size) {
            val gap = coordinateSelector(sortedCards[i]) - coordinateSelector(sortedCards[i - 1])
            
            if (gap > gapThreshold && clusters.size < targetClusters) {
                // Start new cluster
                clusters.add(mutableListOf())
            }
            
            clusters.last().add(sortedCards[i])
        }
        
        return clusters
    }
    
    /**
     * K-means clustering for coordinate-based grouping
     */
    private fun kMeansCluster(
        cards: List<CardPosition>,
        coordinateSelector: (CardPosition) -> Float,
        k: Int
    ): List<List<CardPosition>> {
        if (cards.size <= k) {
            return cards.map { listOf(it) }
        }
        
        // Initialize centroids evenly distributed
        val sortedCards = cards.sortedBy(coordinateSelector)
        val centroids = MutableList(k) { i ->
            coordinateSelector(sortedCards[i * sortedCards.size / k])
        }
        
        var clusters: List<MutableList<CardPosition>>
        var previousCentroids: List<Float>
        var iterations = 0
        val maxIterations = 50
        
        do {
            previousCentroids = centroids.toList()
            
            // Assign cards to nearest centroid
            clusters = List(k) { mutableListOf() }
            for (card in cards) {
                val coord = coordinateSelector(card)
                val nearestCentroidIndex = centroids.indices.minByOrNull { i ->
                    abs(coord - centroids[i])
                } ?: 0
                clusters[nearestCentroidIndex].add(card)
            }
            
            // Update centroids
            for (i in clusters.indices) {
                if (clusters[i].isNotEmpty()) {
                    centroids[i] = clusters[i].map(coordinateSelector).average().toFloat()
                }
            }
            
            iterations++
        } while (centroids != previousCentroids && iterations < maxIterations)
        
        // Sort clusters by centroid position
        return clusters.sortedBy { cluster ->
            if (cluster.isNotEmpty()) coordinateSelector(cluster[0]) else Float.MAX_VALUE
        }
    }
    
    /**
     * Fallback grid assignment based on relative positions
     */
    private fun assignGridPositionsFallback(cards: List<CardPosition>): List<CardPosition> {
        if (cards.isEmpty()) return emptyList()
        
        // Sort by Y then X to assign in reading order
        val sorted = cards.sortedWith(compareBy({ it.centerY }, { it.centerX }))
        
        val result = mutableListOf<CardPosition>()
        var currentRow = 0
        var currentCol = 0
        
        var lastY = sorted[0].centerY
        val averageCardHeight = cards.map { it.height }.average().toFloat()
        
        for (card in sorted) {
            // Check if we've moved to a new row
            if (card.centerY - lastY > averageCardHeight * 0.7) {
                currentRow++
                currentCol = 0
                lastY = card.centerY
            }
            
            // Ensure we don't exceed grid bounds
            if (currentRow >= Constants.GRID_ROWS) {
                currentRow = Constants.GRID_ROWS - 1
            }
            if (currentCol >= Constants.GRID_COLUMNS) {
                currentCol = Constants.GRID_COLUMNS - 1
            }
            
            result.add(card.withGridPosition(currentRow, currentCol))
            currentCol++
        }
        
        return result
    }
    
    /**
     * Analyze grid completeness and validity
     */
    fun analyzeGridCompleteness(mappedCards: List<CardPosition>): GridAnalysis {
        val gridOccupancy = Array(Constants.GRID_ROWS) { 
            BooleanArray(Constants.GRID_COLUMNS) { false } 
        }
        
        var validPositions = 0
        val duplicatePositions = mutableListOf<Pair<Int, Int>>()
        
        for (card in mappedCards) {
            if (card.hasValidGridPosition) {
                if (gridOccupancy[card.gridRow][card.gridColumn]) {
                    duplicatePositions.add(Pair(card.gridRow, card.gridColumn))
                } else {
                    gridOccupancy[card.gridRow][card.gridColumn] = true
                    validPositions++
                }
            }
        }
        
        val completeness = validPositions.toFloat() / Constants.TOTAL_CARDS
        val rowCoverage = gridOccupancy.count { row -> row.any { it } }.toFloat() / Constants.GRID_ROWS
        val colCoverage = (0 until Constants.GRID_COLUMNS).count { col ->
            gridOccupancy.any { row -> row[col] }
        }.toFloat() / Constants.GRID_COLUMNS
        
        return GridAnalysis(
            totalCards = mappedCards.size,
            validPositions = validPositions,
            completeness = completeness,
            rowCoverage = rowCoverage,
            columnCoverage = colCoverage,
            duplicatePositions = duplicatePositions,
            isValid = validPositions >= Constants.MIN_CARDS_FOR_VALID_GRID && 
                     duplicatePositions.isEmpty()
        )
    }
    
    /**
     * Refine grid positions using neighbor relationships
     */
    fun refineGridPositions(cards: List<CardPosition>): List<CardPosition> {
        if (cards.size < 2) return cards
        
        // Calculate average spacing
        val horizontalSpacing = calculateAverageSpacing(cards) { it.centerX }
        val verticalSpacing = calculateAverageSpacing(cards) { it.centerY }
        
        Logger.debug("Average spacing - H: $horizontalSpacing, V: $verticalSpacing")
        
        // Find anchor point (top-left card)
        val topLeft = cards.minByOrNull { it.centerY + it.centerX } ?: return cards
        
        // Reassign grid positions based on expected positions
        return cards.map { card ->
            val expectedCol = ((card.centerX - topLeft.centerX) / horizontalSpacing).roundToInt()
            val expectedRow = ((card.centerY - topLeft.centerY) / verticalSpacing).roundToInt()
            
            val refinedRow = expectedRow.coerceIn(0, Constants.GRID_ROWS - 1)
            val refinedCol = expectedCol.coerceIn(0, Constants.GRID_COLUMNS - 1)
            
            if (refinedRow != card.gridRow || refinedCol != card.gridColumn) {
                Logger.debug("Refined position for card ${card.id}: (${card.gridRow},${card.gridColumn}) -> ($refinedRow,$refinedCol)")
            }
            
            card.withGridPosition(refinedRow, refinedCol)
        }
    }
    
    /**
     * Calculate average spacing between cards in one dimension
     */
    private fun calculateAverageSpacing(
        cards: List<CardPosition>,
        coordinateSelector: (CardPosition) -> Float
    ): Float {
        val sorted = cards.sortedBy(coordinateSelector)
        val spacings = mutableListOf<Float>()
        
        for (i in 1 until sorted.size) {
            val spacing = coordinateSelector(sorted[i]) - coordinateSelector(sorted[i - 1])
            // Only consider reasonable spacings (filter out outliers)
            if (spacing > 20) {
                spacings.add(spacing)
            }
        }
        
        return if (spacings.isNotEmpty()) {
            // Use median to be robust against outliers
            spacings.sorted()[spacings.size / 2]
        } else {
            100f // Default spacing if calculation fails
        }
    }
    
    /**
     * Alternative grid mapping using expected positions
     */
    fun mapToGridUsingExpectedPositions(cards: List<CardPosition>): List<CardPosition> {
        if (cards.isEmpty()) return emptyList()
        
        // Find bounds of all cards
        val minX = cards.minOf { it.centerX }
        val maxX = cards.maxOf { it.centerX }
        val minY = cards.minOf { it.centerY }
        val maxY = cards.maxOf { it.centerY }
        
        // Calculate expected cell dimensions
        val cellWidth = (maxX - minX) / (Constants.GRID_COLUMNS - 1)
        val cellHeight = (maxY - minY) / (Constants.GRID_ROWS - 1)
        
        // Map each card to nearest grid position
        return cards.map { card ->
            val col = ((card.centerX - minX) / cellWidth).roundToInt()
                .coerceIn(0, Constants.GRID_COLUMNS - 1)
            val row = ((card.centerY - minY) / cellHeight).roundToInt()
                .coerceIn(0, Constants.GRID_ROWS - 1)
            
            card.withGridPosition(row, col)
        }
    }
    
    /**
     * Validate and fix grid assignments
     */
    fun validateAndFixGrid(cards: List<CardPosition>): List<CardPosition> {
        val gridMap = mutableMapOf<Pair<Int, Int>, MutableList<CardPosition>>()
        
        // Group cards by their grid position
        cards.forEach { card ->
            if (card.hasValidGridPosition) {
                val key = Pair(card.gridRow, card.gridColumn)
                gridMap.getOrPut(key) { mutableListOf() }.add(card)
            }
        }
        
        val fixedCards = mutableListOf<CardPosition>()
        
        // Handle duplicates - keep highest confidence
        gridMap.forEach { (position, cardsAtPosition) ->
            if (cardsAtPosition.size == 1) {
                fixedCards.add(cardsAtPosition[0])
            } else {
                // Multiple cards at same position - keep best one
                val best = cardsAtPosition.maxByOrNull { it.confidence }
                if (best != null) {
                    fixedCards.add(best)
                    
                    // Try to reassign others to nearby empty positions
                    val others = cardsAtPosition.filter { it != best }
                    others.forEach { card ->
                        val newPosition = findNearestEmptyPosition(
                            position.first, 
                            position.second, 
                            gridMap.keys
                        )
                        if (newPosition != null) {
                            fixedCards.add(
                                card.withGridPosition(newPosition.first, newPosition.second)
                            )
                        }
                    }
                }
            }
        }
        
        return fixedCards
    }
    
    /**
     * Find nearest empty grid position
     */
    private fun findNearestEmptyPosition(
        row: Int, 
        col: Int, 
        occupiedPositions: Set<Pair<Int, Int>>
    ): Pair<Int, Int>? {
        // Search in expanding radius
        for (radius in 1..3) {
            for (dr in -radius..radius) {
                for (dc in -radius..radius) {
                    if (abs(dr) == radius || abs(dc) == radius) {
                        val newRow = row + dr
                        val newCol = col + dc
                        
                        if (newRow in 0 until Constants.GRID_ROWS &&
                            newCol in 0 until Constants.GRID_COLUMNS &&
                            !occupiedPositions.contains(Pair(newRow, newCol))) {
                            return Pair(newRow, newCol)
                        }
                    }
                }
            }
        }
        return null
    }
    
    data class GridAnalysis(
        val totalCards: Int,
        val validPositions: Int,
        val completeness: Float,
        val rowCoverage: Float,
        val columnCoverage: Float,
        val duplicatePositions: List<Pair<Int, Int>>,
        val isValid: Boolean
    )
}
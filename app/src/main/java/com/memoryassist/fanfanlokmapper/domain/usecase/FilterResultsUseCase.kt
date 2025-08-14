package com.memoryassist.fanfanlokmapper.domain.usecase

import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import com.memoryassist.fanfanlokmapper.utils.Logger
import com.memoryassist.fanfanlokmapper.utils.ImageProcessor
import kotlin.math.abs

class FilterResultsUseCase {
    
    /**
     * Filter detection results based on size thresholds and quality criteria
     */
    fun execute(
        detectedCards: List<CardPosition>,
        thresholds: ImageProcessor.ThresholdParams,
        imageWidth: Int,
        imageHeight: Int
    ): FilterResult {
        Logger.info("Starting filtering: ${detectedCards.size} initial detections")
        
        val filterSteps = mutableListOf<FilterStep>()
        var currentCards = detectedCards
        
        // Step 1: Filter by size
        val sizeFiltered = filterBySize(currentCards, thresholds)
        filterSteps.add(
            FilterStep(
                name = "Size Filter",
                cardsRemaining = sizeFiltered.size,
                cardsRemoved = currentCards.size - sizeFiltered.size
            )
        )
        currentCards = sizeFiltered
        
        // Step 2: Filter by aspect ratio
        val aspectFiltered = filterByAspectRatio(currentCards, thresholds)
        filterSteps.add(
            FilterStep(
                name = "Aspect Ratio Filter",
                cardsRemaining = aspectFiltered.size,
                cardsRemoved = currentCards.size - aspectFiltered.size
            )
        )
        currentCards = aspectFiltered
        
        // Step 3: Filter by image bounds
        val boundsFiltered = filterByImageBounds(currentCards, imageWidth, imageHeight)
        filterSteps.add(
            FilterStep(
                name = "Bounds Filter",
                cardsRemaining = boundsFiltered.size,
                cardsRemoved = currentCards.size - boundsFiltered.size
            )
        )
        currentCards = boundsFiltered
        
        // Step 4: Filter outliers by position
        val outlierFiltered = filterPositionalOutliers(currentCards)
        filterSteps.add(
            FilterStep(
                name = "Outlier Filter",
                cardsRemaining = outlierFiltered.size,
                cardsRemoved = currentCards.size - outlierFiltered.size
            )
        )
        currentCards = outlierFiltered
        
        // Step 5: Filter by confidence threshold
        val confidenceFiltered = filterByConfidence(currentCards, minConfidence = 0.5f)
        filterSteps.add(
            FilterStep(
                name = "Confidence Filter",
                cardsRemaining = confidenceFiltered.size,
                cardsRemoved = currentCards.size - confidenceFiltered.size
            )
        )
        currentCards = confidenceFiltered
        
        // Step 6: Remove duplicate/overlapping detections
        val duplicatesRemoved = removeDuplicates(currentCards)
        filterSteps.add(
            FilterStep(
                name = "Duplicate Removal",
                cardsRemaining = duplicatesRemoved.size,
                cardsRemoved = currentCards.size - duplicatesRemoved.size
            )
        )
        
        // Log filtering summary
        val totalRemoved = detectedCards.size - duplicatesRemoved.size
        Logger.info("Filtering complete: ${duplicatesRemoved.size} cards remaining (removed $totalRemoved)")
        
        return FilterResult(
            filteredCards = duplicatesRemoved,
            originalCount = detectedCards.size,
            filterSteps = filterSteps,
            isValid = duplicatesRemoved.size >= com.memoryassist.fanfanlokmapper.utils.Constants.MIN_CARDS_FOR_VALID_GRID
        )
    }
    
    /**
     * Filter cards by size constraints
     */
    private fun filterBySize(
        cards: List<CardPosition>,
        thresholds: ImageProcessor.ThresholdParams
    ): List<CardPosition> {
        return cards.filter { card ->
            val area = card.area
            val meetsAreaConstraints = area >= thresholds.minArea && area <= thresholds.maxArea
            val meetsSizeConstraints = card.width >= thresholds.minWidth && 
                                      card.height >= thresholds.minHeight
            
            if (!meetsAreaConstraints || !meetsSizeConstraints) {
                Logger.debug("Card ${card.id} filtered by size: area=$area, w=${card.width}, h=${card.height}")
            }
            
            meetsAreaConstraints && meetsSizeConstraints
        }
    }
    
    /**
     * Filter cards by aspect ratio
     */
    private fun filterByAspectRatio(
        cards: List<CardPosition>,
        thresholds: ImageProcessor.ThresholdParams
    ): List<CardPosition> {
        return cards.filter { card ->
            val aspectRatio = card.aspectRatio
            val isValid = aspectRatio >= thresholds.aspectRatioMin && 
                         aspectRatio <= thresholds.aspectRatioMax
            
            if (!isValid) {
                Logger.debug("Card ${card.id} filtered by aspect ratio: $aspectRatio")
            }
            
            isValid
        }
    }
    
    /**
     * Filter cards outside image boundaries
     */
    private fun filterByImageBounds(
        cards: List<CardPosition>,
        imageWidth: Int,
        imageHeight: Int
    ): List<CardPosition> {
        val margin = 5 // Allow small margin for edge cards
        
        return cards.filter { card ->
            val isInBounds = card.left >= -margin &&
                           card.top >= -margin &&
                           card.right <= imageWidth + margin &&
                           card.bottom <= imageHeight + margin
            
            if (!isInBounds) {
                Logger.debug("Card ${card.id} filtered by bounds: position (${card.centerX}, ${card.centerY})")
            }
            
            isInBounds
        }
    }
    
    /**
     * Filter positional outliers using statistical methods
     */
    private fun filterPositionalOutliers(cards: List<CardPosition>): List<CardPosition> {
        if (cards.size < 10) return cards // Not enough cards for statistical analysis
        
        // Calculate statistics for card sizes
        val widths = cards.map { it.width }
        val heights = cards.map { it.height }
        
        val medianWidth = widths.sorted()[widths.size / 2]
        val medianHeight = heights.sorted()[heights.size / 2]
        
        // Calculate MAD (Median Absolute Deviation)
        val widthDeviations = widths.map { kotlin.math.abs(it - medianWidth) }
        val heightDeviations = heights.map { kotlin.math.abs(it - medianHeight) }
        
        val madWidth = widthDeviations.sorted()[widthDeviations.size / 2]
        val madHeight = heightDeviations.sorted()[heightDeviations.size / 2]
        
        // Filter outliers (more than 3 MADs from median)
        val threshold = 3.0f
        
        return cards.filter { card ->
            val widthZScore = if (madWidth > 0) {
                kotlin.math.abs(card.width - medianWidth) / madWidth
            } else 0.0f
            
            val heightZScore = if (madHeight > 0) {
                kotlin.math.abs(card.height - medianHeight) / madHeight
            } else 0.0f
            
            val isOutlier = widthZScore > threshold || heightZScore > threshold
            
            if (isOutlier) {
                Logger.debug("Card ${card.id} identified as size outlier")
            }
            
            !isOutlier
        }
    }
    
    /**
     * Filter cards by confidence score
     */
    private fun filterByConfidence(
        cards: List<CardPosition>,
        minConfidence: Float
    ): List<CardPosition> {
        return cards.filter { card ->
            val meetsThreshold = card.confidence >= minConfidence
            
            if (!meetsThreshold) {
                Logger.debug("Card ${card.id} filtered by confidence: ${card.confidence}")
            }
            
            meetsThreshold
        }
    }
    
    /**
     * Remove duplicate detections of the same card
     */
    private fun removeDuplicates(cards: List<CardPosition>): List<CardPosition> {
        if (cards.isEmpty()) return emptyList()
        
        val uniqueCards = mutableListOf<CardPosition>()
        val sortedCards = cards.sortedByDescending { it.confidence }
        
        for (card in sortedCards) {
            var isDuplicate = false
            
            for (existing in uniqueCards) {
                // Check if cards are too close (likely duplicates)
                val distance = card.distanceTo(existing)
                val avgSize = (card.width + card.height + existing.width + existing.height) / 4
                
                // If distance is less than 30% of average card size, consider duplicate
                if (distance < avgSize * 0.3) {
                    isDuplicate = true
                    Logger.debug("Card ${card.id} is duplicate of ${existing.id} (distance: $distance)")
                    break
                }
            }
            
            if (!isDuplicate) {
                uniqueCards.add(card)
            }
        }
        
        return uniqueCards
    }
    
    /**
     * Apply adaptive filtering based on detection count
     */
    fun applyAdaptiveFiltering(
        cards: List<CardPosition>,
        targetCount: Int = com.memoryassist.fanfanlokmapper.utils.Constants.TOTAL_CARDS
    ): List<CardPosition> {
        // If we have too many cards, increase filtering strictness
        if (cards.size > targetCount * 1.5) {
            Logger.info("Applying strict filtering: ${cards.size} cards detected")
            
            // Sort by confidence and take top candidates
            val sortedByConfidence = cards.sortedByDescending { it.confidence }
            val strictFiltered = sortedByConfidence.take(targetCount + 4) // Allow small buffer
            
            // Additional clustering to ensure spatial distribution
            return ensureSpatialDistribution(strictFiltered, targetCount)
        }
        
        // If we have too few cards, relax filtering
        if (cards.size < targetCount * 0.7) {
            Logger.info("Detection count low (${cards.size}), keeping all valid detections")
            return cards
        }
        
        return cards
    }
    
    /**
     * Ensure cards are spatially distributed (not all clustered in one area)
     */
    private fun ensureSpatialDistribution(
        cards: List<CardPosition>,
        targetCount: Int
    ): List<CardPosition> {
        if (cards.size <= targetCount) return cards
        
        // Divide image into quadrants and ensure distribution
        val quadrants = Array(2) { Array(2) { mutableListOf<CardPosition>() } }
        
        val midX = cards.map { it.centerX }.average().toFloat()
        val midY = cards.map { it.centerY }.average().toFloat()
        
        for (card in cards) {
            val qx = if (card.centerX < midX) 0 else 1
            val qy = if (card.centerY < midY) 0 else 1
            quadrants[qy][qx].add(card)
        }
        
        // Take proportional cards from each quadrant
        val cardsPerQuadrant = targetCount / 4
        val distributed = mutableListOf<CardPosition>()
        
        for (row in quadrants) {
            for (quadrant in row) {
                val sorted = quadrant.sortedByDescending { it.confidence }
                distributed.addAll(sorted.take(cardsPerQuadrant + 2))
            }
        }
        
        return distributed.sortedByDescending { it.confidence }.take(targetCount)
    }
    
    data class FilterResult(
        val filteredCards: List<CardPosition>,
        val originalCount: Int,
        val filterSteps: List<FilterStep>,
        val isValid: Boolean
    ) {
        val totalRemoved: Int get() = originalCount - filteredCards.size
        val removalPercentage: Float get() = if (originalCount > 0) {
            (totalRemoved.toFloat() / originalCount) * 100
        } else 0f
    }
    
    data class FilterStep(
        val name: String,
        val cardsRemaining: Int,
        val cardsRemoved: Int
    )
}
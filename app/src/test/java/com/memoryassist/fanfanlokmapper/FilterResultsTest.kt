package com.memoryassist.fanfanlokmapper

import com.memoryassist.fanfanlokmapper.domain.usecase.FilterResultsUseCase
import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import com.memoryassist.fanfanlokmapper.utils.ImageProcessor
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class FilterResultsTest {

    private lateinit var filterResultsUseCase: FilterResultsUseCase

    @Before
    fun setup() {
        filterResultsUseCase = FilterResultsUseCase()
    }

    @Test
    fun `execute should return empty list for empty input`() {
        // Given
        val emptyList = emptyList<CardPosition>()
        val thresholds = createTestThresholds()

        // When
        val result = filterResultsUseCase.execute(emptyList, thresholds, 800, 600)

        // Then
        assertTrue("Should return empty filtered list", result.filteredCards.isEmpty())
        assertEquals("Original count should be 0", 0, result.originalCount)
        assertFalse("Should not be valid with no cards", result.isValid)
    }

    @Test
    fun `execute should filter cards by minimum size`() {
        // Given
        val cards = listOf(
            createCard(0, 100f, 100f, width = 20f, height = 30f), // Too small
            createCard(1, 200f, 100f, width = 60f, height = 80f), // Valid
            createCard(2, 300f, 100f, width = 10f, height = 15f)  // Too small
        )
        val thresholds = createTestThresholds(minArea = 1000, minWidth = 30, minHeight = 40)

        // When
        val result = filterResultsUseCase.execute(cards, thresholds, 800, 600)

        // Then
        assertEquals("Should keep only 1 valid card", 1, result.filteredCards.size)
        assertEquals("Should keep card with id 1", 1, result.filteredCards[0].id)
        assertEquals("Should track original count", 3, result.originalCount)
    }

    @Test
    fun `execute should filter cards by maximum size`() {
        // Given
        val cards = listOf(
            createCard(0, 100f, 100f, width = 60f, height = 80f),   // Valid
            createCard(1, 200f, 100f, width = 200f, height = 300f), // Too large
            createCard(2, 300f, 100f, width = 65f, height = 85f)    // Valid
        )
        val thresholds = createTestThresholds(maxArea = 10000)

        // When
        val result = filterResultsUseCase.execute(cards, thresholds, 800, 600)

        // Then
        assertEquals("Should keep 2 valid cards", 2, result.filteredCards.size)
        assertFalse("Should remove large card", result.filteredCards.any { it.id == 1 })
    }

    @Test
    fun `execute should filter cards by aspect ratio`() {
        // Given
        val cards = listOf(
            createCard(0, 100f, 100f, width = 60f, height = 80f),  // Valid ratio ~0.75
            createCard(1, 200f, 100f, width = 100f, height = 100f), // Square ratio 1.0 (invalid)
            createCard(2, 300f, 100f, width = 30f, height = 120f)   // Extreme ratio 0.25 (invalid)
        )
        val thresholds = createTestThresholds(aspectRatioMin = 0.6, aspectRatioMax = 0.8)

        // When
        val result = filterResultsUseCase.execute(cards, thresholds, 800, 600)

        // Then
        assertEquals("Should keep only card with valid aspect ratio", 1, result.filteredCards.size)
        assertEquals("Should keep card with id 0", 0, result.filteredCards[0].id)
    }

    @Test
    fun `execute should filter cards outside image bounds`() {
        // Given
        val cards = listOf(
            createCard(0, 400f, 300f), // Valid - center of image
            createCard(1, -10f, 100f), // Invalid - negative X
            createCard(2, 100f, -10f), // Invalid - negative Y
            createCard(3, 850f, 300f), // Invalid - beyond width
            createCard(4, 400f, 650f)  // Invalid - beyond height
        )
        val thresholds = createTestThresholds()

        // When
        val result = filterResultsUseCase.execute(cards, thresholds, 800, 600)

        // Then
        assertEquals("Should keep only card within bounds", 1, result.filteredCards.size)
        assertEquals("Should keep card with id 0", 0, result.filteredCards[0].id)
    }

    @Test
    fun `execute should filter positional outliers`() {
        // Given - Most cards are similar size, one is an outlier
        val normalCards = (0..15).map { i ->
            createCard(i, 100f + i * 50f, 100f + (i / 4) * 100f, width = 60f, height = 80f)
        }
        val outlierCard = createCard(16, 500f, 300f, width = 200f, height = 250f)
        val cards = normalCards + outlierCard
        val thresholds = createTestThresholds()

        // When
        val result = filterResultsUseCase.execute(cards, thresholds, 1000, 800)

        // Then
        assertTrue("Should remove outlier", result.filteredCards.size < cards.size)
        assertFalse("Should not contain outlier card", result.filteredCards.any { it.id == 16 })
    }

    @Test
    fun `execute should filter cards by confidence`() {
        // Given
        val cards = listOf(
            createCard(0, 100f, 100f).withConfidence(0.9f),  // High confidence
            createCard(1, 200f, 100f).withConfidence(0.6f),  // Medium confidence
            createCard(2, 300f, 100f).withConfidence(0.3f),  // Low confidence
            createCard(3, 400f, 100f).withConfidence(0.4f)   // Low confidence
        )
        val thresholds = createTestThresholds()

        // When
        val result = filterResultsUseCase.execute(cards, thresholds, 800, 600)

        // Then
        assertEquals("Should keep cards with confidence >= 0.5", 2, result.filteredCards.size)
        assertTrue("Should keep high confidence cards",
            result.filteredCards.all { it.confidence >= 0.5f })
    }

    @Test
    fun `execute should remove duplicate detections`() {
        // Given - Cards that are very close together (likely duplicates)
        val cards = listOf(
            createCard(0, 100f, 100f).withConfidence(0.9f),
            createCard(1, 105f, 105f).withConfidence(0.7f), // Duplicate of card 0
            createCard(2, 200f, 100f).withConfidence(0.8f),
            createCard(3, 202f, 102f).withConfidence(0.6f)  // Duplicate of card 2
        )
        val thresholds = createTestThresholds()

        // When
        val result = filterResultsUseCase.execute(cards, thresholds, 800, 600)

        // Then
        assertEquals("Should keep only 2 unique cards", 2, result.filteredCards.size)
        // Should keep higher confidence cards
        assertTrue("Should keep card 0", result.filteredCards.any { it.id == 0 })
        assertTrue("Should keep card 2", result.filteredCards.any { it.id == 2 })
    }

    @Test
    fun `execute should track filtering steps`() {
        // Given
        val cards = createMixedQualityCards()
        val thresholds = createTestThresholds()

        // When
        val result = filterResultsUseCase.execute(cards, thresholds, 800, 600)

        // Then
        assertTrue("Should have multiple filter steps", result.filterSteps.isNotEmpty())

        // Verify expected filter steps
        val stepNames = result.filterSteps.map { it.name }
        assertTrue("Should have Size Filter", stepNames.contains("Size Filter"))
        assertTrue("Should have Aspect Ratio Filter", stepNames.contains("Aspect Ratio Filter"))
        assertTrue("Should have Bounds Filter", stepNames.contains("Bounds Filter"))
        assertTrue("Should have Outlier Filter", stepNames.contains("Outlier Filter"))
        assertTrue("Should have Confidence Filter", stepNames.contains("Confidence Filter"))
        assertTrue("Should have Duplicate Removal", stepNames.contains("Duplicate Removal"))
    }

    @Test
    fun `execute should validate grid completeness`() {
        // Given - Enough cards for a valid grid
        val cards = (0..22).map { i ->
            createCard(i, 100f + (i % 6) * 100f, 100f + (i / 6) * 120f)
        }
        val thresholds = createTestThresholds()

        // When
        val result = filterResultsUseCase.execute(cards, thresholds, 800, 600)

        // Then
        assertTrue("Should be valid with enough cards", result.isValid)
        assertTrue("Should have at least minimum cards",
            result.filteredCards.size >= com.memoryassist.fanfanlokmapper.utils.Constants.MIN_CARDS_FOR_VALID_GRID)
    }

    @Test
    fun `applyAdaptiveFiltering should reduce cards when too many detected`() {
        // Given - Too many cards detected
        val tooManyCards = (0..40).map { i ->
            createCard(i, 50f + i * 15f, 50f + (i / 8) * 80f)
                .withConfidence(0.5f + (i % 10) * 0.05f)
        }

        // When
        val result = filterResultsUseCase.applyAdaptiveFiltering(tooManyCards, 24)

        // Then
        assertTrue("Should reduce card count", result.size <= 28) // Allow small buffer
        // Should keep highest confidence cards
        val minConfidence = result.minOfOrNull { it.confidence } ?: 0f
        val excludedCards = tooManyCards.filter { it !in result }
        assertTrue("Should exclude lower confidence cards",
            excludedCards.all { it.confidence <= minConfidence + 0.1f })
    }

    @Test
    fun `applyAdaptiveFiltering should keep all cards when count is reasonable`() {
        // Given - Reasonable number of cards
        val reasonableCards = (0..21).map { i ->
            createCard(i, 100f + (i % 6) * 100f, 100f + (i / 6) * 120f)
        }

        // When
        val result = filterResultsUseCase.applyAdaptiveFiltering(reasonableCards, 24)

        // Then
        assertEquals("Should keep all cards when count is reasonable",
            reasonableCards.size, result.size)
    }

    @Test
    fun `applyAdaptiveFiltering should ensure spatial distribution`() {
        // Given - Many cards clustered in one area
        val clusteredCards = (0..35).map { i ->
            // Most cards in top-left quadrant
            val x = if (i < 30) 50f + (i % 6) * 30f else 400f + (i % 3) * 50f
            val y = if (i < 30) 50f + (i / 6) * 30f else 300f + (i / 3) * 50f
            createCard(i, x, y).withConfidence(0.7f + (i % 10) * 0.02f)
        }

        // When
        val result = filterResultsUseCase.applyAdaptiveFiltering(clusteredCards, 24)

        // Then
        // Should have cards from different areas
        val leftCards = result.count { it.centerX < 250f }
        val rightCards = result.count { it.centerX >= 250f }

        assertTrue("Should have cards from left side", leftCards > 0)
        assertTrue("Should have cards from right side", rightCards > 0)
        assertTrue("Should not be too imbalanced",
            leftCards.toFloat() / result.size < 0.9f)
    }

    @Test
    fun `execute should calculate removal percentage`() {
        // Given
        val cards = (0..9).map { i ->
            createCard(i, 100f + i * 50f, 100f)
        }
        val thresholds = createTestThresholds(minArea = 3000) // Will filter some cards

        // When
        val result = filterResultsUseCase.execute(cards, thresholds, 800, 600)

        // Then
        val expectedPercentage = ((10 - result.filteredCards.size).toFloat() / 10) * 100
        assertEquals("Should calculate correct removal percentage",
            expectedPercentage, result.removalPercentage, 0.01f)
    }

    @Test
    fun `execute should handle edge case with all cards filtered`() {
        // Given - All cards are invalid
        val invalidCards = listOf(
            createCard(0, -100f, -100f, width = 5f, height = 5f),
            createCard(1, 1000f, 1000f, width = 3f, height = 3f)
        )
        val thresholds = createTestThresholds()

        // When
        val result = filterResultsUseCase.execute(invalidCards, thresholds, 800, 600)

        // Then
        assertTrue("Should filter all invalid cards", result.filteredCards.isEmpty())
        assertEquals("Should track original count", 2, result.originalCount)
        assertEquals("Total removed should equal original count", 2, result.totalRemoved)
        assertFalse("Should not be valid with no cards", result.isValid)
        assertEquals("Removal percentage should be 100%", 100f, result.removalPercentage, 0.01f)
    }

    // Helper methods for creating test data

    private fun createTestThresholds(
        minArea: Int = 100,
        maxArea: Int = 50000,
        minWidth: Int = 10,
        minHeight: Int = 10,
        aspectRatioMin: Double = 0.5,
        aspectRatioMax: Double = 2.0
    ): ImageProcessor.ThresholdParams {
        return ImageProcessor.ThresholdParams(
            minArea = minArea,
            maxArea = maxArea,
            minWidth = minWidth,
            minHeight = minHeight,
            aspectRatioMin = aspectRatioMin,
            aspectRatioMax = aspectRatioMax
        )
    }

    private fun createCard(
        id: Int,
        x: Float,
        y: Float,
        width: Float = 60f,
        height: Float = 80f
    ): CardPosition {
        return CardPosition(
            id = id,
            centerX = x,
            centerY = y,
            width = width,
            height = height,
            confidence = 0.95f
        )
    }

    private fun createMixedQualityCards(): List<CardPosition> {
        return listOf(
            // Good cards
            createCard(0, 100f, 100f).withConfidence(0.9f),
            createCard(1, 200f, 100f).withConfidence(0.85f),
            createCard(2, 300f, 100f).withConfidence(0.8f),

            // Too small
            createCard(3, 400f, 100f, width = 5f, height = 5f).withConfidence(0.7f),

            // Too large
            createCard(4, 100f, 200f, width = 300f, height = 400f).withConfidence(0.6f),

            // Bad aspect ratio
            createCard(5, 200f, 200f, width = 100f, height = 100f).withConfidence(0.75f),

            // Out of bounds
            createCard(6, -50f, 200f).withConfidence(0.8f),

            // Low confidence
            createCard(7, 300f, 200f).withConfidence(0.3f),

            // Duplicate
            createCard(8, 102f, 102f).withConfidence(0.85f),

            // Good cards
            createCard(9, 400f, 200f).withConfidence(0.9f),
            createCard(10, 100f, 300f).withConfidence(0.88f),
            createCard(11, 200f, 300f).withConfidence(0.82f),
            createCard(12, 300f, 300f).withConfidence(0.79f)
        )
    }
}
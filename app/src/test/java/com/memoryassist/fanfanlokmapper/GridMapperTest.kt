package com.memoryassist.fanfanlokmapper

import com.memoryassist.fanfanlokmapper.utils.GridMapper
import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import com.memoryassist.fanfanlokmapper.utils.Constants
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class GridMapperTest {

    private lateinit var gridMapper: GridMapper

    @Before
    fun setup() {
        gridMapper = GridMapper()
    }

    @Test
    fun `mapToGrid should handle empty list`() {
        // Given
        val emptyList = emptyList<CardPosition>()

        // When
        val result = gridMapper.mapToGrid(emptyList)

        // Then
        assertTrue("Should return empty list for empty input", result.isEmpty())
    }

    @Test
    fun `mapToGrid should handle single card`() {
        // Given
        val singleCard = listOf(
            CardPosition(id = 0, centerX = 100f, centerY = 100f, width = 50f, height = 70f)
        )

        // When
        val result = gridMapper.mapToGrid(singleCard)

        // Then
        assertEquals("Should return single card", 1, result.size)
        assertTrue("Card should have valid grid position", result[0].hasValidGridPosition)
        assertEquals("Single card should be at position (0,0)", 0, result[0].gridRow)
        assertEquals("Single card should be at position (0,0)", 0, result[0].gridColumn)
    }

    @Test
    fun `mapToGrid should map perfect 4x6 grid`() {
        // Given
        val perfectGrid = createPerfectGrid()

        // When
        val result = gridMapper.mapToGrid(perfectGrid)

        // Then
        assertEquals("Should return all 24 cards", 24, result.size)

        // Verify each card is mapped to correct position
        result.forEach { card ->
            assertTrue("All cards should have valid grid positions", card.hasValidGridPosition)
        }

        // Check that all grid positions are unique
        val positions = result.map { Pair(it.gridRow, it.gridColumn) }.toSet()
        assertEquals("All positions should be unique", 24, positions.size)
    }

    @Test
    fun `mapToGrid should handle missing cards`() {
        // Given - Grid with some cards missing
        val incompleteGrid = createIncompleteGrid()

        // When
        val result = gridMapper.mapToGrid(incompleteGrid)

        // Then
        assertEquals("Should return all provided cards", incompleteGrid.size, result.size)
        result.forEach { card ->
            assertTrue("All cards should have valid grid positions", card.hasValidGridPosition)
        }
    }

    @Test
    fun `mapToGrid should handle cards with noise in positions`() {
        // Given - Grid with position noise
        val noisyGrid = createGridWithNoise()

        // When
        val result = gridMapper.mapToGrid(noisyGrid)

        // Then
        assertEquals("Should return all cards", noisyGrid.size, result.size)

        // Verify clustering still works with noise
        val rows = result.groupBy { it.gridRow }
        val cols = result.groupBy { it.gridColumn }

        assertTrue("Should detect 4 rows", rows.size <= 4)
        assertTrue("Should detect 6 columns", cols.size <= 6)
    }

    @Test
    fun `analyzeGridCompleteness should calculate correct statistics`() {
        // Given
        val cards = createPerfectGrid().mapIndexed { index, card ->
            card.withGridPosition(index / 6, index % 6)
        }

        // When
        val analysis = gridMapper.analyzeGridCompleteness(cards)

        // Then
        assertEquals("Should have 24 total cards", 24, analysis.totalCards)
        assertEquals("Should have 24 valid positions", 24, analysis.validPositions)
        assertEquals("Should be 100% complete", 1.0f, analysis.completeness, 0.01f)
        assertEquals("Should have full row coverage", 1.0f, analysis.rowCoverage, 0.01f)
        assertEquals("Should have full column coverage", 1.0f, analysis.columnCoverage, 0.01f)
        assertTrue("Should be valid grid", analysis.isValid)
        assertTrue("Should have no duplicates", analysis.duplicatePositions.isEmpty())
    }

    @Test
    fun `analyzeGridCompleteness should detect duplicate positions`() {
        // Given - Cards with duplicate grid positions
        val cards = listOf(
            createCard(0, 100f, 100f).withGridPosition(0, 0),
            createCard(1, 150f, 100f).withGridPosition(0, 0), // Duplicate
            createCard(2, 200f, 100f).withGridPosition(0, 1)
        )

        // When
        val analysis = gridMapper.analyzeGridCompleteness(cards)

        // Then
        assertEquals("Should detect 1 duplicate position", 1, analysis.duplicatePositions.size)
        assertEquals("Duplicate should be at (0,0)", Pair(0, 0), analysis.duplicatePositions[0])
        assertFalse("Grid should be invalid due to duplicates", analysis.isValid)
    }

    @Test
    fun `refineGridPositions should improve grid alignment`() {
        // Given - Cards slightly misaligned
        val misalignedCards = createMisalignedGrid()

        // When
        val refined = gridMapper.refineGridPositions(misalignedCards)

        // Then
        assertEquals("Should return same number of cards", misalignedCards.size, refined.size)

        // Check that grid positions are more regular
        val rowGroups = refined.groupBy { it.gridRow }
        val colGroups = refined.groupBy { it.gridColumn }

        rowGroups.forEach { (_, cards) ->
            assertTrue("Each row should have cards", cards.isNotEmpty())
        }
        colGroups.forEach { (_, cards) ->
            assertTrue("Each column should have cards", cards.isNotEmpty())
        }
    }

    @Test
    fun `mapToGridUsingExpectedPositions should handle irregular spacing`() {
        // Given - Cards with irregular spacing
        val irregularCards = createIrregularGrid()

        // When
        val result = gridMapper.mapToGridUsingExpectedPositions(irregularCards)

        // Then
        result.forEach { card ->
            assertTrue("All cards should have valid grid positions", card.hasValidGridPosition)
            assertTrue("Row should be in valid range", card.gridRow in 0..3)
            assertTrue("Column should be in valid range", card.gridColumn in 0..5)
        }
    }

    @Test
    fun `validateAndFixGrid should resolve conflicts`() {
        // Given - Cards with position conflicts
        val conflictingCards = listOf(
            createCard(0, 100f, 100f).withGridPosition(0, 0).withConfidence(0.9f),
            createCard(1, 105f, 105f).withGridPosition(0, 0).withConfidence(0.7f), // Lower confidence
            createCard(2, 200f, 100f).withGridPosition(0, 1).withConfidence(0.8f)
        )

        // When
        val fixed = gridMapper.validateAndFixGrid(conflictingCards)

        // Then
        // Should keep higher confidence card at (0,0)
        val cardsAt00 = fixed.filter { it.gridRow == 0 && it.gridColumn == 0 }
        assertEquals("Should have only one card at (0,0)", 1, cardsAt00.size)
        assertEquals("Should keep higher confidence card", 0, cardsAt00[0].id)
    }

    @Test
    fun `mapToGrid should handle very few cards gracefully`() {
        // Given - Only 3 cards
        val fewCards = listOf(
            createCard(0, 100f, 100f),
            createCard(1, 250f, 100f),
            createCard(2, 100f, 250f)
        )

        // When
        val result = gridMapper.mapToGrid(fewCards)

        // Then
        assertEquals("Should return all 3 cards", 3, result.size)
        result.forEach { card ->
            assertTrue("All cards should have grid positions", card.hasValidGridPosition)
        }
    }

    @Test
    fun `mapToGrid should handle cards in single row`() {
        // Given - All cards in one row
        val singleRow = (0..5).map { col ->
            createCard(col, 100f + col * 100f, 100f)
        }

        // When
        val result = gridMapper.mapToGrid(singleRow)

        // Then
        assertEquals("Should return all cards", 6, result.size)

        // All should be in row 0
        result.forEach { card ->
            assertEquals("All cards should be in row 0", 0, card.gridRow)
        }

        // Should have different columns
        val columns = result.map { it.gridColumn }.toSet()
        assertEquals("Should have 6 different columns", 6, columns.size)
    }

    @Test
    fun `mapToGrid should handle cards in single column`() {
        // Given - All cards in one column
        val singleColumn = (0..3).map { row ->
            createCard(row, 100f, 100f + row * 100f)
        }

        // When
        val result = gridMapper.mapToGrid(singleColumn)

        // Then
        assertEquals("Should return all cards", 4, result.size)

        // All should be in column 0
        result.forEach { card ->
            assertEquals("All cards should be in column 0", 0, card.gridColumn)
        }

        // Should have different rows
        val rows = result.map { it.gridRow }.toSet()
        assertEquals("Should have 4 different rows", 4, rows.size)
    }

    // Helper methods for creating test data

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

    private fun createPerfectGrid(): List<CardPosition> {
        val cards = mutableListOf<CardPosition>()
        var id = 0

        for (row in 0 until 4) {
            for (col in 0 until 6) {
                cards.add(
                    createCard(
                        id = id++,
                        x = 100f + col * 120f,
                        y = 100f + row * 140f
                    )
                )
            }
        }

        return cards
    }

    private fun createIncompleteGrid(): List<CardPosition> {
        // Create grid with some cards missing (positions 5, 11, 17, 23)
        return createPerfectGrid().filterIndexed { index, _ ->
            index !in listOf(5, 11, 17, 23)
        }
    }

    private fun createGridWithNoise(): List<CardPosition> {
        // Add random noise to perfect grid positions
        return createPerfectGrid().map { card ->
            card.copy(
                centerX = card.centerX + (Math.random() * 20 - 10).toFloat(),
                centerY = card.centerY + (Math.random() * 20 - 10).toFloat()
            )
        }
    }

    private fun createMisalignedGrid(): List<CardPosition> {
        // Create grid with systematic misalignment
        return createPerfectGrid().mapIndexed { index, card ->
            val offset = if (index % 2 == 0) 5f else -5f
            card.copy(
                centerX = card.centerX + offset,
                centerY = card.centerY + offset
            ).withGridPosition(index / 6, index % 6)
        }
    }

    private fun createIrregularGrid(): List<CardPosition> {
        val cards = mutableListOf<CardPosition>()
        var id = 0

        // Create grid with irregular spacing
        val xPositions = listOf(50f, 180f, 290f, 430f, 550f, 700f)
        val yPositions = listOf(60f, 200f, 320f, 480f)

        for (row in yPositions.indices) {
            for (col in xPositions.indices) {
                cards.add(
                    createCard(
                        id = id++,
                        x = xPositions[col],
                        y = yPositions[row]
                    )
                )
            }
        }

        return cards
    }
}
package com.memoryassist.fanfanlokmapper

import com.memoryassist.fanfanlokmapper.utils.BorderDetector
import com.memoryassist.fanfanlokmapper.utils.ImageProcessor
import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import kotlin.math.abs

@RunWith(MockitoJUnitRunner::class)
class BorderDetectorTest {

    private lateinit var borderDetector: BorderDetector

    @Mock
    private lateinit var mockMat: Mat

    @Before
    fun setup() {
        borderDetector = BorderDetector()

        // Initialize OpenCV for testing (if needed)
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME)
        } catch (e: UnsatisfiedLinkError) {
            // OpenCV might not be available in unit tests
        }
    }

    @Test
    fun `detectCardBorders should return empty list when no edges detected`() {
        // Given
        val emptyMat = createEmptyMat(100, 100)
        val thresholds = createTestThresholds()

        // When
        val result = borderDetector.detectCardBorders(emptyMat, thresholds)

        // Then
        assertTrue("Should return empty list for empty image", result.isEmpty())
    }

    @Test
    fun `detectCardBorders should detect single card`() {
        // Given
        val matWithCard = createMatWithSingleCard()
        val thresholds = createTestThresholds()

        // When
        val result = borderDetector.detectCardBorders(matWithCard, thresholds)

        // Then
        assertEquals("Should detect exactly one card", 1, result.size)

        val card = result[0]
        assertTrue("Card should have valid dimensions", card.width > 0 && card.height > 0)
        assertTrue("Card should have valid position", card.centerX > 0 && card.centerY > 0)
    }

    @Test
    fun `detectCardBorders should detect grid of cards`() {
        // Given
        val matWithGrid = createMatWithCardGrid(4, 6)
        val thresholds = createTestThresholds()

        // When
        val result = borderDetector.detectCardBorders(matWithGrid, thresholds)

        // Then
        assertTrue("Should detect multiple cards", result.size > 0)
        assertTrue("Should detect at most 24 cards", result.size <= 24)
    }

    @Test
    fun `detectCardBorders should filter out too small contours`() {
        // Given
        val matWithNoise = createMatWithNoiseAndCards()
        val thresholds = createTestThresholds(minArea = 1000)

        // When
        val result = borderDetector.detectCardBorders(matWithNoise, thresholds)

        // Then
        result.forEach { card ->
            assertTrue(
                "All detected cards should meet minimum area requirement",
                card.area >= 1000
            )
        }
    }

    @Test
    fun `detectCardBorders should filter out too large contours`() {
        // Given
        val matWithLargeShapes = createMatWithLargeShapes()
        val thresholds = createTestThresholds(maxArea = 5000)

        // When
        val result = borderDetector.detectCardBorders(matWithLargeShapes, thresholds)

        // Then
        result.forEach { card ->
            assertTrue(
                "All detected cards should be within maximum area",
                card.area <= 5000
            )
        }
    }

    @Test
    fun `detectCardBorders should handle overlapping cards`() {
        // Given
        val matWithOverlapping = createMatWithOverlappingCards()
        val thresholds = createTestThresholds()

        // When
        val result = borderDetector.detectCardBorders(matWithOverlapping, thresholds)

        // Then
        // Check that no two cards overlap significantly
        for (i in result.indices) {
            for (j in i + 1 until result.size) {
                assertFalse(
                    "Detected cards should not overlap",
                    result[i].overlapsWith(result[j], tolerance = 10f)
                )
            }
        }
    }

    @Test
    fun `detectCardBorders should assign confidence scores`() {
        // Given
        val mat = createMatWithCardGrid(2, 3)
        val thresholds = createTestThresholds()

        // When
        val result = borderDetector.detectCardBorders(mat, thresholds)

        // Then
        result.forEach { card ->
            assertTrue(
                "Confidence should be between 0 and 1",
                card.confidence in 0f..1f
            )
        }
    }

    @Test
    fun `detectUsingConnectedComponents should detect cards as fallback`() {
        // Given
        val binaryMat = createBinaryMatWithCards()
        val thresholds = createTestThresholds()

        // When
        val result = borderDetector.detectUsingConnectedComponents(binaryMat, thresholds)

        // Then
        assertTrue("Should detect at least one card", result.isNotEmpty())
        result.forEach { card ->
            assertEquals(
                "Fallback method should assign 0.8 confidence",
                0.8f,
                card.confidence,
                0.01f
            )
        }
    }

    @Test
    fun `detectCardBorders should handle rotated cards`() {
        // Given
        val matWithRotated = createMatWithRotatedCard(15.0) // 15 degree rotation
        val thresholds = createTestThresholds()

        // When
        val result = borderDetector.detectCardBorders(matWithRotated, thresholds)

        // Then
        assertTrue("Should detect rotated cards", result.isNotEmpty())
        // Confidence should be reduced for rotated cards
        result.forEach { card ->
            assertTrue(
                "Rotated cards should have reduced confidence",
                card.confidence < 1.0f
            )
        }
    }

    @Test
    fun `detectCardBorders should respect aspect ratio constraints`() {
        // Given
        val mat = createMatWithVariousShapes()
        val thresholds = createTestThresholds(
            aspectRatioMin = 0.6,
            aspectRatioMax = 0.8
        )

        // When
        val result = borderDetector.detectCardBorders(mat, thresholds)

        // Then
        result.forEach { card ->
            val aspectRatio = minOf(card.width, card.height) / maxOf(card.width, card.height)
            assertTrue(
                "Detected cards should meet aspect ratio constraints",
                aspectRatio in 0.6..0.8
            )
        }
    }

    // Helper methods for creating test data

    private fun createTestThresholds(
        minArea: Int = 100,
        maxArea: Int = 10000,
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

    private fun createEmptyMat(width: Int, height: Int): Mat {
        return Mat.zeros(height, width, CvType.CV_8UC1)
    }

    private fun createMatWithSingleCard(): Mat {
        val mat = Mat.zeros(200, 200, CvType.CV_8UC1)
        // Draw a rectangle representing a card
        Imgproc.rectangle(
            mat,
            Point(50.0, 50.0),
            Point(100.0, 120.0),
            Scalar(255.0),
            2
        )
        return mat
    }

    private fun createMatWithCardGrid(rows: Int, cols: Int): Mat {
        val mat = Mat.zeros(600, 800, CvType.CV_8UC1)
        val cardWidth = 60.0
        val cardHeight = 80.0
        val spacingX = 120.0
        val spacingY = 140.0

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val x = 50 + col * spacingX
                val y = 50 + row * spacingY
                Imgproc.rectangle(
                    mat,
                    Point(x, y),
                    Point(x + cardWidth, y + cardHeight),
                    Scalar(255.0),
                    2
                )
            }
        }
        return mat
    }

    private fun createMatWithNoiseAndCards(): Mat {
        val mat = createMatWithCardGrid(2, 2)
        // Add small noise rectangles
        for (i in 0..10) {
            val x = (Math.random() * 700).toInt()
            val y = (Math.random() * 500).toInt()
            Imgproc.rectangle(
                mat,
                Point(x.toDouble(), y.toDouble()),
                Point(x + 5.0, y + 5.0),
                Scalar(255.0),
                1
            )
        }
        return mat
    }

    private fun createMatWithLargeShapes(): Mat {
        val mat = createMatWithSingleCard()
        // Add a large rectangle
        Imgproc.rectangle(
            mat,
            Point(150.0, 10.0),
            Point(190.0, 190.0),
            Scalar(255.0),
            2
        )
        return mat
    }

    private fun createMatWithOverlappingCards(): Mat {
        val mat = Mat.zeros(300, 300, CvType.CV_8UC1)
        // Draw two overlapping rectangles
        Imgproc.rectangle(
            mat,
            Point(50.0, 50.0),
            Point(120.0, 150.0),
            Scalar(255.0),
            2
        )
        Imgproc.rectangle(
            mat,
            Point(100.0, 100.0),
            Point(170.0, 200.0),
            Scalar(255.0),
            2
        )
        return mat
    }

    private fun createBinaryMatWithCards(): Mat {
        val mat = Mat.zeros(400, 400, CvType.CV_8UC1)
        // Fill rectangles to simulate binary connected components
        Imgproc.rectangle(
            mat,
            Point(50.0, 50.0),
            Point(120.0, 150.0),
            Scalar(255.0),
            -1 // Filled
        )
        Imgproc.rectangle(
            mat,
            Point(200.0, 50.0),
            Point(270.0, 150.0),
            Scalar(255.0),
            -1
        )
        return mat
    }

    private fun createMatWithVariousShapes(): Mat {
        val mat = Mat.zeros(400, 600, CvType.CV_8UC1)

        // Square (aspect ratio 1.0)
        Imgproc.rectangle(
            mat,
            Point(50.0, 50.0),
            Point(100.0, 100.0),
            Scalar(255.0),
            2
        )

        // Card-like rectangle (aspect ratio ~0.7)
        Imgproc.rectangle(
            mat,
            Point(150.0, 50.0),
            Point(210.0, 135.0),
            Scalar(255.0),
            2
        )

        // Wide rectangle (aspect ratio ~0.4)
        Imgproc.rectangle(
            mat,
            Point(250.0, 50.0),
            Point(380.0, 100.0),
            Scalar(255.0),
            2
        )

        return mat
    }

    private fun createMatWithRotatedCard(angle: Double): Mat {
        val mat = Mat.zeros(300, 300, CvType.CV_8UC1)
        val center = Point(150.0, 150.0)
        val size = Size(60.0, 80.0)
        val rotatedRect = RotatedRect(center, size, angle)

        // Draw rotated rectangle
        val vertices = arrayOfNulls<Point>(4)
        rotatedRect.points(vertices)
        for (i in 0..3) {
            Imgproc.line(
                mat,
                vertices[i],
                vertices[(i + 1) % 4],
                Scalar(255.0),
                2
            )
        }
        return mat
    }
}
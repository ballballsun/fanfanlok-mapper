package com.memoryassist.fanfanlokmapper.utils

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import kotlin.math.abs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BorderDetector @Inject constructor() {
    
    /**
     * Detect card borders using Canny edge detection and contour analysis
     */
    fun detectCardBorders(
        preprocessedMat: Mat,
        thresholds: ImageProcessor.ThresholdParams
    ): List<CardPosition> {
        val startTime = System.currentTimeMillis()
        Logger.logDetectionStart("Border detection initiated")
        
        // Step 1: Apply Canny edge detection
        val edges = applyCanyEdgeDetection(preprocessedMat)
        Logger.logDetectionProgress("Canny edge detection", "Edges detected")
        
        // Step 2: Find contours
        val contours = findContours(edges)
        Logger.logDetectionProgress("Contour detection", "${contours.size} contours found")
        
        // Step 3: Filter contours to find rectangles
        val rectangles = filterRectangularContours(contours, thresholds)
        Logger.logDetectionProgress("Rectangle filtering", "${rectangles.size} rectangles found")
        
        // Step 4: Convert rectangles to CardPositions
        val cardPositions = rectanglesToCardPositions(rectangles)
        
        // Step 5: Remove overlapping detections
        val finalPositions = removeOverlappingCards(cardPositions)
        
        // Step 6: If we found very few cards, try alternative approaches
        if (finalPositions.size < 5) {
            Logger.warning("Primary detection found only ${finalPositions.size} cards, trying fallback methods")
            
            // Try with even more relaxed thresholds
            val relaxedThresholds = thresholds.copy(
                minArea = 50,  // Even lower minimum
                maxArea = (thresholds.maxArea * 3.0).toInt(),
                minWidth = 10,  // Very low minimum width
                minHeight = 15, // Very low minimum height
                aspectRatioMin = 0.1,  // Very lenient
                aspectRatioMax = 10.0   // Very lenient
            )
            
            Logger.info("🔄 Trying fallback with relaxed thresholds")
            Logger.logThresholds(relaxedThresholds)
            
            val fallbackRectangles = filterRectangularContours(contours, relaxedThresholds)
            
            if (fallbackRectangles.size > finalPositions.size) {
                val fallbackPositions = rectanglesToCardPositions(fallbackRectangles)
                val fallbackFinal = removeOverlappingCards(fallbackPositions)
                
                if (fallbackFinal.size > finalPositions.size) {
                    Logger.logFallbackDetection(finalPositions.size, fallbackFinal.size, "fallback")
                    val processingTime = System.currentTimeMillis() - startTime
                    Logger.logDetectionResult(fallbackFinal.size, processingTime)
                    return fallbackFinal
                }
            } else {
                Logger.warning("Fallback detection didn't improve results: ${fallbackRectangles.size} rectangles")
            }
        }
        
        val processingTime = System.currentTimeMillis() - startTime
        Logger.logDetectionResult(finalPositions.size, processingTime)
        
        return finalPositions
    }
    
    /**
     * Apply Canny edge detection
     */
    private fun applyCanyEdgeDetection(mat: Mat): Mat {
        val edges = Mat()
        
        // Use adaptive thresholds based on image statistics
        val (threshold1, threshold2) = calculateCannyThresholds(mat)
        
        Imgproc.Canny(mat, edges, threshold1, threshold2, 3, false)
        
        // Apply morphological operations to connect nearby edges
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(3.0, 3.0)
        )
        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
        
        return edges
    }
    
    /**
     * Calculate adaptive Canny thresholds based on image statistics
     */
    private fun calculateCannyThresholds(mat: Mat): Pair<Double, Double> {
        val mean = Core.mean(mat)
        val sigma = 0.33
        
        val v = mean.`val`[0]
        val lower = maxOf(0.0, (1.0 - sigma) * v)
        val upper = minOf(255.0, (1.0 + sigma) * v)
        
        Logger.logCannyParams(lower, upper, "mean=${String.format("%.1f", v)}")
        return Pair(lower, upper)
    }
    
    /**
     * Find contours in edge image
     */
    private fun findContours(edges: Mat): List<MatOfPoint> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        
        Imgproc.findContours(
            edges,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        
        return contours
    }
    
    /**
     * Filter contours to find rectangular shapes
     */
    private fun filterRectangularContours(
        contours: List<MatOfPoint>,
        thresholds: ImageProcessor.ThresholdParams
    ): List<RotatedRect> {
        val rectangles = mutableListOf<RotatedRect>()
        var areaFiltered = 0
        var shapeFiltered = 0
        var rectangleFiltered = 0
        
        Logger.info("Filtering ${contours.size} contours with thresholds")
        Logger.logThresholds(thresholds)
        
        for ((index, contour) in contours.withIndex()) {
            // Calculate contour area
            val area = Imgproc.contourArea(contour)
            
            // Log first 20 contours for detailed debugging
            if (index < 20) {
                Logger.debug("Contour $index: area=$area (range: ${thresholds.minArea}-${thresholds.maxArea})")
            }
            
            // Skip if area is outside thresholds
            if (area < thresholds.minArea || area > thresholds.maxArea) {
                areaFiltered++
                if (index < 20) { // Log first 20 for debugging
                    Logger.logContourDetails(index, area, 0, "n/a", "AREA_FILTERED")
                }
                continue
            }
            
            // Approximate contour to polygon
            val approx = approximateContour(contour)
            val vertices = approx.rows()
            
            if (index < 20) {
                Logger.debug("Contour $index: area=$area, vertices=$vertices")
            }
            
            // Check if it's roughly rectangular (3-8 vertices allowing for imperfect detection)
            if (vertices in 3..8) {
                // Fit a rotated rectangle
                val rect = Imgproc.minAreaRect(MatOfPoint2f(*contour.toArray()))
                val sizeStr = "${String.format("%.1f", rect.size.width)}x${String.format("%.1f", rect.size.height)}"
                
                if (index < 20) {
                    Logger.debug("Contour $index: rect size=$sizeStr, angle=${rect.angle}")
                }
                
                // Check dimensions and aspect ratio
                if (isValidCardRectangle(rect, thresholds)) {
                    rectangles.add(rect)
                    Logger.logContourDetails(index, area, vertices, sizeStr, "ACCEPTED")
                } else {
                    rectangleFiltered++
                    if (index < 20) { // Log first 20 rejected for debugging
                        Logger.logContourDetails(index, area, vertices, sizeStr, "RECT_REJECTED")
                    }
                }
            } else {
                shapeFiltered++
                if (index < 20) {
                    Logger.logContourDetails(index, area, vertices, "n/a", "SHAPE_FILTERED")
                }
            }
        }
        
        Logger.logContourFiltering(contours.size, areaFiltered, shapeFiltered, rectangleFiltered, rectangles.size)
        return rectangles
    }
    
    /**
     * Approximate contour to reduce points
     */
    private fun approximateContour(contour: MatOfPoint): MatOfPoint {
        val contour2f = MatOfPoint2f(*contour.toArray())
        val approx2f = MatOfPoint2f()
        
        val epsilon = 0.05 * Imgproc.arcLength(contour2f, true)  // More lenient approximation
        Imgproc.approxPolyDP(contour2f, approx2f, epsilon, true)
        
        val approx = MatOfPoint(*approx2f.toArray())
        return approx
    }
    
    /**
     * Validate if rectangle meets card criteria
     */
    private fun isValidCardRectangle(
        rect: RotatedRect,
        thresholds: ImageProcessor.ThresholdParams
    ): Boolean {
        val width = minOf(rect.size.width, rect.size.height)
        val height = maxOf(rect.size.width, rect.size.height)
        
        // Check minimum dimensions
        if (width < thresholds.minWidth || height < thresholds.minHeight) {
            Logger.debug("Rectangle rejected: dimensions ${String.format("%.1f", width)}x${String.format("%.1f", height)} below min ${thresholds.minWidth}x${thresholds.minHeight}")
            return false
        }
        
        // Check aspect ratio (be more lenient)
        val aspectRatio = width / height
        if (aspectRatio < thresholds.aspectRatioMin || aspectRatio > thresholds.aspectRatioMax) {
            Logger.debug("Rectangle rejected: aspect ratio ${String.format("%.3f", aspectRatio)} outside ${thresholds.aspectRatioMin}-${thresholds.aspectRatioMax}")
            return false
        }
        
        // Check for reasonable angle (be more lenient - allow up to 30 degrees rotation)
        val angle = abs(rect.angle)
        if (angle > 30 && angle < 60) {
            Logger.debug("Rectangle rejected: angle ${String.format("%.1f", angle)} too steep")
            return false
        }
        
        Logger.debug("Rectangle accepted: ${String.format("%.1f", width)}x${String.format("%.1f", height)}, aspect=${String.format("%.3f", aspectRatio)}, angle=${String.format("%.1f", angle)}")
        return true
    }
    
    /**
     * Convert RotatedRect objects to CardPosition objects
     */
    private fun rectanglesToCardPositions(rectangles: List<RotatedRect>): List<CardPosition> {
        return rectangles.mapIndexed { index, rect ->
            val center = rect.center
            val size = rect.size
            
            // Ensure width < height (portrait orientation)
            val width = minOf(size.width, size.height).toFloat()
            val height = maxOf(size.width, size.height).toFloat()
            
            CardPosition(
                id = index,
                centerX = center.x.toFloat(),
                centerY = center.y.toFloat(),
                width = width,
                height = height,
                confidence = calculateConfidence(rect)
            )
        }
    }
    
    /**
     * Calculate confidence score for detected rectangle
     */
    private fun calculateConfidence(rect: RotatedRect): Float {
        var confidence = 1.0f
        
        // Reduce confidence for rotated rectangles
        val angle = abs(rect.angle)
        if (angle > 5) {
            confidence *= (1.0f - angle.toFloat() / 90.0f)
        }
        
        // Reduce confidence for extreme aspect ratios
        val aspectRatio = minOf(rect.size.width, rect.size.height) / 
                         maxOf(rect.size.width, rect.size.height)
        if (aspectRatio < 0.5 || aspectRatio > 0.8) {
            confidence *= 0.9f
        }
        
        return confidence.coerceIn(0.5f, 1.0f)
    }
    
    /**
     * Remove overlapping card detections, keeping higher confidence ones
     */
    private fun removeOverlappingCards(cards: List<CardPosition>): List<CardPosition> {
        val sortedCards = cards.sortedByDescending { it.confidence }
        val finalCards = mutableListOf<CardPosition>()
        
        for (card in sortedCards) {
            var hasOverlap = false
            
            for (existing in finalCards) {
                if (card.overlapsWith(existing, tolerance = 20f)) {
                    hasOverlap = true
                    break
                }
            }
            
            if (!hasOverlap) {
                finalCards.add(card)
            }
        }
        
        Logger.debug("Removed ${cards.size - finalCards.size} overlapping detections")
        return finalCards
    }
    
    /**
     * Alternative detection using connected components (fallback method)
     */
    fun detectUsingConnectedComponents(
        binaryMat: Mat,
        thresholds: ImageProcessor.ThresholdParams
    ): List<CardPosition> {
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        
        val numComponents = Imgproc.connectedComponentsWithStats(
            binaryMat, labels, stats, centroids, 8, CvType.CV_32S
        )
        
        val cards = mutableListOf<CardPosition>()
        
        // Skip 0 as it's the background
        for (i in 1 until numComponents) {
            val area = stats.get(i, Imgproc.CC_STAT_AREA)[0].toInt()
            val width = stats.get(i, Imgproc.CC_STAT_WIDTH)[0].toInt()
            val height = stats.get(i, Imgproc.CC_STAT_HEIGHT)[0].toInt()
            
            if (area >= thresholds.minArea && area <= thresholds.maxArea &&
                width >= thresholds.minWidth && height >= thresholds.minHeight) {
                
                val centerX = centroids.get(i, 0)[0].toFloat()
                val centerY = centroids.get(i, 1)[0].toFloat()
                
                cards.add(
                    CardPosition(
                        id = cards.size,
                        centerX = centerX,
                        centerY = centerY,
                        width = width.toFloat(),
                        height = height.toFloat(),
                        confidence = 0.8f // Lower confidence for fallback method
                    )
                )
            }
        }
        
        return cards
    }
}
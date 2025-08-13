package com.memoryassist.fanfanlokmapper.domain.usecase

import android.graphics.Bitmap
import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import com.memoryassist.fanfanlokmapper.data.models.DetectionResult
import com.memoryassist.fanfanlokmapper.data.models.DetectionMetadata
import com.memoryassist.fanfanlokmapper.domain.repository.DetectionConfig
import com.memoryassist.fanfanlokmapper.utils.BorderDetector
import com.memoryassist.fanfanlokmapper.utils.GridMapper
import com.memoryassist.fanfanlokmapper.utils.ImageProcessor
import com.memoryassist.fanfanlokmapper.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case for orchestrating the card detection process
 */
@Singleton
class DetectCardsUseCase @Inject constructor(
    private val imageProcessor: ImageProcessor,
    private val borderDetector: BorderDetector,
    private val gridMapper: GridMapper,
    private val filterResultsUseCase: FilterResultsUseCase
) {
    
    /**
     * Execute card detection on a bitmap image
     */
    suspend fun execute(
        bitmap: Bitmap,
        config: DetectionConfig = DetectionConfig.default()
    ): DetectionResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        try {
            Logger.logDetectionStart("Card detection use case started")
            
            // Step 1: Convert bitmap to OpenCV Mat
            val originalMat = imageProcessor.bitmapToMat(bitmap)
            
            // Step 2: Resize if needed for performance
            val workingMat = if (config.maxImageWidth > 0 && config.maxImageHeight > 0) {
                imageProcessor.resizeIfNeeded(
                    originalMat,
                    config.maxImageWidth,
                    config.maxImageHeight
                )
            } else {
                originalMat
            }
            
            // Step 3: Calculate adaptive thresholds
            val thresholds = if (config.useAdaptiveSizeFilter) {
                imageProcessor.calculateAdaptiveThresholds(
                    workingMat.cols(),
                    workingMat.rows()
                )
            } else {
                ImageProcessor.ThresholdParams(
                    minArea = config.minCardArea,
                    maxArea = config.maxCardArea,
                    minWidth = com.memoryassist.fanfanlokmapper.utils.Constants.MIN_CARD_WIDTH,
                    minHeight = com.memoryassist.fanfanlokmapper.utils.Constants.MIN_CARD_HEIGHT,
                    aspectRatioMin = com.memoryassist.fanfanlokmapper.utils.Constants.ASPECT_RATIO_MIN,
                    aspectRatioMax = com.memoryassist.fanfanlokmapper.utils.Constants.ASPECT_RATIO_MAX
                )
            }
            
            // Step 4: Detect cards using selected method
            val detectedCards = when {
                config.useEdgeDetection -> detectUsingEdges(workingMat, thresholds, config)
                config.useColorDetection -> detectUsingColor(workingMat, thresholds, config)
                config.useTemplateMatching -> detectUsingTemplate(workingMat, thresholds, config)
                else -> detectUsingEdges(workingMat, thresholds, config) // Default to edge detection
            }
            
            Logger.logDetectionProgress("Initial detection", "${detectedCards.size} cards found")
            
            // Step 5: Filter results
            val filterResult = filterResultsUseCase.execute(
                detectedCards,
                thresholds,
                workingMat.cols(),
                workingMat.rows()
            )
            
            Logger.logDetectionProgress("Filtering", "${filterResult.filteredCards.size} cards after filtering")
            
            // Step 6: Apply adaptive filtering if needed
            val adaptiveFiltered = if (config.useAdaptiveSizeFilter) {
                filterResultsUseCase.applyAdaptiveFiltering(
                    filterResult.filteredCards,
                    config.expectedRows * config.expectedColumns
                )
            } else {
                filterResult.filteredCards
            }
            
            // Step 7: Map to grid
            val gridMapped = gridMapper.mapToGrid(adaptiveFiltered)
            
            // Step 8: Refine grid positions if enabled
            val finalCards = if (config.refineGridPositions) {
                val refined = gridMapper.refineGridPositions(gridMapped)
                gridMapper.validateAndFixGrid(refined)
            } else {
                gridMapped
            }
            
            // Step 9: Scale coordinates back if image was resized
            val scaledCards = if (workingMat !== originalMat) {
                val scaleX = originalMat.cols().toFloat() / workingMat.cols()
                val scaleY = originalMat.rows().toFloat() / workingMat.rows()
                scaleCardPositions(finalCards, scaleX, scaleY)
            } else {
                finalCards
            }
            
            val processingTime = System.currentTimeMillis() - startTime
            
            // Create metadata
            val metadata = DetectionMetadata(
                algorithmUsed = getAlgorithmName(config),
                preprocessingSteps = getPreprocessingSteps(config),
                detectionParameters = getDetectionParameters(config),
                qualityScore = calculateQualityScore(scaledCards),
                detectedEdges = detectedCards.size,
                filteredContours = filterResult.totalRemoved,
                gridAnalysisScore = calculateGridScore(scaledCards)
            )
            
            Logger.logDetectionResult(scaledCards.size, processingTime)
            
            DetectionResult.success(
                cards = scaledCards,
                processingTime = processingTime,
                imageWidth = originalMat.cols(),
                imageHeight = originalMat.rows(),
                metadata = metadata
            )
            
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Logger.logDetectionError("Detection failed", e.message ?: "Unknown error")
            
            DetectionResult.failure(
                error = e.message ?: "Unknown error occurred during detection",
                processingTime = processingTime,
                imageWidth = bitmap.width,
                imageHeight = bitmap.height
            )
        }
    }
    
    /**
     * Detect cards using edge detection
     */
    private suspend fun detectUsingEdges(
        mat: Mat,
        thresholds: ImageProcessor.ThresholdParams,
        config: DetectionConfig
    ): List<CardPosition> = withContext(Dispatchers.Default) {
        // Preprocess image
        val preprocessed = if (config.enhanceContrast) {
            val enhanced = imageProcessor.enhanceContrast(mat)
            imageProcessor.preprocessForEdgeDetection(enhanced)
        } else {
            imageProcessor.preprocessForEdgeDetection(mat)
        }
        
        // Detect borders
        borderDetector.detectCardBorders(preprocessed, thresholds)
    }
    
    /**
     * Detect cards using color detection (placeholder for future implementation)
     */
    private suspend fun detectUsingColor(
        mat: Mat,
        thresholds: ImageProcessor.ThresholdParams,
        config: DetectionConfig
    ): List<CardPosition> = withContext(Dispatchers.Default) {
        Logger.warning("Color detection not yet implemented, falling back to edge detection")
        detectUsingEdges(mat, thresholds, config)
    }
    
    /**
     * Detect cards using template matching (placeholder for future implementation)
     */
    private suspend fun detectUsingTemplate(
        mat: Mat,
        thresholds: ImageProcessor.ThresholdParams,
        config: DetectionConfig
    ): List<CardPosition> = withContext(Dispatchers.Default) {
        Logger.warning("Template matching not yet implemented, falling back to edge detection")
        detectUsingEdges(mat, thresholds, config)
    }
    
    /**
     * Scale card positions back to original image dimensions
     */
    private fun scaleCardPositions(
        cards: List<CardPosition>,
        scaleX: Float,
        scaleY: Float
    ): List<CardPosition> {
        return cards.map { card ->
            card.copy(
                centerX = card.centerX * scaleX,
                centerY = card.centerY * scaleY,
                width = card.width * scaleX,
                height = card.height * scaleY
            )
        }
    }
    
    /**
     * Get algorithm name based on configuration
     */
    private fun getAlgorithmName(config: DetectionConfig): String {
        return when {
            config.useEdgeDetection -> "Canny Edge + Contour Analysis"
            config.useColorDetection -> "Color-based Detection"
            config.useTemplateMatching -> "Template Matching"
            else -> "Unknown"
        }
    }
    
    /**
     * Get list of preprocessing steps based on configuration
     */
    private fun getPreprocessingSteps(config: DetectionConfig): List<String> {
        val steps = mutableListOf<String>()
        
        if (config.maxImageWidth > 0 || config.maxImageHeight > 0) {
            steps.add("Image Resizing")
        }
        if (config.enhanceContrast) {
            steps.add("Contrast Enhancement (CLAHE)")
        }
        if (config.gaussianBlurSize > 0) {
            steps.add("Gaussian Blur (${config.gaussianBlurSize}x${config.gaussianBlurSize})")
        }
        steps.add("Grayscale Conversion")
        
        return steps
    }
    
    /**
     * Get detection parameters as string map
     */
    private fun getDetectionParameters(config: DetectionConfig): Map<String, String> {
        return mapOf(
            "cannyLowerThreshold" to config.cannyLowerThreshold.toString(),
            "cannyUpperThreshold" to config.cannyUpperThreshold.toString(),
            "useAdaptiveThresholds" to config.useAdaptiveThresholds.toString(),
            "minCardArea" to config.minCardArea.toString(),
            "maxCardArea" to config.maxCardArea.toString(),
            "minConfidence" to config.minConfidenceThreshold.toString(),
            "expectedGrid" to "${config.expectedRows}x${config.expectedColumns}"
        )
    }
    
    /**
     * Calculate quality score for detected cards
     */
    private fun calculateQualityScore(cards: List<CardPosition>): Float {
        if (cards.isEmpty()) return 0f
        
        val avgConfidence = cards.map { it.confidence }.average().toFloat()
        val gridCompleteness = cards.size.toFloat() / 
            (com.memoryassist.fanfanlokmapper.utils.Constants.GRID_ROWS * 
             com.memoryassist.fanfanlokmapper.utils.Constants.GRID_COLUMNS)
        
        // Weight: 60% confidence, 40% completeness
        return (avgConfidence * 0.6f + gridCompleteness * 0.4f).coerceIn(0f, 1f)
    }
    
    /**
     * Calculate grid analysis score
     */
    private fun calculateGridScore(cards: List<CardPosition>): Float {
        val gridAnalysis = gridMapper.analyzeGridCompleteness(cards)
        
        // Weight different factors
        val completenessWeight = 0.4f
        val rowCoverageWeight = 0.3f
        val colCoverageWeight = 0.3f
        
        return (gridAnalysis.completeness * completenessWeight +
                gridAnalysis.rowCoverage * rowCoverageWeight +
                gridAnalysis.columnCoverage * colCoverageWeight).coerceIn(0f, 1f)
    }
    
    /**
     * Validate detection configuration
     */
    fun validateConfig(config: DetectionConfig): Result<Unit> {
        val errors = mutableListOf<String>()
        
        if (config.cannyLowerThreshold >= config.cannyUpperThreshold) {
            errors.add("Canny lower threshold must be less than upper threshold")
        }
        
        if (config.minCardArea >= config.maxCardArea) {
            errors.add("Minimum card area must be less than maximum card area")
        }
        
        if (config.expectedRows <= 0 || config.expectedColumns <= 0) {
            errors.add("Grid dimensions must be positive")
        }
        
        if (config.minConfidenceThreshold < 0 || config.minConfidenceThreshold > 1) {
            errors.add("Confidence threshold must be between 0 and 1")
        }
        
        return if (errors.isEmpty()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException(errors.joinToString("; ")))
        }
    }
}
package com.memoryassist.fanfanlokmapper.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import com.memoryassist.fanfanlokmapper.data.models.DetectionResult
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Repository interface for image processing and data persistence operations
 */
interface ImageRepositoryInterface {
    
    // Image Loading Operations
    
    /**
     * Load an image from URI
     * @param uri The URI of the image to load
     * @return Result containing the loaded Bitmap or error
     */
    suspend fun loadImage(uri: Uri): Result<Bitmap>
    
    /**
     * Load multiple images from URIs
     * @param uris List of image URIs
     * @return Flow of loaded Bitmaps with progress updates
     */
    fun loadImages(uris: List<Uri>): Flow<LoadImageProgress>
    
    // Processing Operations
    
    /**
     * Process a single image for card detection
     * @param bitmap The image to process
     * @return Detection result with card positions
     */
    suspend fun processImage(bitmap: Bitmap): DetectionResult
    
    /**
     * Process image from URI directly
     * @param uri The URI of the image to process
     * @return Detection result with card positions
     */
    suspend fun processImageFromUri(uri: Uri): DetectionResult
    
    // Storage Operations
    
    /**
     * Save detection results to local storage
     * @param result The detection result to save
     * @param filename Optional custom filename
     * @return File path where results were saved
     */
    suspend fun saveDetectionResult(
        result: DetectionResult,
        filename: String? = null
    ): Result<String>
    
    /**
     * Load previously saved detection results
     * @param filename The filename to load from
     * @return Previously saved detection result
     */
    suspend fun loadDetectionResult(filename: String): Result<DetectionResult>
    
    /**
     * Export detection results to JSON format
     * @param result The detection result to export
     * @param outputPath The path where to save the JSON file
     * @return File containing the exported JSON
     */
    suspend fun exportToJson(
        result: DetectionResult,
        outputPath: String
    ): Result<File>
    
    /**
     * Save processed image with overlay
     * @param originalBitmap The original image
     * @param cardPositions The detected card positions
     * @param outputPath Where to save the image with overlay
     * @return File containing the saved image
     */
    suspend fun saveImageWithOverlay(
        originalBitmap: Bitmap,
        cardPositions: List<CardPosition>,
        outputPath: String
    ): Result<File>
    
    // Cache Operations
    
    /**
     * Cache a processed result for quick retrieval
     * @param key Unique identifier for the cached result
     * @param result The detection result to cache
     */
    suspend fun cacheResult(key: String, result: DetectionResult)
    
    /**
     * Retrieve a cached result
     * @param key Unique identifier for the cached result
     * @return Cached detection result if exists
     */
    suspend fun getCachedResult(key: String): DetectionResult?
    
    /**
     * Clear all cached results
     */
    suspend fun clearCache()
    
    // History Operations
    
    /**
     * Get list of previously processed images
     * @return List of processing history entries
     */
    suspend fun getProcessingHistory(): List<ProcessingHistoryEntry>
    
    /**
     * Add entry to processing history
     * @param entry The history entry to add
     */
    suspend fun addToHistory(entry: ProcessingHistoryEntry)
    
    /**
     * Clear processing history
     */
    suspend fun clearHistory()
    
    // Validation Operations
    
    /**
     * Validate if a file is a supported image format
     * @param uri The URI to validate
     * @return True if the file is a supported image format
     */
    suspend fun isValidImageFile(uri: Uri): Boolean
    
    /**
     * Get image metadata without loading the full image
     * @param uri The URI of the image
     * @return Image metadata
     */
    suspend fun getImageMetadata(uri: Uri): Result<ImageMetadata>
    
    // Configuration Operations
    
    /**
     * Get current detection parameters
     * @return Current detection configuration
     */
    suspend fun getDetectionConfig(): DetectionConfig
    
    /**
     * Update detection parameters
     * @param config New detection configuration
     */
    suspend fun updateDetectionConfig(config: DetectionConfig)
    
    /**
     * Reset detection parameters to defaults
     */
    suspend fun resetDetectionConfig()
}

/**
 * Progress update for image loading operations
 */
data class LoadImageProgress(
    val currentIndex: Int,
    val totalCount: Int,
    val currentUri: Uri,
    val bitmap: Bitmap?,
    val error: Throwable?
) {
    val progressPercentage: Float get() = (currentIndex.toFloat() / totalCount) * 100
    val isComplete: Boolean get() = currentIndex == totalCount
    val isSuccess: Boolean get() = bitmap != null && error == null
}

/**
 * Entry in the processing history
 */
data class ProcessingHistoryEntry(
    val id: String,
    val timestamp: Long,
    val imagePath: String,
    val thumbnailPath: String? = null,
    val cardCount: Int,
    val processingTimeMs: Long,
    val isSuccessful: Boolean,
    val exportPath: String? = null
) {
    val formattedDate: String
        get() = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date(timestamp))
}

/**
 * Metadata about an image file
 */
data class ImageMetadata(
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val orientation: Int = 0,
    val hasAlpha: Boolean = false
) {
    val aspectRatio: Float get() = width.toFloat() / height
    val megapixels: Float get() = (width * height) / 1_000_000f
    val formattedSize: String get() = when {
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
        else -> "${sizeBytes / (1024 * 1024)} MB"
    }
}

/**
 * Configuration for card detection algorithms
 */
data class DetectionConfig(
    // Edge detection parameters
    val cannyLowerThreshold: Double = Constants.CANNY_THRESHOLD_1,
    val cannyUpperThreshold: Double = Constants.CANNY_THRESHOLD_2,
    val useAdaptiveThresholds: Boolean = true,
    
    // Preprocessing parameters
    val gaussianBlurSize: Int = Constants.GAUSSIAN_BLUR_SIZE,
    val enhanceContrast: Boolean = true,
    
    // Size filtering parameters
    val minCardArea: Int = Constants.MIN_CARD_AREA,
    val maxCardArea: Int = Constants.MAX_CARD_AREA,
    val useAdaptiveSizeFilter: Boolean = true,
    
    // Grid detection parameters
    val expectedRows: Int = Constants.GRID_ROWS,
    val expectedColumns: Int = Constants.GRID_COLUMNS,
    val minCardsForValidGrid: Int = Constants.MIN_CARDS_FOR_VALID_GRID,
    
    // Detection methods
    val useEdgeDetection: Boolean = true,
    val useColorDetection: Boolean = false,
    val useTemplateMatching: Boolean = false,
    
    // Performance parameters
    val maxImageWidth: Int = 2000,
    val maxImageHeight: Int = 2000,
    val enableDebugVisualization: Boolean = Constants.ENABLE_DETECTION_VISUALIZATION,
    
    // Quality parameters
    val minConfidenceThreshold: Float = 0.5f,
    val removeOverlapping: Boolean = true,
    val refineGridPositions: Boolean = true
) {
    companion object {
        /**
         * Create a configuration optimized for speed over accuracy
         */
        fun fast(): DetectionConfig = DetectionConfig(
            gaussianBlurSize = 3,
            enhanceContrast = false,
            maxImageWidth = 1500,
            maxImageHeight = 1500,
            refineGridPositions = false
        )
        
        /**
         * Create a configuration optimized for accuracy over speed
         */
        fun accurate(): DetectionConfig = DetectionConfig(
            enhanceContrast = true,
            useAdaptiveThresholds = true,
            useAdaptiveSizeFilter = true,
            minConfidenceThreshold = 0.7f,
            refineGridPositions = true,
            maxImageWidth = 3000,
            maxImageHeight = 3000
        )
        
        /**
         * Create a default balanced configuration
         */
        fun default(): DetectionConfig = DetectionConfig()
    }
}
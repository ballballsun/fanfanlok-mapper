package com.memoryassist.fanfanlokmapper.domain.usecase

import android.net.Uri
import com.memoryassist.fanfanlokmapper.data.models.DetectionResult
import com.memoryassist.fanfanlokmapper.domain.repository.ImageRepositoryInterface
import com.memoryassist.fanfanlokmapper.data.models.ProcessingHistoryEntry
import com.memoryassist.fanfanlokmapper.domain.repository.ImageMetadata
import com.memoryassist.fanfanlokmapper.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main use case for orchestrating the complete image processing pipeline
 */
@Singleton
class ProcessImageUseCase @Inject constructor(
    private val repository: ImageRepositoryInterface,
    private val detectCardsUseCase: DetectCardsUseCase
) {
    
    /**
     * Process a single image from URI
     */
    suspend fun processImage(
        imageUri: Uri,
        config: DetectionConfig? = null
    ): ProcessingResult = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        
        try {
            Logger.info("Starting image processing pipeline for URI: $imageUri")
            
            // Step 1: Validate image file
            val isValid = repository.isValidImageFile(imageUri)
            if (!isValid) {
                return@withContext ProcessingResult.InvalidImage(
                    uri = imageUri,
                    reason = "Unsupported image format or corrupted file"
                )
            }
            
            // Step 2: Check cache first
            val cacheKey = generateCacheKey(imageUri)
            val cachedResult = repository.getCachedResult(cacheKey)
            if (cachedResult != null) {
                Logger.info("Returning cached result for URI: $imageUri")
                return@withContext ProcessingResult.Success(
                    uri = imageUri,
                    detectionResult = cachedResult,
                    fromCache = true,
                    metadata = null
                )
            }
            
            // Step 3: Load and process image
            val loadResult = repository.loadImage(imageUri)
            val bitmap = loadResult.getOrElse { error ->
                return@withContext ProcessingResult.LoadError(
                    uri = imageUri,
                    error = error
                )
            }
            
            // Step 4: Process image for card detection
            val detectionResult = repository.processImage(bitmap)
            
            // Step 5: Cache the result
            repository.cacheResult(cacheKey, detectionResult)
            
            // Step 6: Add to history
            val historyEntry = ProcessingHistoryEntry(
                id = sessionId,
                fileName = imageUri.lastPathSegment ?: "unknown",
                uri = imageUri.toString(),
                timestamp = System.currentTimeMillis(),
                detectedCards = detectionResult.validCardsCount,
                processingTimeMs = System.currentTimeMillis() - startTime,
                isSuccessful = detectionResult.isSuccessful
            )
            repository.addToHistory(historyEntry)
            
            Logger.info("Successfully processed image: ${detectionResult.validCardsCount} cards detected")
            
            ProcessingResult.Success(
                uri = imageUri,
                detectionResult = detectionResult,
                fromCache = false,
                metadata = repository.getImageMetadata(imageUri).getOrNull()
            )
            
        } catch (e: Exception) {
            Logger.error("Unexpected error during image processing", e)
            ProcessingResult.UnexpectedError(
                uri = imageUri,
                error = e
            )
        }
    }
    
    /**
     * Reprocess an image with different configuration
     */
    suspend fun reprocessImage(
        imageUri: Uri,
        newConfig: DetectionConfig
    ): ProcessingResult = withContext(Dispatchers.IO) {
        // Clear cache for this image to force reprocessing
        val cacheKey = generateCacheKey(imageUri)
        repository.cacheResult(cacheKey, null!!) // Clear specific cache entry
        
        // Process with new configuration
        processImage(imageUri, newConfig)
    }
    
    /**
     * Process image and save with overlay
     */
    suspend fun processAndSaveWithOverlay(
        imageUri: Uri,
        outputPath: String,
        config: DetectionConfig? = null
    ): OverlayResult = withContext(Dispatchers.IO) {
        // Process the image
        val processingResult = processImage(imageUri, config)
        
        when (processingResult) {
            is ProcessingResult.Success -> {
                // Load original image
                val bitmapResult = repository.loadImage(imageUri)
                
                bitmapResult.fold(
                    onSuccess = { bitmap ->
                        // Save image with overlay
                        val saveResult = repository.saveImageWithOverlay(
                            bitmap,
                            processingResult.detectionResult.getValidCards(),
                            outputPath
                        )
                        
                        saveResult.fold(
                            onSuccess = { file ->
                                OverlayResult.Success(
                                    savedFile = file,
                                    cardCount = processingResult.detectionResult.validCardsCount
                                )
                            },
                            onFailure = { error ->
                                OverlayResult.SaveFailed(error)
                            }
                        )
                    },
                    onFailure = { error ->
                        OverlayResult.LoadFailed(error)
                    }
                )
            }
            else -> {
                OverlayResult.ProcessingFailed(
                    reason = getProcessingFailureReason(processingResult)
                )
            }
        }
    }
    
    /**
     * Process multiple images in batch with progress updates
     */
    fun processImages(
        imageUris: List<Uri>,
        config: DetectionConfig? = null
    ): kotlinx.coroutines.flow.Flow<BatchProcessingUpdate> = kotlinx.coroutines.flow.flow {
        val results = mutableListOf<ProcessingResult>()
        
        imageUris.forEachIndexed { index, uri ->
            emit(BatchProcessingUpdate.Progress(
                currentIndex = index + 1,
                totalCount = imageUris.size,
                progressPercentage = ((index + 1) * 100) / imageUris.size,
                completedResults = results.toList()
            ))
            
            val result = processImage(uri, config)
            results.add(result)
            
            emit(BatchProcessingUpdate.ItemCompleted(
                index = index,
                result = result,
                completedResults = results.toList()
            ))
        }
        
        emit(BatchProcessingUpdate.BatchCompleted(results.toList()))
    }
    
    /**
     * Process image and export results to file
     */
    suspend fun processAndExport(
        imageUri: Uri,
        exportPath: String,
        config: DetectionConfig? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        val processingResult = processImage(imageUri, config)
        
        when (processingResult) {
            is ProcessingResult.Success -> {
                try {
                    val exportData = processingResult.detectionResult.toExportFormat()
                    val jsonString = kotlinx.serialization.json.Json.encodeToString(
                        com.memoryassist.fanfanlokmapper.data.models.ExportData.serializer(),
                        exportData
                    )
                    
                    File(exportPath).writeText(jsonString)
                    
                    ExportResult.Success(
                        filePath = exportPath,
                        cardCount = processingResult.detectionResult.validCardsCount
                    )
                } catch (e: Exception) {
                    Logger.error("Failed to export results", e)
                    ExportResult.Error(e.message ?: "Export failed")
                }
            }
            is ProcessingResult.InvalidImage -> ExportResult.Error("Invalid image: ${processingResult.reason}")
            is ProcessingResult.LoadError -> ExportResult.Error("Load error: ${processingResult.error.message}")
            is ProcessingResult.DetectionFailed -> ExportResult.Error("Detection failed: ${processingResult.reason}")
            is ProcessingResult.ConfigurationError -> ExportResult.Error("Configuration error: ${processingResult.error.message}")
            is ProcessingResult.UnexpectedError -> ExportResult.Error("Unexpected error: ${processingResult.error.message}")
        }
    }
    
    /**
     * Clear all cached results and history
     */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        repository.clearCache()
        repository.clearHistory()
        Logger.info("Cleared all cached results and processing history")
    }
    
    /**
     * Get processing statistics
     */
    suspend fun getProcessingStatistics(): ProcessingStatistics = withContext(Dispatchers.IO) {
        val history = repository.getProcessingHistory()
        
        ProcessingStatistics(
            totalProcessed = history.size,
            successfulProcessed = history.count { it.isSuccessful },
            averageProcessingTime = if (history.isNotEmpty()) {
                history.map { it.processingTimeMs }.average().toLong()
            } else 0L,
            averageCardCount = if (history.isNotEmpty()) {
                history.map { it.detectedCards }.average().toInt()
            } else 0,
            recentHistory = history.sortedByDescending { it.timestamp }.take(10)
        )
    }
    
    /**
     * Generate cache key for an image URI
     */
    private fun generateCacheKey(uri: Uri): String {
        return "cache_${uri.toString().hashCode()}"
    }
    
    /**
     * Get failure reason from processing result
     */
    private fun getProcessingFailureReason(result: ProcessingResult): String {
        return when (result) {
            is ProcessingResult.InvalidImage -> result.reason
            is ProcessingResult.LoadError -> "Failed to load image: ${result.error.message}"
            is ProcessingResult.DetectionFailed -> result.reason
            is ProcessingResult.ConfigurationError -> "Configuration error: ${result.error.message}"
            is ProcessingResult.UnexpectedError -> "Unexpected error: ${result.error.message}"
            else -> "Unknown error"
        }
    }
}

/**
 * Sealed class representing different processing results
 */
sealed class ProcessingResult {
    abstract val uri: Uri
    
    data class Success(
        override val uri: Uri,
        val detectionResult: DetectionResult,
        val fromCache: Boolean,
        val metadata: com.memoryassist.fanfanlokmapper.domain.repository.ImageMetadata?
    ) : ProcessingResult()
    
    data class InvalidImage(
        override val uri: Uri,
        val reason: String
    ) : ProcessingResult()
    
    data class LoadError(
        override val uri: Uri,
        val error: Throwable
    ) : ProcessingResult()
    
    data class DetectionFailed(
        override val uri: Uri,
        val detectionResult: DetectionResult,
        val reason: String
    ) : ProcessingResult()
    
    data class ConfigurationError(
        override val uri: Uri,
        val error: Throwable
    ) : ProcessingResult()
    
    data class UnexpectedError(
        override val uri: Uri,
        val error: Throwable
    ) : ProcessingResult()
}

/**
 * Sealed class for batch processing updates
 */
sealed class BatchProcessingUpdate {
    data class Progress(
        val currentIndex: Int,
        val totalCount: Int,
        val progressPercentage: Int,
        val completedResults: List<ProcessingResult>
    ) : BatchProcessingUpdate()
    
    data class ItemCompleted(
        val index: Int,
        val result: ProcessingResult,
        val completedResults: List<ProcessingResult>
    ) : BatchProcessingUpdate()
    
    data class BatchCompleted(
        val allResults: List<ProcessingResult>
    ) : BatchProcessingUpdate()
}

/**
 * Batch processing progress data
 */
data class BatchProgress(
    val current: Int,
    val total: Int,
    val completed: Int
)

/**
 * Processing statistics data
 */
data class ProcessingStatistics(
    val totalProcessed: Int,
    val successfulProcessed: Int,
    val averageProcessingTime: Long,
    val averageCardCount: Int,
    val recentHistory: List<ProcessingHistoryEntry>
) {
    val successRate: Float get() = if (totalProcessed > 0) {
        (successfulProcessed.toFloat() / totalProcessed) * 100
    } else 0f
}

/**
 * Detection configuration
 */
data class DetectionConfig(
    val sensitivity: Float = 0.5f,
    val minCardSize: Int = 30,
    val maxCardSize: Int = 200,
    val useAdvancedFiltering: Boolean = true
) {
    companion object {
        fun default() = DetectionConfig()
    }
}

/**
 * Export result sealed class
 */
sealed class ExportResult {
    data class Success(
        val filePath: String,
        val cardCount: Int
    ) : ExportResult()
    
    data class Error(
        val message: String
    ) : ExportResult()
}

/**
 * Overlay save operation results
 */
sealed class OverlayResult {
    data class Success(
        val savedFile: java.io.File,
        val cardCount: Int
    ) : OverlayResult()
    
    data class LoadFailed(
        val error: Throwable
    ) : OverlayResult()
    
    data class SaveFailed(
        val error: Throwable
    ) : OverlayResult()
    
    data class ProcessingFailed(
        val reason: String
    ) : OverlayResult()
}
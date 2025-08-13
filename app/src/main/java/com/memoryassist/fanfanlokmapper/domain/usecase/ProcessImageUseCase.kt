package com.memoryassist.fanfanlokmapper.domain.usecase

import android.net.Uri
import com.memoryassist.fanfanlokmapper.data.models.DetectionResult
import com.memoryassist.fanfanlokmapper.domain.repository.ImageRepositoryInterface
import com.memoryassist.fanfanlokmapper.domain.repository.DetectionConfig
import com.memoryassist.fanfanlokmapper.domain.repository.ProcessingHistoryEntry
import com.memoryassist.fanfanlokmapper.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
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
            
            // Step 2: Get image metadata
            val metadataResult = repository.getImageMetadata(imageUri)
            val metadata = metadataResult.getOrNull()
            
            if (metadata != null) {
                Logger.info("Image metadata: ${metadata.width}x${metadata.height}, ${metadata.formattedSize}")
            }
            
            // Step 3: Check cache for previous results
            val cacheKey = generateCacheKey(imageUri)
            val cachedResult = repository.getCachedResult(cacheKey)
            
            if (cachedResult != null) {
                Logger.info("Found cached result for image")
                return@withContext ProcessingResult.Success(
                    uri = imageUri,
                    detectionResult = cachedResult,
                    fromCache = true,
                    metadata = metadata
                )
            }
            
            // Step 4: Load image
            val loadResult = repository.loadImage(imageUri)
            val bitmap = loadResult.getOrElse { error ->
                Logger.error("Failed to load image", error)
                return@withContext ProcessingResult.LoadError(
                    uri = imageUri,
                    error = error
                )
            }
            
            // Step 5: Get or use provided configuration
            val detectionConfig = config ?: repository.getDetectionConfig()
            
            // Step 6: Validate configuration
            val configValidation = detectCardsUseCase.validateConfig(detectionConfig)
            if (configValidation.isFailure) {
                return@withContext ProcessingResult.ConfigurationError(
                    uri = imageUri,
                    error = configValidation.exceptionOrNull() ?: Exception("Invalid configuration")
                )
            }
            
            // Step 7: Execute card detection
            val detectionResult = detectCardsUseCase.execute(bitmap, detectionConfig)
            
            // Step 8: Check if detection was successful
            if (!detectionResult.isSuccessful) {
                return@withContext ProcessingResult.DetectionFailed(
                    uri = imageUri,
                    detectionResult = detectionResult,
                    reason = detectionResult.errorMessage ?: "Unknown detection error"
                )
            }
            
            // Step 9: Cache the result
            repository.cacheResult(cacheKey, detectionResult)
            
            // Step 10: Save to history
            val processingTime = System.currentTimeMillis() - startTime
            val historyEntry = ProcessingHistoryEntry(
                id = sessionId,
                timestamp = System.currentTimeMillis(),
                imagePath = imageUri.toString(),
                cardCount = detectionResult.validCardsCount,
                processingTimeMs = processingTime,
                isSuccessful = true
            )
            repository.addToHistory(historyEntry)
            
            Logger.success("Image processing completed: ${detectionResult.validCardsCount} cards detected in ${processingTime}ms")
            
            ProcessingResult.Success(
                uri = imageUri,
                detectionResult = detectionResult,
                fromCache = false,
                metadata = metadata
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
     * Process multiple images with progress updates
     */
    fun processImages(
        imageUris: List<Uri>,
        config: DetectionConfig? = null
    ): Flow<BatchProcessingUpdate> = flow {
        val totalCount = imageUris.size
        val results = mutableListOf<ProcessingResult>()
        
        imageUris.forEachIndexed { index, uri ->
            // Emit progress update
            emit(BatchProcessingUpdate.Progress(
                currentIndex = index,
                totalCount = totalCount,
                currentUri = uri,
                completedResults = results.toList()
            ))
            
            // Process image
            val result = processImage(uri, config)
            results.add(result)
            
            // Emit individual result
            emit(BatchProcessingUpdate.ItemComplete(
                index = index,
                uri = uri,
                result = result
            ))
        }
        
        // Emit completion
        emit(BatchProcessingUpdate.Complete(
            totalProcessed = totalCount,
            results = results,
            successCount = results.count { it is ProcessingResult.Success },
            failureCount = results.count { it !is ProcessingResult.Success }
        ))
    }.flowOn(Dispatchers.IO)
    
    /**
     * Process and export results directly
     */
    suspend fun processAndExport(
        imageUri: Uri,
        exportPath: String,
        config: DetectionConfig? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        // Process the image
        val processingResult = processImage(imageUri, config)
        
        when (processingResult) {
            is ProcessingResult.Success -> {
                // Export to JSON
                val exportResult = repository.exportToJson(
                    processingResult.detectionResult,
                    exportPath
                )
                
                exportResult.fold(
                    onSuccess = { file ->
                        Logger.logExport(file.name, processingResult.detectionResult.validCardsCount)
                        ExportResult.Success(
                            exportedFile = file,
                            cardCount = processingResult.detectionResult.validCardsCount
                        )
                    },
                    onFailure = { error ->
                        ExportResult.ExportFailed(error)
                    }
                )
            }
            else -> {
                ExportResult.ProcessingFailed(
                    reason = when (processingResult) {
                        is ProcessingResult.InvalidImage -> processingResult.reason
                        is ProcessingResult.LoadError -> "Failed to load image: ${processingResult.error.message}"
                        is ProcessingResult.DetectionFailed -> processingResult.reason
                        is ProcessingResult.ConfigurationError -> "Configuration error: ${processingResult.error.message}"
                        is ProcessingResult.UnexpectedError -> "Unexpected error: ${processingResult.error.message}"
                        else -> "Unknown error"
                    }
                )
            }
        }
    }
}
package com.memoryassist.fanfanlokmapper.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import com.memoryassist.fanfanlokmapper.data.models.DetectionResult
import com.memoryassist.fanfanlokmapper.domain.usecase.DetectionConfig
import com.memoryassist.fanfanlokmapper.domain.repository.ImageMetadata
import com.memoryassist.fanfanlokmapper.domain.usecase.*
import com.memoryassist.fanfanlokmapper.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing image processing state
 */
@HiltViewModel
class ImageProcessingViewModel @Inject constructor(
    private val processImageUseCase: ProcessImageUseCase,
    private val detectCardsUseCase: DetectCardsUseCase,
    private val filterResultsUseCase: FilterResultsUseCase
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(ImageProcessingUiState())
    val uiState: StateFlow<ImageProcessingUiState> = _uiState.asStateFlow()
    
    // Processing State
    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()
    
    // Detection Result
    private val _detectionResult = MutableStateFlow<DetectionResult?>(null)
    val detectionResult: StateFlow<DetectionResult?> = _detectionResult.asStateFlow()
    
    // Card Positions (mutable for manual editing)
    private val _cardPositions = MutableStateFlow<List<CardPosition>>(emptyList())
    val cardPositions: StateFlow<List<CardPosition>> = _cardPositions.asStateFlow()
    
    // Processing Progress
    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()
    
    // Error messages
    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()
    
    // Success messages
    private val _successMessage = MutableSharedFlow<String>()
    val successMessage: SharedFlow<String> = _successMessage.asSharedFlow()
    
    // Current processing job
    private var currentProcessingJob: Job? = null
    
    // Detection configuration
    private val _detectionConfig = MutableStateFlow(DetectionConfig.default())
    val detectionConfig: StateFlow<DetectionConfig> = _detectionConfig.asStateFlow()
    
    /**
     * Process a single image
     */
    fun processImage(uri: Uri) {
        currentProcessingJob?.cancel()
        currentProcessingJob = viewModelScope.launch {
            try {
                Logger.logDetectionStart(uri.toString())
                _processingState.value = ProcessingState.Loading
                _processingProgress.value = 0.1f
                
                // Update UI state
                _uiState.update { 
                    it.copy(
                        selectedImageUri = uri,
                        isProcessing = true,
                        processingStage = "Loading image..."
                    )
                }
                
                _processingProgress.value = 0.2f
                _processingState.value = ProcessingState.Detecting
                _uiState.update { it.copy(processingStage = "Detecting cards...") }
                
                // Process the image (bypass cache for debugging)
                val result = processImageUseCase.processImage(
                    imageUri = uri,
                    config = _detectionConfig.value,
                    bypassCache = true // Always bypass cache for fresh detection
                )
                
                _processingProgress.value = 0.8f
                
                when (result) {
                    is ProcessingResult.Success -> {
                        handleSuccessfulProcessing(result)
                    }
                    is ProcessingResult.DetectionFailed -> {
                        handleDetectionFailure(result)
                    }
                    is ProcessingResult.InvalidImage -> {
                        handleInvalidImage(result)
                    }
                    is ProcessingResult.LoadError -> {
                        handleLoadError(result)
                    }
                    else -> {
                        handleUnexpectedError(result)
                    }
                }
                
            } catch (e: Exception) {
                handleException(e)
            } finally {
                _processingProgress.value = 1.0f
                _uiState.update { 
                    it.copy(
                        isProcessing = false,
                        processingStage = null
                    )
                }
            }
        }
    }
    
    /**
     * Process multiple images
     */
    fun processMultipleImages(uris: List<Uri>) {
        currentProcessingJob?.cancel()
        currentProcessingJob = viewModelScope.launch {
            _processingState.value = ProcessingState.BatchProcessing
            
            val results = mutableListOf<ProcessingResult>()
            
            processImageUseCase.processImages(uris, _detectionConfig.value)
                .collect { update ->
                    when (update) {
                        is BatchProcessingUpdate.Progress -> {
                            _processingProgress.value = update.progressPercentage / 100f
                            _uiState.update {
                                it.copy(
                                    processingStage = "Processing image ${update.currentIndex} of ${update.totalCount}",
                                    batchProgress = BatchProgress(
                                        current = update.currentIndex,
                                        total = update.totalCount,
                                        completed = update.completedResults.size
                                    )
                                )
                            }
                        }
                        is BatchProcessingUpdate.ItemCompleted -> {
                            results.add(update.result)
                        }
                        is BatchProcessingUpdate.BatchCompleted -> {
                            handleBatchComplete(update)
                        }
                    }
                }
        }
    }
    
    /**
     * Remove a card detection manually
     */
    fun removeCard(card: CardPosition) {
        viewModelScope.launch {
            val currentResult = _detectionResult.value ?: return@launch
            
            val updatedResult = currentResult.withCardRemoved(card.id)
            _detectionResult.value = updatedResult
            _cardPositions.value = updatedResult.getValidCards()
            
            _uiState.update {
                it.copy(
                    removedCardsCount = it.removedCardsCount + 1,
                    lastAction = UserAction.CardRemoved(card.id)
                )
            }
            
            Logger.logUserInteraction("Card removed", "Card ${card.id}")
            _successMessage.emit("Card removed from detection")
        }
    }
    
    /**
     * Restore all removed cards
     */
    fun restoreAllCards() {
        viewModelScope.launch {
            val currentResult = _detectionResult.value ?: return@launch
            
            val restoredCards = currentResult.cardPositions.map {
                it.copy(isManuallyRemoved = false)
            }
            
            val updatedResult = currentResult.copy(cardPositions = restoredCards)
            _detectionResult.value = updatedResult
            _cardPositions.value = updatedResult.getValidCards()
            
            _uiState.update {
                it.copy(
                    removedCardsCount = 0,
                    lastAction = UserAction.AllCardsRestored
                )
            }
            
            Logger.logUserInteraction("All cards restored")
            _successMessage.emit("All cards restored")
        }
    }
    
    /**
     * Clear all card detections
     */
    fun clearAllCards() {
        viewModelScope.launch {
            _detectionResult.value = null
            _cardPositions.value = emptyList()
            
            _uiState.update {
                it.copy(
                    removedCardsCount = 0,
                    lastAction = UserAction.AllCardsCleared
                )
            }
            
            Logger.logUserInteraction("All cards cleared")
            _successMessage.emit("All detections cleared")
        }
    }
    
    /**
     * Update detection configuration
     */
    fun updateDetectionConfig(config: DetectionConfig) {
        _detectionConfig.value = config
        Logger.info("Detection config updated")
    }
    
    /**
     * Use preset configuration
     */
    fun usePresetConfig(preset: ConfigPreset) {
        val config = when (preset) {
            ConfigPreset.FAST -> DetectionConfig.fast()
            ConfigPreset.BALANCED -> DetectionConfig.default()
            ConfigPreset.ACCURATE -> DetectionConfig.accurate()
        }
        updateDetectionConfig(config)
        
        viewModelScope.launch {
            _successMessage.emit("Using ${preset.name.lowercase()} configuration")
        }
    }
    
    /**
     * Export detection results
     */
    fun exportResults(outputPath: String) {
        viewModelScope.launch {
            val result = _detectionResult.value
            if (result == null) {
                _errorMessage.emit("No detection results to export")
                return@launch
            }
            
            _uiState.update { it.copy(isExporting = true) }
            
            try {
                val exportResult = processImageUseCase.processAndExport(
                    imageUri = _uiState.value.selectedImageUri ?: return@launch,
                    exportPath = outputPath,
                    config = _detectionConfig.value
                )
                
                when (exportResult) {
                    is ExportResult.Success -> {
                        _successMessage.emit("Exported ${exportResult.cardCount} cards to file")
                        _uiState.update {
                            it.copy(
                                lastExportPath = exportResult.filePath,
                                exportCount = it.exportCount + 1
                            )
                        }
                    }
                    is ExportResult.Error -> {
                        _errorMessage.emit("Export failed: ${exportResult.message}")
                    }
                }
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }
    
    /**
     * Reprocess current image with new configuration
     */
    fun reprocessCurrentImage() {
        val uri = _uiState.value.selectedImageUri
        if (uri != null) {
            processImage(uri)
        } else {
            viewModelScope.launch {
                _errorMessage.emit("No image selected for reprocessing")
            }
        }
    }
    
    /**
     * Cancel current processing
     */
    fun cancelProcessing() {
        currentProcessingJob?.cancel()
        _processingState.value = ProcessingState.Cancelled
        _uiState.update {
            it.copy(
                isProcessing = false,
                processingStage = null
            )
        }
        Logger.logUserInteraction("Processing cancelled")
    }
    
    // Private helper methods
    
    private suspend fun handleBatchComplete(update: BatchProcessingUpdate.BatchCompleted) {
        _processingState.value = ProcessingState.Success
        _uiState.update {
            it.copy(
                isProcessing = false,
                processingStage = null,
                batchProgress = null
            )
        }
        
        val successfulResults = update.allResults.filterIsInstance<ProcessingResult.Success>()
        val message = "Batch processing complete: ${successfulResults.size}/${update.allResults.size} images processed successfully"
        _successMessage.emit(message)
        
        Logger.info("Batch processing completed with ${successfulResults.size} successful results")
    }
    
    private suspend fun handleSuccessfulProcessing(result: ProcessingResult.Success) {
        _processingState.value = ProcessingState.Success
        _detectionResult.value = result.detectionResult
        _cardPositions.value = result.detectionResult.getValidCards()
        
        _uiState.update {
            it.copy(
                imageMetadata = result.metadata,
                lastProcessingTime = result.detectionResult.processingTimeMs,
                totalCardsDetected = result.detectionResult.totalCardsDetected,
                lastAction = UserAction.ImageProcessed
            )
        }
        
        val message = "Detected ${result.detectionResult.validCardsCount} cards" +
                     if (result.fromCache) " (from cache)" else ""
        _successMessage.emit(message)
        
        Logger.logDetectionResult(
            result.detectionResult.validCardsCount,
            result.detectionResult.processingTimeMs
        )
    }
    
    private suspend fun handleDetectionFailure(result: ProcessingResult.DetectionFailed) {
        _processingState.value = ProcessingState.Failed(result.reason)
        _detectionResult.value = result.detectionResult
        
        if (result.detectionResult.cardPositions.isNotEmpty()) {
            _cardPositions.value = result.detectionResult.getValidCards()
            _errorMessage.emit("Detection incomplete: ${result.reason}")
        } else {
            _errorMessage.emit("Detection failed: ${result.reason}")
        }
    }
    
    private suspend fun handleInvalidImage(result: ProcessingResult.InvalidImage) {
        _processingState.value = ProcessingState.Failed(result.reason)
        _errorMessage.emit("Invalid image: ${result.reason}")
    }
    
    private suspend fun handleLoadError(result: ProcessingResult.LoadError) {
        _processingState.value = ProcessingState.Failed(result.error.message ?: "Load error")
        _errorMessage.emit("Failed to load image: ${result.error.message}")
    }
    
    private suspend fun handleUnexpectedError(result: ProcessingResult) {
        val message = when (result) {
            is ProcessingResult.ConfigurationError -> "Configuration error: ${result.error.message}"
            is ProcessingResult.UnexpectedError -> "Unexpected error: ${result.error.message}"
            else -> "Unknown error occurred"
        }
        _processingState.value = ProcessingState.Failed(message)
        _errorMessage.emit(message)
    }
    
    private suspend fun handleException(e: Exception) {
        Logger.error("Processing exception", e)
        _processingState.value = ProcessingState.Failed(e.message ?: "Unknown error")
        _errorMessage.emit("Processing failed: ${e.message}")
    }
    
    /**
     * Clear all cached results to force fresh detection
     */
    fun clearCache() {
        viewModelScope.launch {
            try {
                processImageUseCase.clearAllData()
                Logger.success("🗑️ Cache cleared - next detection will be fresh")
            } catch (e: Exception) {
                Logger.error("Failed to clear cache", e)
            }
        }
    }
    
}

/**
 * UI State for image processing
 */
data class ImageProcessingUiState(
    val selectedImageUri: Uri? = null,
    val imageMetadata: ImageMetadata? = null,
    val isProcessing: Boolean = false,
    val isExporting: Boolean = false,
    val processingStage: String? = null,
    val lastProcessingTime: Long = 0,
    val totalCardsDetected: Int = 0,
    val removedCardsCount: Int = 0,
    val exportCount: Int = 0,
    val lastExportPath: String? = null,
    val batchProgress: BatchProgress? = null,
    val lastAction: UserAction? = null
)

/**
 * Batch processing progress
 */
data class BatchProgress(
    val current: Int,
    val total: Int,
    val completed: Int
) {
    val percentage: Float get() = (current.toFloat() / total) * 100
}

/**
 * Processing states
 */
sealed class ProcessingState {
    object Idle : ProcessingState()
    object Loading : ProcessingState()
    object Detecting : ProcessingState()
    object Filtering : ProcessingState()
    object Mapping : ProcessingState()
    object BatchProcessing : ProcessingState()
    object Success : ProcessingState()
    object Cancelled : ProcessingState()
    data class Failed(val reason: String) : ProcessingState()
}

/**
 * User actions for tracking
 */
sealed class UserAction {
    object ImageProcessed : UserAction()
    data class CardRemoved(val cardId: Int) : UserAction()
    object AllCardsCleared : UserAction()
    object AllCardsRestored : UserAction()
    data class BatchProcessed(val count: Int) : UserAction()
}

/**
 * Configuration presets
 */
enum class ConfigPreset {
    FAST,
    BALANCED,
    ACCURATE
}
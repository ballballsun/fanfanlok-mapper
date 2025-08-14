package com.memoryassist.fanfanlokmapper.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memoryassist.fanfanlokmapper.data.export.ExportFormat
import com.memoryassist.fanfanlokmapper.data.models.DetectionResult
import com.memoryassist.fanfanlokmapper.data.repository.ImageRepository
import com.memoryassist.fanfanlokmapper.data.repository.StorageStatistics
import com.memoryassist.fanfanlokmapper.domain.repository.ProcessingHistoryEntry
import com.memoryassist.fanfanlokmapper.domain.usecase.ProcessImageUseCase
import com.memoryassist.fanfanlokmapper.domain.usecase.ProcessingStatistics
import com.memoryassist.fanfanlokmapper.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Main ViewModel for managing application-wide state
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val imageRepository: ImageRepository,
    private val processImageUseCase: ProcessImageUseCase
) : ViewModel() {
    
    // UI State
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    
    // Navigation State
    private val _navigationState = MutableStateFlow(NavigationState.ImageSelection)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()
    
    // Processing History
    private val _processingHistory = MutableStateFlow<List<ProcessingHistoryEntry>>(emptyList())
    val processingHistory: StateFlow<List<ProcessingHistoryEntry>> = _processingHistory.asStateFlow()
    
    // Storage Statistics
    private val _storageStats = MutableStateFlow<StorageStatistics?>(null)
    val storageStats: StateFlow<StorageStatistics?> = _storageStats.asStateFlow()
    
    // App Settings
    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()
    
    // Debug Console Visibility
    private val _showDebugConsole = MutableStateFlow(false)
    val showDebugConsole: StateFlow<Boolean> = _showDebugConsole.asStateFlow()
    
    // Overlay Settings
    private val _overlaySettings = MutableStateFlow(OverlaySettings())
    val overlaySettings: StateFlow<OverlaySettings> = _overlaySettings.asStateFlow()
    
    // Export Settings
    private val _exportSettings = MutableStateFlow(ExportSettings())
    val exportSettings: StateFlow<ExportSettings> = _exportSettings.asStateFlow()
    
    // Toast Messages
    private val _toastMessage = MutableSharedFlow<ToastMessage>()
    val toastMessage: SharedFlow<ToastMessage> = _toastMessage.asSharedFlow()
    
    init {
        // Load initial data
        loadProcessingHistory()
        loadStorageStatistics()
        
        // Periodic updates
        startPeriodicUpdates()
    }
    
    /**
     * Navigate to a specific screen
     */
    fun navigateTo(destination: NavigationState) {
        _navigationState.value = destination
        Logger.logUserInteraction("Navigation", "Navigated to ${destination.javaClass.simpleName}")
    }
    
    /**
     * Toggle debug console visibility
     */
    fun toggleDebugConsole() {
        _showDebugConsole.value = !_showDebugConsole.value
        Logger.logUserInteraction("Debug console", if (_showDebugConsole.value) "shown" else "hidden")
    }
    
    /**
     * Update overlay settings
     */
    fun updateOverlaySettings(update: OverlaySettings.() -> OverlaySettings) {
        _overlaySettings.value = _overlaySettings.value.update()
        saveSettings()
    }
    
    /**
     * Update export settings
     */
    fun updateExportSettings(update: ExportSettings.() -> ExportSettings) {
        _exportSettings.value = _exportSettings.value.update()
        saveSettings()
    }
    
    /**
     * Update app settings
     */
    fun updateAppSettings(update: AppSettings.() -> AppSettings) {
        _appSettings.value = _appSettings.value.update()
        saveSettings()
    }
    
    /**
     * Load processing history
     */
    fun loadProcessingHistory() {
        viewModelScope.launch {
            try {
                val history = imageRepository.getProcessingHistory()
                _processingHistory.value = history
                
                _uiState.update {
                    it.copy(
                        totalProcessed = history.size,
                        lastProcessedTime = history.firstOrNull()?.timestamp
                    )
                }
            } catch (e: Exception) {
                Logger.error("Failed to load processing history", e)
                showToast("Failed to load history", ToastType.ERROR)
            }
        }
    }
    
    /**
     * Load storage statistics
     */
    fun loadStorageStatistics() {
        viewModelScope.launch {
            try {
                val stats = imageRepository.getStorageStatistics()
                _storageStats.value = stats
                
                _uiState.update {
                    it.copy(
                        cacheSize = stats.formattedCacheSize,
                        totalStorageUsed = stats.formattedTotalSize
                    )
                }
            } catch (e: Exception) {
                Logger.error("Failed to load storage statistics", e)
            }
        }
    }
    
    /**
     * Clear cache
     */
    fun clearCache() {
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingCache = true) }
            
            try {
                imageRepository.clearCache()
                loadStorageStatistics()
                showToast("Cache cleared successfully", ToastType.SUCCESS)
                Logger.info("Cache cleared by user")
            } catch (e: Exception) {
                Logger.error("Failed to clear cache", e)
                showToast("Failed to clear cache", ToastType.ERROR)
            } finally {
                _uiState.update { it.copy(isClearingCache = false) }
            }
        }
    }
    
    /**
     * Clear processing history
     */
    fun clearHistory() {
        viewModelScope.launch {
            try {
                imageRepository.clearHistory()
                _processingHistory.value = emptyList()
                showToast("History cleared", ToastType.SUCCESS)
                Logger.info("Processing history cleared")
            } catch (e: Exception) {
                Logger.error("Failed to clear history", e)
                showToast("Failed to clear history", ToastType.ERROR)
            }
        }
    }
    
    /**
     * Export all formats
     */
    fun exportAllFormats(result: DetectionResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            
            try {
                val exportResults = imageRepository.exportAllFormats(result)
                
                val successCount = exportResults.count { it.value.isSuccess }
                val failureCount = exportResults.count { it.value.isFailure }
                
                if (successCount > 0) {
                    showToast(
                        "Exported $successCount format(s) successfully",
                        ToastType.SUCCESS
                    )
                }
                
                if (failureCount > 0) {
                    showToast(
                        "$failureCount format(s) failed to export",
                        ToastType.WARNING
                    )
                }
                
                _uiState.update {
                    it.copy(lastExportTime = System.currentTimeMillis())
                }
                
            } catch (e: Exception) {
                Logger.error("Failed to export formats", e)
                showToast("Export failed", ToastType.ERROR)
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }
    
    /**
     * Import detection result from file
     */
    fun importDetectionResult(file: File) {
        viewModelScope.launch {
            try {
                val result = imageRepository.importDetectionResult(file)
                
                result.fold(
                    onSuccess = { detectionResult ->
                        showToast(
                            "Imported ${detectionResult.validCardsCount} cards",
                            ToastType.SUCCESS
                        )
                        Logger.info("Detection result imported from ${file.name}")
                    },
                    onFailure = { error ->
                        showToast("Import failed: ${error.message}", ToastType.ERROR)
                        Logger.error("Import failed", error)
                    }
                )
            } catch (e: Exception) {
                Logger.error("Failed to import detection result", e)
                showToast("Import failed", ToastType.ERROR)
            }
        }
    }
    
    /**
     * Get processing statistics
     */
    fun getProcessingStatistics() {
        viewModelScope.launch {
            try {
                val stats = processImageUseCase.getProcessingStatistics()
                
                _uiState.update {
                    it.copy(
                        processingStats = stats,
                        successRate = stats.successRate,
                        averageProcessingTime = stats.averageProcessingTime
                    )
                }
            } catch (e: Exception) {
                Logger.error("Failed to get processing statistics", e)
            }
        }
    }
    
    /**
     * Clean up old files
     */
    fun cleanupOldFiles() {
        viewModelScope.launch {
            try {
                imageRepository.cleanupOldCache()
                loadStorageStatistics()
                showToast("Cleanup completed", ToastType.SUCCESS)
                Logger.info("Old files cleaned up")
            } catch (e: Exception) {
                Logger.error("Failed to cleanup old files", e)
                showToast("Cleanup failed", ToastType.ERROR)
            }
        }
    }
    
    /**
     * Show toast message
     */
    private fun showToast(message: String, type: ToastType) {
        viewModelScope.launch {
            _toastMessage.emit(ToastMessage(message, type))
        }
    }
    
    /**
     * Save settings to persistent storage
     */
    private fun saveSettings() {
        // Settings would be saved to SharedPreferences or DataStore
        Logger.debug("Settings saved")
    }
    
    /**
     * Start periodic updates for statistics
     */
    private fun startPeriodicUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(30000) // Update every 30 seconds
                loadStorageStatistics()
                getProcessingStatistics()
            }
        }
    }
    
    /**
     * Handle app lifecycle events
     */
    fun onAppPaused() {
        saveSettings()
        Logger.info("App paused")
    }
    
    fun onAppResumed() {
        loadProcessingHistory()
        loadStorageStatistics()
        Logger.info("App resumed")
    }
}

/**
 * Main UI State
 */
data class MainUiState(
    val totalProcessed: Int = 0,
    val lastProcessedTime: Long? = null,
    val cacheSize: String = "0 KB",
    val totalStorageUsed: String = "0 KB",
    val isExporting: Boolean = false,
    val isClearingCache: Boolean = false,
    val lastExportTime: Long? = null,
    val processingStats: ProcessingStatistics? = null,
    val successRate: Float = 0f,
    val averageProcessingTime: Long = 0L
)

/**
 * Navigation states
 */
sealed class NavigationState {
    object ImageSelection : NavigationState()
    object Processing : NavigationState()
    object Results : NavigationState()
    object History : NavigationState()
    object Settings : NavigationState()
    object About : NavigationState()
}

/**
 * App settings
 */
data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val autoProcessOnSelect: Boolean = false,
    val saveProcessingHistory: Boolean = true,
    val enableDebugLogging: Boolean = true,
    val maxHistoryEntries: Int = 100,
    val autoCleanupDays: Int = 7,
    val defaultExportFormat: ExportFormat = ExportFormat.AUTOMATION,
    val showTutorialOnStart: Boolean = true,
    val enableHapticFeedback: Boolean = true,
    val enableSoundEffects: Boolean = false
)

/**
 * Overlay display settings
 */
data class OverlaySettings(
    val showConfidence: Boolean = true,
    val showGridPosition: Boolean = false,
    val showGridLines: Boolean = false,
    val overlayOpacity: Float = 1.0f,
    val overlayColor: OverlayColor = OverlayColor.GREEN,
    val strokeWidth: Float = 3f,
    val showCrosshair: Boolean = true,
    val enableAnimations: Boolean = true
)

/**
 * Export settings
 */
data class ExportSettings(
    val defaultFormat: ExportFormat = ExportFormat.AUTOMATION,
    val includeMetadata: Boolean = true,
    val prettyPrint: Boolean = true,
    val useRoundedCoordinates: Boolean = true,
    val exportPath: String? = null,
    val autoExportAfterProcessing: Boolean = false,
    val exportAllFormats: Boolean = false
)

/**
 * App themes
 */
enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Overlay colors
 */
enum class OverlayColor {
    GREEN,
    BLUE,
    RED,
    YELLOW,
    PURPLE,
    CYAN
}

/**
 * Toast message
 */
data class ToastMessage(
    val message: String,
    val type: ToastType,
    val duration: Long = 3000L
)

/**
 * Toast types
 */
enum class ToastType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

/**
 * Extension functions for state updates
 */
fun AppSettings.withTheme(theme: AppTheme) = copy(theme = theme)
fun AppSettings.withAutoProcess(enabled: Boolean) = copy(autoProcessOnSelect = enabled)
fun AppSettings.withDebugLogging(enabled: Boolean) = copy(enableDebugLogging = enabled)
fun AppSettings.withDefaultExportFormat(format: ExportFormat) = copy(defaultExportFormat = format)

fun OverlaySettings.withConfidence(show: Boolean) = copy(showConfidence = show)
fun OverlaySettings.withGridPosition(show: Boolean) = copy(showGridPosition = show)
fun OverlaySettings.withGridLines(show: Boolean) = copy(showGridLines = show)
fun OverlaySettings.withOpacity(opacity: Float) = copy(overlayOpacity = opacity.coerceIn(0f, 1f))
fun OverlaySettings.withColor(color: OverlayColor) = copy(overlayColor = color)

fun ExportSettings.withFormat(format: ExportFormat) = copy(defaultFormat = format)
fun ExportSettings.withMetadata(include: Boolean) = copy(includeMetadata = include)
fun ExportSettings.withPrettyPrint(pretty: Boolean) = copy(prettyPrint = pretty)
fun ExportSettings.withAutoExport(auto: Boolean) = copy(autoExportAfterProcessing = auto)
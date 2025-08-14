package com.memoryassist.fanfanlokmapper.data.repository

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.memoryassist.fanfanlokmapper.data.export.JsonExporter
import com.memoryassist.fanfanlokmapper.data.export.ExportFormat
import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import com.memoryassist.fanfanlokmapper.data.models.DetectionResult
import com.memoryassist.fanfanlokmapper.domain.repository.*
import com.memoryassist.fanfanlokmapper.utils.Constants
import com.memoryassist.fanfanlokmapper.utils.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of the ImageRepositoryInterface
 */
@Singleton
class ImageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jsonExporter: JsonExporter
) : ImageRepositoryInterface {
    
    // In-memory cache for detection results
    private val resultCache = ConcurrentHashMap<String, DetectionResult>()
    
    // Processing history stored in memory (could be persisted to database)
    private val processingHistory = mutableListOf<ProcessingHistoryEntry>()
    
    // Current detection configuration
    private var currentConfig = DetectionConfig.default()
    
    // JSON serializer
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Directories for storage
    private val cacheDir: File = File(context.cacheDir, "detection_cache")
    private val exportDir: File = File(context.getExternalFilesDir(null), "exports")
    private val historyDir: File = File(context.filesDir, "history")
    private val overlayDir: File = File(context.getExternalFilesDir(null), "overlays")
    
    init {
        // Ensure directories exist
        cacheDir.mkdirs()
        exportDir.mkdirs()
        historyDir.mkdirs()
        overlayDir.mkdirs()
        
        // Load saved history on initialization
        loadHistoryFromDisk()
    }
    
    // Image Loading Operations
    
    override suspend fun loadImage(uri: Uri): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IOException("Cannot open input stream for URI: $uri"))
            
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
            
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()
            
            if (bitmap != null) {
                Logger.info("Image loaded: ${bitmap.width}x${bitmap.height}")
                Result.success(bitmap)
            } else {
                Result.failure(IOException("Failed to decode bitmap from URI"))
            }
        } catch (e: Exception) {
            Logger.error("Failed to load image from URI: $uri", e)
            Result.failure(e)
        }
    }
    
    override fun loadImages(uris: List<Uri>): Flow<LoadImageProgress> = flow {
        uris.forEachIndexed { index, uri ->
            try {
                val result = loadImage(uri)
                val bitmap = result.getOrNull()
                
                emit(LoadImageProgress(
                    currentIndex = index + 1,
                    totalCount = uris.size,
                    currentUri = uri,
                    bitmap = bitmap,
                    error = result.exceptionOrNull()
                ))
            } catch (e: Exception) {
                emit(LoadImageProgress(
                    currentIndex = index + 1,
                    totalCount = uris.size,
                    currentUri = uri,
                    bitmap = null,
                    error = e
                ))
            }
        }
    }
    
    // Processing Operations
    
    override suspend fun processImage(bitmap: Bitmap): DetectionResult {
        // This would typically call the detection use case
        // For now, returning a placeholder
        return DetectionResult.failure(
            error = "Direct bitmap processing not implemented - use ProcessImageUseCase",
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )
    }
    
    override suspend fun processImageFromUri(uri: Uri): DetectionResult {
        val bitmapResult = loadImage(uri)
        return bitmapResult.fold(
            onSuccess = { bitmap -> processImage(bitmap) },
            onFailure = { error ->
                DetectionResult.failure(
                    error = "Failed to load image: ${error.message}",
                    imageWidth = 0,
                    imageHeight = 0
                )
            }
        )
    }
    
    // Storage Operations
    
    override suspend fun saveDetectionResult(
        result: DetectionResult,
        filename: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = filename ?: "detection_${System.currentTimeMillis()}.json"
            val file = File(cacheDir, fileName)
            
            val jsonContent = json.encodeToString(result)
            file.writeText(jsonContent)
            
            Logger.info("Detection result saved to: ${file.absolutePath}")
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Logger.error("Failed to save detection result", e)
            Result.failure(e)
        }
    }
    
    override suspend fun loadDetectionResult(filename: String): Result<DetectionResult> = 
        withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, filename)
                if (!file.exists()) {
                    return@withContext Result.failure(FileNotFoundException("File not found: $filename"))
                }
                
                val jsonContent = file.readText()
                val result = json.decodeFromString<DetectionResult>(jsonContent)
                
                Logger.info("Detection result loaded from: ${file.absolutePath}")
                Result.success(result)
            } catch (e: Exception) {
                Logger.error("Failed to load detection result", e)
                Result.failure(e)
            }
        }
    
    override suspend fun exportToJson(
        result: DetectionResult,
        outputPath: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = File(outputPath)
            val exportResult = jsonExporter.exportToJson(
                result, 
                file, 
                ExportFormat.AUTOMATION // Default to automation format
            )
            
            exportResult.fold(
                onSuccess = { Result.success(it) },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) {
            Logger.error("Failed to export to JSON", e)
            Result.failure(e)
        }
    }
    
    override suspend fun saveImageWithOverlay(
        originalBitmap: Bitmap,
        cardPositions: List<CardPosition>,
        outputPath: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Create a mutable copy of the bitmap
            val overlayBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(overlayBitmap)
            
            // Paint for drawing rectangles
            val paint = Paint().apply {
                color = Color.GREEN
                style = Paint.Style.STROKE
                strokeWidth = Constants.OVERLAY_STROKE_WIDTH
                isAntiAlias = true
            }
            
            // Draw rectangles for each card position
            cardPositions.forEach { card ->
                val rect = RectF(
                    card.left,
                    card.top,
                    card.right,
                    card.bottom
                )
                canvas.drawRoundRect(
                    rect,
                    Constants.OVERLAY_CORNER_RADIUS,
                    Constants.OVERLAY_CORNER_RADIUS,
                    paint
                )
                
                // Draw confidence text if enabled
                if (currentConfig.enableDebugVisualization) {
                    val textPaint = Paint().apply {
                        color = Color.GREEN
                        textSize = 12f
                        isAntiAlias = true
                    }
                    canvas.drawText(
                        "${(card.confidence * 100).toInt()}%",
                        card.centerX - 15,
                        card.centerY,
                        textPaint
                    )
                }
            }
            
            // Save the bitmap
            val file = File(outputPath)
            file.parentFile?.mkdirs()
            
            FileOutputStream(file).use { out ->
                overlayBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            Logger.info("Image with overlay saved to: ${file.absolutePath}")
            Result.success(file)
            
        } catch (e: Exception) {
            Logger.error("Failed to save image with overlay", e)
            Result.failure(e)
        }
    }
    
    // Cache Operations
    
    override suspend fun cacheResult(key: String, result: DetectionResult) {
        resultCache[key] = result
        
        // Also save to disk for persistence
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(cacheDir, "$key.cache")
                val jsonContent = json.encodeToString(result)
                cacheFile.writeText(jsonContent)
            } catch (e: Exception) {
                Logger.warning("Failed to persist cache to disk: ${e.message}")
            }
        }
    }
    
    override suspend fun getCachedResult(key: String): DetectionResult? {
        // Check in-memory cache first
        resultCache[key]?.let { return it }
        
        // Try to load from disk
        return withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(cacheDir, "$key.cache")
                if (cacheFile.exists()) {
                    val jsonContent = cacheFile.readText()
                    val result = json.decodeFromString<DetectionResult>(jsonContent)
                    resultCache[key] = result // Update in-memory cache
                    result
                } else {
                    null
                }
            } catch (e: Exception) {
                Logger.debug("No cached result found for key: $key")
                null
            }
        }
    }
    
    override suspend fun clearCache() {
        resultCache.clear()
        
        withContext(Dispatchers.IO) {
            try {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.extension == "cache") {
                        file.delete()
                    }
                }
                Logger.info("Cache cleared")
            } catch (e: Exception) {
                Logger.error("Failed to clear cache files", e)
            }
        }
    }
    
    // History Operations
    
    override suspend fun getProcessingHistory(): List<ProcessingHistoryEntry> {
        return processingHistory.toList()
    }
    
    override suspend fun addToHistory(entry: ProcessingHistoryEntry) {
        processingHistory.add(0, entry) // Add to beginning for recent first
        
        // Keep only last 100 entries
        if (processingHistory.size > 100) {
            processingHistory.removeAt(processingHistory.size - 1)
        }
        
        // Save to disk
        saveHistoryToDisk()
    }
    
    override suspend fun clearHistory() {
        processingHistory.clear()
        
        withContext(Dispatchers.IO) {
            try {
                val historyFile = File(historyDir, "history.json")
                if (historyFile.exists()) {
                    historyFile.delete()
                }
                Logger.info("History cleared")
            } catch (e: Exception) {
                Logger.error("Failed to clear history file", e)
            }
        }
    }
    
    // Validation Operations
    
    override suspend fun isValidImageFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri)
            val isImageType = mimeType?.startsWith("image/") == true
            
            if (!isImageType) return@withContext false
            
            // Check file extension
            val path = uri.path ?: return@withContext false
            val extension = path.substringAfterLast('.', "").lowercase()
            
            Constants.SUPPORTED_EXTENSIONS.contains(extension)
        } catch (e: Exception) {
            Logger.debug("Failed to validate image file: ${e.message}")
            false
        }
    }
    
    override suspend fun getImageMetadata(uri: Uri): Result<ImageMetadata> = 
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext Result.failure(IOException("Cannot open input stream"))
                
                // Get image dimensions without loading full bitmap
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()
                
                // Get file size
                val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                val fileSize = fileDescriptor?.statSize ?: 0L
                fileDescriptor?.close()
                
                // Get EXIF data if available
                val exifInputStream = context.contentResolver.openInputStream(uri)
                var orientation = 0
                if (exifInputStream != null) {
                    try {
                        val exif = ExifInterface(exifInputStream)
                        orientation = exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    } catch (e: Exception) {
                        // Ignore EXIF errors
                    }
                    exifInputStream.close()
                }
                
                val metadata = ImageMetadata(
                    width = options.outWidth,
                    height = options.outHeight,
                    sizeBytes = fileSize,
                    mimeType = options.outMimeType ?: "image/unknown",
                    orientation = orientation,
                    hasAlpha = options.outConfig == Bitmap.Config.ARGB_8888
                )
                
                Result.success(metadata)
            } catch (e: Exception) {
                Logger.error("Failed to get image metadata", e)
                Result.failure(e)
            }
        }
    
    // Configuration Operations
    
    override suspend fun getDetectionConfig(): DetectionConfig {
        return currentConfig
    }
    
    override suspend fun updateDetectionConfig(config: DetectionConfig) {
        currentConfig = config
        saveConfigToDisk()
    }
    
    override suspend fun resetDetectionConfig() {
        currentConfig = DetectionConfig.default()
        saveConfigToDisk()
    }
    
    // Private helper methods
    
    private fun saveHistoryToDisk() {
        try {
            val historyFile = File(historyDir, "history.json")
            val jsonContent = json.encodeToString(processingHistory)
            historyFile.writeText(jsonContent)
        }
package com.memoryassist.fanfanlokmapper.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private const val TAG = Constants.DEBUG_TAG
    private val isDebugEnabled = Constants.ENABLE_DEBUG_LOGGING
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    // Internal log storage for debug console
    private val _logEntries = mutableListOf<LogEntry>()
    val logEntries: List<LogEntry> get() = _logEntries.toList()
    
    data class LogEntry(
        val timestamp: String,
        val level: LogLevel,
        val message: String,
        val tag: String = TAG
    )
    
    enum class LogLevel(val displayName: String, val color: androidx.compose.ui.graphics.Color) {
        DEBUG("DEBUG", androidx.compose.ui.graphics.Color.Gray),
        INFO("INFO", androidx.compose.ui.graphics.Color.Blue),
        WARNING("WARN", androidx.compose.ui.graphics.Color(0xFFFF9800)),
        ERROR("ERROR", androidx.compose.ui.graphics.Color.Red),
        SUCCESS("SUCCESS", androidx.compose.ui.graphics.Color.Green)
    }
    
    fun debug(message: String, tag: String = TAG) {
        if (isDebugEnabled) {
            Log.d(tag, message)
            addLogEntry(LogLevel.DEBUG, message, tag)
        }
    }
    
    fun info(message: String, tag: String = TAG) {
        Log.i(tag, message)
        addLogEntry(LogLevel.INFO, message, tag)
    }
    
    fun warning(message: String, tag: String = TAG) {
        Log.w(tag, message)
        addLogEntry(LogLevel.WARNING, message, tag)
    }
    
    fun error(message: String, throwable: Throwable? = null, tag: String = TAG) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
            addLogEntry(LogLevel.ERROR, "$message: ${throwable.message}", tag)
        } else {
            Log.e(tag, message)
            addLogEntry(LogLevel.ERROR, message, tag)
        }
    }
    
    fun success(message: String, tag: String = TAG) {
        Log.i(tag, "✓ $message")
        addLogEntry(LogLevel.SUCCESS, message, tag)
    }
    
    // Detection-specific logging methods
    fun logDetectionStart(imagePath: String) {
        info("🔍 Starting detection for image: $imagePath")
    }
    
    fun logDetectionProgress(step: String, details: String = "") {
        debug("📊 Detection step: $step${if (details.isNotEmpty()) " - $details" else ""}")
    }
    
    fun logDetectionResult(cardsFound: Int, processingTimeMs: Long) {
        val message = "✅ Detection completed: $cardsFound cards found in ${processingTimeMs}ms"
        if (cardsFound >= Constants.MIN_CARDS_FOR_VALID_GRID) {
            success(message)
        } else {
            warning("⚠️ $message (Below minimum threshold)")
        }
    }
    
    fun logDetectionError(error: String, details: String = "") {
        error("❌ Detection failed: $error${if (details.isNotEmpty()) " - $details" else ""}")
    }
    
    fun logGridAnalysis(expectedCards: Int, detectedCards: Int, gridValid: Boolean) {
        val message = "📐 Grid Analysis: Expected $expectedCards, Found $detectedCards, Valid: $gridValid"
        if (gridValid) {
            info(message)
        } else {
            warning(message)
        }
    }
    
    fun logExport(filename: String, cardCount: Int) {
        success("💾 Exported $cardCount card positions to $filename")
    }
    
    fun logUserInteraction(action: String, details: String = "") {
        debug("👆 User action: $action${if (details.isNotEmpty()) " - $details" else ""}")
    }
    
    // Internal helper
    private fun addLogEntry(level: LogLevel, message: String, tag: String) {
        val timestamp = dateFormat.format(Date())
        val entry = LogEntry(timestamp, level, message, tag)
        
        synchronized(_logEntries) {
            _logEntries.add(entry)
            // Keep only last 100 entries to prevent memory issues
            if (_logEntries.size > 100) {
                _logEntries.removeAt(0)
            }
        }
    }
    
    fun clearLogs() {
        synchronized(_logEntries) {
            _logEntries.clear()
        }
        info("🗑️ Debug console cleared")
    }
    
    // Get formatted log for export
    fun getFormattedLogs(): String {
        return synchronized(_logEntries) {
            _logEntries.joinToString("\n") { entry ->
                "[${entry.timestamp}] ${entry.level.displayName}: ${entry.message}"
            }
        }
    }
}
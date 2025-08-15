package com.memoryassist.fanfanlokmapper.data.models

import kotlinx.serialization.Serializable

@Serializable
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
) {
    companion object {
        fun default() = AppSettings()
    }
}

enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

enum class ExportFormat {
    JSON,
    CSV,
    XML,
    AUTOMATION
}
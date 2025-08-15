package com.memoryassist.fanfanlokmapper.data.models

import kotlinx.serialization.Serializable

/**
 * Export data structure for JSON output
 */
@Serializable
data class ExportData(
    val cardPositions: List<SimpleCoordinate>,
    val metadata: ExportMetadata
)

/**
 * Metadata for exported detection results
 */
@Serializable
data class ExportMetadata(
    val totalCards: Int,
    val gridRows: Int,
    val gridColumns: Int,
    val imageWidth: Int,
    val imageHeight: Int,
    val processingTimeMs: Long,
    val averageConfidence: Float,
    val timestamp: Long
)
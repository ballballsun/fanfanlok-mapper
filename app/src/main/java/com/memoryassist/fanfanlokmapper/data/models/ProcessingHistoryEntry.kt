package com.memoryassist.fanfanlokmapper.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ProcessingHistoryEntry(
    val id: String,
    val fileName: String,
    val uri: String,
    val timestamp: Long,
    val detectedCards: Int,
    val processingTimeMs: Long,
    val isSuccessful: Boolean,
    val errorMessage: String? = null
) {
    val formattedDate: String
        get() = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
}
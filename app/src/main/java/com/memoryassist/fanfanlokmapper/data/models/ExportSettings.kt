package com.memoryassist.fanfanlokmapper.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ExportSettings(
    val defaultFormat: ExportFormat = ExportFormat.AUTOMATION,
    val includeMetadata: Boolean = true,
    val prettyPrint: Boolean = true,
    val useRoundedCoordinates: Boolean = true,
    val exportPath: String? = null,
    val autoExportAfterProcessing: Boolean = false,
    val exportAllFormats: Boolean = false
) {
    companion object {
        fun default() = ExportSettings()
    }
}
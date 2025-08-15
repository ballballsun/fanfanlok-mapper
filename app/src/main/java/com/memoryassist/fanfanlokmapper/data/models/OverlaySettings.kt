package com.memoryassist.fanfanlokmapper.data.models

import kotlinx.serialization.Serializable

@Serializable
data class OverlaySettings(
    val showConfidence: Boolean = true,
    val showGridPosition: Boolean = false,
    val showGridLines: Boolean = false,
    val overlayOpacity: Float = 1.0f,
    val overlayColor: OverlayColor = OverlayColor.GREEN,
    val strokeWidth: Float = 3f,
    val showCrosshair: Boolean = true,
    val enableAnimations: Boolean = true
) {
    companion object {
        fun default() = OverlaySettings()
    }
}

enum class OverlayColor {
    GREEN,
    BLUE,
    RED,
    YELLOW,
    PURPLE,
    CYAN
}
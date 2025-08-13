package com.memoryassist.fanfanlokmapper.data.models

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Represents the position and properties of a detected memory card
 */
@Serializable
data class CardPosition(
    val id: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float = 0f,
    val height: Float = 0f,
    val confidence: Float = 1.0f,
    val gridRow: Int = -1,
    val gridColumn: Int = -1,
    val isManuallyRemoved: Boolean = false
) {
    // Computed properties for convenience
    val left: Float get() = centerX - width / 2
    val top: Float get() = centerY - height / 2
    val right: Float get() = centerX + width / 2
    val bottom: Float get() = centerY + height / 2
    
    val area: Float get() = width * height
    val aspectRatio: Float get() = if (height > 0) width / height else 0f
    
    // Grid position validation
    val hasValidGridPosition: Boolean 
        get() = gridRow in 0 until com.memoryassist.fanfanlokmapper.utils.Constants.GRID_ROWS && 
                gridColumn in 0 until com.memoryassist.fanfanlokmapper.utils.Constants.GRID_COLUMNS
    
    // Rounded coordinates for export (reduces JSON file size)
    val roundedCenterX: Int get() = centerX.roundToInt()
    val roundedCenterY: Int get() = centerY.roundToInt()
    
    /**
     * Calculate distance to another card position
     */
    fun distanceTo(other: CardPosition): Float {
        val dx = centerX - other.centerX
        val dy = centerY - other.centerY
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Check if this card overlaps with another card
     */
    fun overlapsWith(other: CardPosition, tolerance: Float = 10f): Boolean {
        val overlapX = (right + tolerance) > other.left && 
                      (left - tolerance) < other.right
        val overlapY = (bottom + tolerance) > other.top && 
                      (top - tolerance) < other.bottom
        return overlapX && overlapY
    }
    
    /**
     * Check if this card position is within image bounds
     */
    fun isWithinBounds(imageWidth: Int, imageHeight: Int): Boolean {
        return centerX >= 0 && centerX <= imageWidth && 
               centerY >= 0 && centerY <= imageHeight
    }
    
    /**
     * Create a copy with updated grid position
     */
    fun withGridPosition(row: Int, column: Int): CardPosition {
        return copy(gridRow = row, gridColumn = column)
    }
    
    /**
     * Create a copy marked as manually removed
     */
    fun asRemoved(): CardPosition {
        return copy(isManuallyRemoved = true)
    }
    
    /**
     * Create a copy with adjusted confidence
     */
    fun withConfidence(newConfidence: Float): CardPosition {
        return copy(confidence = newConfidence.coerceIn(0f, 1f))
    }
    
    /**
     * Convert to simple coordinate format for external programs
     */
    fun toSimpleCoordinate(): SimpleCoordinate {
        return SimpleCoordinate(
            id = id,
            centerX = roundedCenterX,
            centerY = roundedCenterY
        )
    }
    
    companion object {
        /**
         * Create CardPosition from center point with default dimensions
         */
        fun fromCenter(
            id: Int, 
            centerX: Float, 
            centerY: Float, 
            defaultWidth: Float = 50f, 
            defaultHeight: Float = 70f
        ): CardPosition {
            return CardPosition(
                id = id,
                centerX = centerX,
                centerY = centerY,
                width = defaultWidth,
                height = defaultHeight
            )
        }
        
        /**
         * Create CardPosition from bounding rectangle
         */
        fun fromBounds(
            id: Int,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            confidence: Float = 1.0f
        ): CardPosition {
            val width = right - left
            val height = bottom - top
            val centerX = left + width / 2
            val centerY = top + height / 2
            
            return CardPosition(
                id = id,
                centerX = centerX,
                centerY = centerY,
                width = width,
                height = height,
                confidence = confidence
            )
        }
    }
}

/**
 * Simplified coordinate format for JSON export to external programs
 */
@Serializable
data class SimpleCoordinate(
    val id: Int,
    val centerX: Int,
    val centerY: Int
)
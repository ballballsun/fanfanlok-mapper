package com.memoryassist.fanfanlokmapper.utils

object Constants {
    // Grid Configuration
    const val GRID_ROWS = 4
    const val GRID_COLUMNS = 6
    const val TOTAL_CARDS = GRID_ROWS * GRID_COLUMNS // 24 cards
    
    // Detection Parameters
    const val MIN_CARD_AREA = 1000 // Minimum area in pixels for a valid card
    const val MAX_CARD_AREA = 50000 // Maximum area in pixels for a valid card
    const val MIN_CARD_WIDTH = 30 // Minimum width in pixels
    const val MIN_CARD_HEIGHT = 40 // Minimum height in pixels
    const val ASPECT_RATIO_MIN = 0.6 // Min width/height ratio for cards
    const val ASPECT_RATIO_MAX = 1.8 // Max width/height ratio for cards
    
    // OpenCV Edge Detection Parameters
    const val CANNY_THRESHOLD_1 = 50.0 // Lower threshold for edge detection
    const val CANNY_THRESHOLD_2 = 150.0 // Upper threshold for edge detection
    const val GAUSSIAN_BLUR_SIZE = 5 // Kernel size for Gaussian blur preprocessing
    const val CONTOUR_APPROXIMATION_EPSILON = 0.02 // Contour approximation accuracy
    
    // UI Constants
    const val OVERLAY_STROKE_WIDTH = 3f
    const val OVERLAY_CORNER_RADIUS = 8f
    const val LONG_PRESS_DURATION_MS = 500L
    
    // File Handling
    const val SUPPORTED_IMAGE_TYPES = "image/*"
    val SUPPORTED_EXTENSIONS = listOf("jpg", "jpeg", "png")
    const val JSON_EXPORT_FILENAME = "card_positions.json"
    
    // Debug
    const val DEBUG_TAG = "FanFanLokMapper"
    const val ENABLE_DEBUG_LOGGING = true
    const val ENABLE_DETECTION_VISUALIZATION = true
    
    // Grid Analysis
    const val GRID_TOLERANCE_PERCENTAGE = 0.15 // 15% tolerance for grid alignment
    const val MIN_CARDS_FOR_VALID_GRID = 20 // Minimum cards detected to consider grid valid
    
    // Color Detection (if using color-based detection as fallback)
    const val COLOR_TOLERANCE = 30 // RGB color tolerance for border detection
    
    // Export Settings
    const val JSON_INDENT_SPACES = 2
    const val COORDINATE_PRECISION = 1 // Decimal places for coordinates
}
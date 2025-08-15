package com.memoryassist.fanfanlokmapper.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Context
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.core.CvType
import org.opencv.core.Scalar
import org.opencv.core.Point
import org.opencv.core.Rect2d
import com.memoryassist.fanfanlokmapper.data.models.CardPosition
import java.io.InputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class ImageProcessor @Inject constructor(@ApplicationContext private val context: Context) {
    
    /**
     * Load image from URI and convert to Bitmap
     */
    fun loadBitmapFromUri(uri: Uri): Result<Bitmap> {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    Logger.info("Image loaded successfully: ${bitmap.width}x${bitmap.height}")
                    Result.success(bitmap)
                } else {
                    Logger.error("Failed to decode image from URI")
                    Result.failure(Exception("Failed to decode image"))
                }
            } ?: Result.failure(Exception("Cannot open input stream"))
        } catch (e: Exception) {
            Logger.error("Error loading image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Convert Bitmap to OpenCV Mat
     */
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        val bitmap32 = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        Utils.bitmapToMat(bitmap32, mat)
        Logger.debug("Converted Bitmap to Mat: ${mat.cols()}x${mat.rows()}")
        return mat
    }
    
    /**
     * Convert OpenCV Mat to Bitmap
     */
    fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }
    
    /**
     * Preprocess image for edge detection
     */
    fun preprocessForEdgeDetection(mat: Mat): Mat {
        val processed = Mat()
        
        Logger.info("🔧 Starting image preprocessing - Input: ${mat.cols()}x${mat.rows()}, channels=${mat.channels()}")
        
        // Convert to grayscale if needed
        val gray = if (mat.channels() > 1) {
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            Logger.logPreprocessing("Convert to grayscale", "${mat.cols()}x${mat.rows()}, channels=${mat.channels()}")
            grayMat
        } else {
            Logger.logPreprocessing("Already grayscale", "${mat.cols()}x${mat.rows()}")
            mat.clone()
        }
        
        // Apply Gaussian blur to reduce noise
        Imgproc.GaussianBlur(
            gray, 
            processed, 
            Size(Constants.GAUSSIAN_BLUR_SIZE.toDouble(), Constants.GAUSSIAN_BLUR_SIZE.toDouble()),
            0.0
        )
        
        Logger.logPreprocessing("Gaussian blur", "kernel=${Constants.GAUSSIAN_BLUR_SIZE}x${Constants.GAUSSIAN_BLUR_SIZE}")
        Logger.info("✅ Preprocessing complete - Output: ${processed.cols()}x${processed.rows()}")
        return processed
    }
    
    /**
     * Apply adaptive histogram equalization for better contrast
     */
    fun enhanceContrast(mat: Mat): Mat {
        val enhanced = Mat()
        
        // Ensure single channel
        val gray = if (mat.channels() > 1) {
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            grayMat
        } else {
            mat.clone()
        }
        
        // Apply CLAHE (Contrast Limited Adaptive Histogram Equalization)
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        clahe.apply(gray, enhanced)
        
        Logger.debug("Contrast enhancement applied")
        return enhanced
    }
    
    /**
     * Resize image if too large (for performance)
     */
    fun resizeIfNeeded(mat: Mat, maxWidth: Int = 2000, maxHeight: Int = 2000): Mat {
        val width = mat.cols()
        val height = mat.rows()
        
        if (width <= maxWidth && height <= maxHeight) {
            return mat
        }
        
        val scale = minOf(maxWidth.toDouble() / width, maxHeight.toDouble() / height)
        val newSize = Size(width * scale, height * scale)
        
        val resized = Mat()
        Imgproc.resize(mat, resized, newSize, 0.0, 0.0, Imgproc.INTER_AREA)
        
        Logger.info("Image resized from ${width}x${height} to ${resized.cols()}x${resized.rows()}")
        return resized
    }
    
    /**
     * Calculate adaptive thresholds based on image size
     */
    fun calculateAdaptiveThresholds(imageWidth: Int, imageHeight: Int): ThresholdParams {
        val totalArea = imageWidth * imageHeight
        
        // For memory card games in screenshots, cards are often much smaller
        // Based on your log: largest card was 4,531 pixels, most were 100-1,500
        // Set very aggressive minimum to capture small cards
        val minArea = 100  // Very low minimum to capture small cards
        val maxArea = maxOf(20000, totalArea / 30)  // Allow larger range
        
        // Use very low minimum dimensions to catch small cards
        val minWidth = 15   // Much lower than before (was 30)
        val minHeight = 20  // Much lower than before (was 40)
        
        Logger.info("Adaptive thresholds calculated - Image: ${imageWidth}x${imageHeight}, TotalArea: $totalArea")
        Logger.info("AGGRESSIVE thresholds: MinArea: $minArea, MaxArea: $maxArea")
        Logger.info("AGGRESSIVE dimensions: MinWidth: ${minWidth}, MinHeight: ${minHeight}")
        
        return ThresholdParams(
            minArea = minArea,
            maxArea = maxArea,
            minWidth = minWidth,
            minHeight = minHeight,
            aspectRatioMin = 0.2, // Very lenient aspect ratio
            aspectRatioMax = 5.0   // Very lenient aspect ratio
        )
    }
    
    /**
     * Create debug visualization with detected edges
     */
    fun createDebugVisualization(original: Mat, edges: Mat): Bitmap {
        val visualization = Mat()
        original.copyTo(visualization)
        
        // Overlay edges in green
        val greenEdges = Mat()
        Imgproc.cvtColor(edges, greenEdges, Imgproc.COLOR_GRAY2BGR)
        
        val green = Scalar(0.0, 255.0, 0.0)
        greenEdges.setTo(green, edges)
        
        // Blend with original
        org.opencv.core.Core.addWeighted(visualization, 0.7, greenEdges, 0.3, 0.0, visualization)
        
        return matToBitmap(visualization)
    }
    
    /**
     * Draw detection rectangles directly on the image
     */
    fun drawDetectionRectangles(
        originalBitmap: Bitmap,
        cardPositions: List<CardPosition>,
        showConfidence: Boolean = true,
        showGridPosition: Boolean = false
    ): Bitmap {
        val mat = bitmapToMat(originalBitmap)
        val result = Mat()
        mat.copyTo(result)
        
        Logger.info("Drawing ${cardPositions.size} rectangles on image ${mat.cols()}x${mat.rows()}")
        
        cardPositions.forEach { card ->
            if (!card.isManuallyRemoved) {
                drawSingleRectangle(result, card, showConfidence, showGridPosition)
            }
        }
        
        val processedBitmap = matToBitmap(result)
        Logger.info("Generated processed image with detection rectangles")
        return processedBitmap
    }
    
    /**
     * Draw a single detection rectangle on the image
     */
    private fun drawSingleRectangle(
        mat: Mat,
        card: CardPosition,
        showConfidence: Boolean,
        showGridPosition: Boolean
    ) {
        // Green color for rectangles (BGR format)
        val greenColor = Scalar(0.0, 255.0, 0.0)
        val textColor = Scalar(0.0, 255.0, 0.0)
        val thickness = 3
        
        // Draw rectangle
        val topLeft = Point(card.left.toDouble(), card.top.toDouble())
        val bottomRight = Point(card.right.toDouble(), card.bottom.toDouble())
        
        Imgproc.rectangle(mat, topLeft, bottomRight, greenColor, thickness)
        
        // Draw confidence text if enabled
        if (showConfidence) {
            val confidenceText = "${(card.confidence * 100).toInt()}%"
            val textPosition = Point(card.left.toDouble() + 5, card.top.toDouble() + 20)
            
            Imgproc.putText(
                mat,
                confidenceText,
                textPosition,
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.6,
                textColor,
                2
            )
        }
        
        // Draw grid position if enabled and available
        if (showGridPosition && card.hasValidGridPosition) {
            val gridText = "${card.gridRow + 1},${card.gridColumn + 1}"
            val textWidth = gridText.length * 12 // Approximate text width
            val textPosition = Point(
                card.right.toDouble() - textWidth - 5,
                card.bottom.toDouble() - 5
            )
            
            Imgproc.putText(
                mat,
                gridText,
                textPosition,
                Imgproc.FONT_HERSHEY_SIMPLEX,
                0.6,
                textColor,
                2
            )
        }
        
        // Draw center crosshair for better visibility
        val centerX = card.centerX.toDouble()
        val centerY = card.centerY.toDouble()
        val crosshairSize = 8.0
        
        // Horizontal line
        Imgproc.line(
            mat,
            Point(centerX - crosshairSize, centerY),
            Point(centerX + crosshairSize, centerY),
            greenColor,
            2
        )
        
        // Vertical line
        Imgproc.line(
            mat,
            Point(centerX, centerY - crosshairSize),
            Point(centerX, centerY + crosshairSize),
            greenColor,
            2
        )
        
        Logger.debug("Drew rectangle for card ${card.id} at (${card.left}, ${card.top}) to (${card.right}, ${card.bottom})")
    }
    
    /**
     * Save processed image to temporary file
     */
    fun saveProcessedImageToTemp(bitmap: Bitmap, filename: String = "processed_image.jpg"): Result<String> {
        return try {
            val tempDir = File(context.cacheDir, "processed_images")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            
            val imageFile = File(tempDir, filename)
            val outputStream = FileOutputStream(imageFile)
            
            outputStream.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            }
            
            Logger.info("Processed image saved to: ${imageFile.absolutePath}")
            Result.success(imageFile.absolutePath)
            
        } catch (e: IOException) {
            Logger.error("Failed to save processed image", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get temp directory for processed images
     */
    fun getTempImageDirectory(): File {
        val tempDir = File(context.cacheDir, "processed_images")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return tempDir
    }
    
    data class ThresholdParams(
        val minArea: Int,
        val maxArea: Int,
        val minWidth: Int,
        val minHeight: Int,
        val aspectRatioMin: Double,
        val aspectRatioMax: Double
    )
}
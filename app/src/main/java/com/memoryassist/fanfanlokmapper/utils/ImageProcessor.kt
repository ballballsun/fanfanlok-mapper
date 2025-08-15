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
import java.io.InputStream
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
        
        // Convert to grayscale if needed
        val gray = if (mat.channels() > 1) {
            val grayMat = Mat()
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY)
            grayMat
        } else {
            mat.clone()
        }
        
        // Apply Gaussian blur to reduce noise
        Imgproc.GaussianBlur(
            gray, 
            processed, 
            Size(Constants.GAUSSIAN_BLUR_SIZE.toDouble(), Constants.GAUSSIAN_BLUR_SIZE.toDouble()),
            0.0
        )
        
        Logger.debug("Preprocessing completed: Grayscale + Gaussian blur applied")
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
        
        // Each card should be roughly 1/24 to 1/30 of total area (accounting for spacing)
        val expectedCardArea = totalArea / 30.0
        
        // Allow 50% variation in size
        val minArea = (expectedCardArea * 0.5).toInt()
        val maxArea = (expectedCardArea * 1.5).toInt()
        
        // Calculate expected dimensions (assuming roughly 0.7 aspect ratio for cards)
        val expectedHeight = kotlin.math.sqrt(expectedCardArea / 0.7)
        val expectedWidth = expectedHeight * 0.7
        
        val minWidth = (expectedWidth * 0.5).toInt()
        val minHeight = (expectedHeight * 0.5).toInt()
        
        Logger.info("Adaptive thresholds calculated - MinArea: $minArea, MaxArea: $maxArea")
        
        return ThresholdParams(
            minArea = minArea,
            maxArea = maxArea,
            minWidth = minWidth,
            minHeight = minHeight,
            aspectRatioMin = Constants.ASPECT_RATIO_MIN,
            aspectRatioMax = Constants.ASPECT_RATIO_MAX
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
    
    data class ThresholdParams(
        val minArea: Int,
        val maxArea: Int,
        val minWidth: Int,
        val minHeight: Int,
        val aspectRatioMin: Double,
        val aspectRatioMax: Double
    )
}
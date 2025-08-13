package ai.costar.objectsegmentation

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.tasks.await

class SubjectSegmentationProcessor {

    private val segmenterOptions = SubjectSegmenterOptions.Builder()
        .enableForegroundBitmap()
        .enableForegroundConfidenceMask()
        .enableMultipleSubjects(
            SubjectSegmenterOptions.SubjectResultOptions.Builder()
                .enableSubjectBitmap()
                .enableConfidenceMask()
                .build()
        )
        .build()

    private val segmenter = SubjectSegmentation.getClient(segmenterOptions)

    data class SegmentationResult(
        val originalImage: Bitmap,
        val foregroundBitmap: Bitmap?,
        val subjectBitmaps: List<Bitmap>,
        val highlightedImage: Bitmap
    )

    suspend fun processImage(bitmap: Bitmap): SegmentationResult {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        return try {
            val result = segmenter.process(inputImage).await()

            // Get foreground bitmap
            val foregroundBitmap = result.foregroundBitmap

            // Get individual subject bitmaps
            val subjectBitmaps = result.subjects.mapNotNull { subject ->
                subject.bitmap
            }

            // Create highlighted image showing segmented areas
            val highlightedImage = createHighlightedImage(bitmap, result)

            SegmentationResult(
                originalImage = bitmap,
                foregroundBitmap = foregroundBitmap,
                subjectBitmaps = subjectBitmaps,
                highlightedImage = highlightedImage
            )
        } catch (e: Exception) {
            e.printStackTrace()
            SegmentationResult(
                originalImage = bitmap,
                foregroundBitmap = null,
                subjectBitmaps = emptyList(),
                highlightedImage = bitmap
            )
        }
    }

    suspend fun processImageProxy(imageProxy: ImageProxy): SegmentationResult? {
        val mediaImage = imageProxy.image ?: return null
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        return try {
            val result = segmenter.process(inputImage).await()

            // Convert to bitmap for display
            val bitmap = imageProxy.toBitmap()

            val foregroundBitmap = result.foregroundBitmap
            val subjectBitmaps = result.subjects.mapNotNull { subject ->
                subject.bitmap
            }
            val highlightedImage = createHighlightedImage(bitmap, result)

            SegmentationResult(
                originalImage = bitmap,
                foregroundBitmap = foregroundBitmap,
                subjectBitmaps = subjectBitmaps,
                highlightedImage = highlightedImage
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createHighlightedImage(originalBitmap: Bitmap, result: com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        val highlightedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)

        try {
            // Create an overlay to highlight segmented areas
            val colors = IntArray(width * height)

            // Initialize with transparent color
            for (i in colors.indices) {
                colors[i] = Color.TRANSPARENT
            }

            // Highlight foreground if available
            result.foregroundConfidenceMask?.let { mask ->
                for (i in 0 until width * height) {
                    if (i < mask.array().size && mask.array()[i] > 0.5f) {
                        colors[i] = Color.argb(100, 255, 0, 0) // Semi-transparent red
                    }
                }
            }

            // Highlight individual subjects with different colors
            val subjectColors = listOf(
                Color.argb(100, 0, 255, 0), // Green
                Color.argb(100, 0, 0, 255), // Blue
                Color.argb(100, 255, 255, 0), // Yellow
                Color.argb(100, 255, 0, 255), // Magenta
                Color.argb(100, 0, 255, 255)  // Cyan
            )

            result.subjects.forEachIndexed { index, subject ->
                val subjectMask = subject.confidenceMask
                val colorIndex = index % subjectColors.size
                val subjectColor = subjectColors[colorIndex]

                if (subjectMask != null) {
                    for (y in 0 until subject.height) {
                        for (x in 0 until subject.width) {
                            val maskIndex = y * subject.width + x
                            if (maskIndex < subjectMask.array().size && subjectMask.array()[maskIndex] > 0.5f) {
                                val imageX = subject.startX + x
                                val imageY = subject.startY + y
                                if (imageX < width && imageY < height) {
                                    val imageIndex = imageY * width + imageX
                                    if (imageIndex < colors.size) {
                                        colors[imageIndex] = subjectColor
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Create overlay bitmap
            val overlayBitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)

            // Composite original and overlay
            val canvas = android.graphics.Canvas(highlightedBitmap)
            canvas.drawBitmap(overlayBitmap, 0f, 0f, null)

        } catch (e: Exception) {
            e.printStackTrace()
            return originalBitmap
        }

        return highlightedBitmap
    }

    fun close() {
        segmenter.close()
    }
}

// Extension function to convert ImageProxy to Bitmap
private fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer // Y
    val vuBuffer = planes[2].buffer // VU

    val ySize = yBuffer.remaining()
    val vuSize = vuBuffer.remaining()

    val nv21 = ByteArray(ySize + vuSize)

    yBuffer.get(nv21, 0, ySize)
    vuBuffer.get(nv21, ySize, vuSize)

    val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, this.width, this.height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
    val imageBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

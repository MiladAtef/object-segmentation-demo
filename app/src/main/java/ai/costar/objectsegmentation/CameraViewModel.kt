package ai.costar.objectsegmentation

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class CameraViewModel : ViewModel() {

    private val segmentationProcessor = SubjectSegmentationProcessor()

    var cameraSelector = mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA)
        private set

    var segmentationResult = mutableStateOf<SubjectSegmentationProcessor.SegmentationResult?>(null)
        private set

    var isProcessing = mutableStateOf(false)
        private set

    var currentDisplayMode = mutableStateOf(DisplayMode.ORIGINAL)
        private set

    enum class DisplayMode {
        ORIGINAL,
        HIGHLIGHTED,
        FOREGROUND,
        SUBJECTS
    }

    fun switchCamera() {
        cameraSelector.value = if (cameraSelector.value == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    fun processImage(bitmap: Bitmap) {
        if (isProcessing.value) return

        viewModelScope.launch {
            isProcessing.value = true
            try {
                val result = segmentationProcessor.processImage(bitmap)
                segmentationResult.value = result
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessing.value = false
            }
        }
    }

    fun setDisplayMode(mode: DisplayMode) {
        currentDisplayMode.value = mode
    }

    fun clearResult() {
        segmentationResult.value = null
        currentDisplayMode.value = DisplayMode.ORIGINAL
    }

    override fun onCleared() {
        super.onCleared()
        segmentationProcessor.close()
    }
}

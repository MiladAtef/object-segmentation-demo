package ai.costar.objectsegmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel()
) {
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    LaunchedEffect(Unit) {
        cameraPermissionState.launchPermissionRequest()
    }

    val permissionStatus = cameraPermissionState.status
    when (permissionStatus) {
        PermissionStatus.Granted -> {
            CameraContent(viewModel = viewModel)
        }
        is PermissionStatus.Denied -> {
            if (permissionStatus.shouldShowRationale) {
                PermissionRationaleContent(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            } else {
                PermissionDeniedContent(
                    onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
                )
            }
        }
    }
}

@Composable
fun CameraContent(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val segmentationResult by viewModel.segmentationResult
    val isProcessing by viewModel.isProcessing
    val currentDisplayMode by viewModel.currentDisplayMode

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    if (segmentationResult != null) {
        SegmentationResultScreen(
            result = segmentationResult!!,
            displayMode = currentDisplayMode,
            isProcessing = isProcessing,
            onDisplayModeChange = viewModel::setDisplayMode,
            onBack = viewModel::clearResult
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            // Camera Preview
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        previewView = this
                        setupCamera(ctx, lifecycleOwner, this, viewModel) { capture ->
                            imageCapture = capture
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Camera Controls
            CameraControls(
                modifier = Modifier.align(Alignment.BottomCenter),
                onCaptureImage = {
                    imageCapture?.let { capture ->
                        captureImage(context, capture) { bitmap ->
                            viewModel.processImage(bitmap)
                        }
                    }
                },
                onSwitchCamera = {
                    viewModel.switchCamera()
                    previewView?.let { preview ->
                        setupCamera(context, lifecycleOwner, preview, viewModel) { capture ->
                            imageCapture = capture
                        }
                    }
                },
                isProcessing = isProcessing
            )

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(32.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Processing image...")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraControls(
    modifier: Modifier = Modifier,
    onCaptureImage: () -> Unit,
    onSwitchCamera: () -> Unit,
    isProcessing: Boolean
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Switch Camera Button
        FloatingActionButton(
            onClick = onSwitchCamera,
            modifier = Modifier.size(56.dp),
            containerColor = MaterialTheme.colorScheme.secondary
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Switch Camera"
            )
        }

        // Capture Button
        FloatingActionButton(
            onClick = onCaptureImage,
            modifier = Modifier.size(72.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Capture Image",
                modifier = Modifier.size(32.dp)
            )
        }

        // Placeholder for symmetry
        Spacer(modifier = Modifier.size(56.dp))
    }
}

@Composable
fun SegmentationResultScreen(
    result: SubjectSegmentationProcessor.SegmentationResult,
    displayMode: CameraViewModel.DisplayMode,
    isProcessing: Boolean,
    onDisplayModeChange: (CameraViewModel.DisplayMode) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Back to Camera"
                )
            }

            Text(
                text = "Segmentation Result",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(48.dp)) // Balance the layout
        }

        // Display Mode Selector
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CameraViewModel.DisplayMode.values()) { mode ->
                FilterChip(
                    onClick = { onDisplayModeChange(mode) },
                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    selected = displayMode == mode
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Image Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            val imageToShow = when (displayMode) {
                CameraViewModel.DisplayMode.ORIGINAL -> result.originalImage
                CameraViewModel.DisplayMode.HIGHLIGHTED -> result.highlightedImage
                CameraViewModel.DisplayMode.FOREGROUND -> result.foregroundBitmap ?: result.originalImage
                CameraViewModel.DisplayMode.SUBJECTS -> result.originalImage // Will show subjects below
            }

            Image(
                bitmap = imageToShow.asImageBitmap(),
                contentDescription = "Segmentation Result",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
        }

        // Subject Images (when in SUBJECTS mode)
        if (displayMode == CameraViewModel.DisplayMode.SUBJECTS && result.subjectBitmaps.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Detected Subjects (${result.subjectBitmaps.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(result.subjectBitmaps) { subjectBitmap ->
                        Image(
                            bitmap = subjectBitmap.asImageBitmap(),
                            contentDescription = "Subject",
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        // Information Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Segmentation Info",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Subjects detected: ${result.subjectBitmaps.size}")
                Text("Foreground detected: ${if (result.foregroundBitmap != null) "Yes" else "No"}")
                Text("Image size: ${result.originalImage.width} x ${result.originalImage.height}")
            }
        }
    }
}

@Composable
fun PermissionRationaleContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This app needs camera permission to capture images for object segmentation.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Grant Permission")
        }
    }
}

@Composable
fun PermissionDeniedContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Access Denied",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Please enable camera permission in app settings to use this feature.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("Try Again")
        }
    }
}

private fun setupCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    viewModel: CameraViewModel,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        val imageCapture = ImageCapture.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                viewModel.cameraSelector.value,
                preview,
                imageCapture
            )
            onImageCaptureReady(imageCapture)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun captureImage(
    context: Context,
    imageCapture: ImageCapture,
    onImageCaptured: (Bitmap) -> Unit
) {
    val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
        context.cacheDir.resolve("temp_image.jpg")
    ).build()

    imageCapture.takePicture(
        outputFileOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // Load the saved image as bitmap
                val bitmap = android.graphics.BitmapFactory.decodeFile(
                    context.cacheDir.resolve("temp_image.jpg").absolutePath
                )
                bitmap?.let(onImageCaptured)
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }
        }
    )
}

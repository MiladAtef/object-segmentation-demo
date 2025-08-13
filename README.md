# Object Segmentation Demo App

This is a demo Android application that demonstrates Google ML Kit's Subject Segmentation feature.

## Features

- **Camera Integration**: Full camera functionality with front/back camera switching
- **Real-time Object Segmentation**: Uses Google ML Kit to segment objects in captured images
- **Multiple Display Modes**:
  - Original image
  - Highlighted segmented areas
  - Foreground-only view
  - Individual segmented subjects
- **Interactive UI**: Easy-to-use camera controls and result viewing

## How to Use

1. **Launch the App**: The app will request camera permission on first launch
2. **Take a Photo**: Use the camera button to capture an image
3. **View Results**: The app will automatically process the image and show segmentation results
4. **Switch Display Modes**: Use the filter chips to toggle between different views:
   - **Original**: Shows the original captured image
   - **Highlighted**: Shows the original image with colored overlays on detected objects
   - **Foreground**: Shows only the detected foreground subjects
   - **Subjects**: Shows individual segmented objects as separate images
5. **Switch Cameras**: Use the camera switch button to toggle between front and back cameras
6. **Take Another Photo**: Tap the back button to return to camera mode

## Technical Implementation

### ML Kit Integration
- Uses `play-services-mlkit-subject-segmentation:16.0.0-beta1`
- Enables both foreground and multi-subject segmentation
- Processes images asynchronously using Kotlin coroutines

### Camera Implementation
- Built with CameraX library for modern camera functionality
- Supports both front and back cameras
- Handles camera permissions properly

### UI Framework
- Built entirely with Jetpack Compose
- Material 3 design system
- Responsive layout design

### Key Components

1. **SubjectSegmentationProcessor**: Handles ML Kit processing
2. **CameraViewModel**: Manages camera state and image processing
3. **CameraScreen**: Main UI component with camera preview and controls
4. **SegmentationResultScreen**: Displays processing results with multiple view modes

## Requirements

- Android API Level 26 or higher
- Camera permission
- Internet connection (for initial ML Kit model download)

## Build Instructions

1. Clone the repository
2. Open in Android Studio
3. Sync Gradle dependencies
4. Run on device or emulator with camera support

The app will automatically download the ML Kit model on first use if it's not already cached.

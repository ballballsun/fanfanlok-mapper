# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FanFanLokMapper is an Android application that detects and locates memory matching card positions from static screenshot images using computer vision. The app processes images to identify rectangular cards in a fixed 4×6 grid layout and exports coordinate data as JSON for external automation programs.

## Architecture

This is a modern Android app built with:
- **Jetpack Compose** for UI
- **MVVM architecture** with ViewModels
- **Clean Architecture** with domain/data layers
- **Dependency Injection** with Hilt (referenced in MainActivity but not fully configured)
- **OpenCV** for computer vision processing
- **Navigation Component** for screen navigation

### Key Directories Structure

- `app/src/main/java/com/memoryassist/fanfanlokmapper/`
  - `data/` - Data layer with models, repository implementations, export functionality
    - `models/` - CardPosition, DetectionResult data classes
    - `repository/` - ImageRepository implementation
    - `export/` - JsonExporter for coordinate data
  - `domain/` - Business logic layer
    - `repository/` - Repository interfaces
    - `usecase/` - DetectCardsUseCase, FilterResultsUseCase, ProcessImageUseCase
  - `ui/` - UI layer with Compose screens and components
    - `components/` - CardOverlay, DebugConsole, ImagePicker
    - `screens/` - MainScreen, ImageProcessingScreen
    - `theme/` - Material3 theming
  - `utils/` - Utility classes for image processing
    - `BorderDetector.kt` - Core card detection logic
    - `GridMapper.kt` - Grid layout mapping
    - `ImageProcessor.kt` - Image processing utilities
    - `Logger.kt` - Application logging
  - `viewmodel/` - ViewModels for state management

## Development Commands

### Build Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK  
./gradlew assembleRelease

# Build and install debug APK
./gradlew installDebug
```

### Testing Commands
```bash
# Run unit tests
./gradlew test

# Run unit tests with coverage
./gradlew testDebugUnitTestCoverage

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "*.BorderDetectorTest"
./gradlew test --tests "*.GridMapperTest" 
./gradlew test --tests "*.FilterResultsTest"
```

### Code Quality
```bash
# Lint check
./gradlew lint

# Generate lint report
./gradlew lintDebug
```

### Debugging
```bash
# Enable debug logging and install
./gradlew installDebug && adb logcat -s FanFanLokMapper
```

## Technical Specifications

- **Target SDK**: Android 15 (API 35)
- **Minimum SDK**: API 34
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Computer Vision**: OpenCV for Android
- **Input**: PNG/JPG image files via file picker
- **Output**: JSON format with card center coordinates
- **Grid Layout**: Fixed 4 rows × 6 columns (24 cards total)

## Core Workflow

1. User selects image via file picker (MainScreen)
2. Navigate to ImageProcessingScreen with image URI
3. Image processing pipeline:
   - BorderDetector identifies rectangular cards
   - Size filtering removes noise/false positives
   - GridMapper maps to 4×6 grid layout
   - Coordinate calculation for card centers
4. Visual overlay shows detected positions
5. User can manually remove incorrect detections (long press)
6. JSON export with coordinate data

## Key Files to Understand

- `utils/BorderDetector.kt` - Core computer vision logic for card detection
- `utils/GridMapper.kt` - Grid layout mapping and coordinate calculation
- `data/models/CardPosition.kt` - Primary data structure for card coordinates
- `ui/screens/ImageProcessingScreen.kt` - Main processing UI
- `MainActivity.kt` - Navigation setup and OpenCV initialization

## Testing Strategy

The project has unit tests for core utilities:
- `BorderDetectorTest.kt` - Tests card detection algorithms
- `GridMapperTest.kt` - Tests grid mapping logic  
- `FilterResultsTest.kt` - Tests result filtering

When modifying detection logic, always run the relevant tests and add new test cases for new functionality.
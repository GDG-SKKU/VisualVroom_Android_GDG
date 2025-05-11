# VisualVroom - Android Application

## Overview
VisualVroom is an accessibility application designed to help users with hearing impairments detect and identify approaching vehicles and emergency sirens through audio analysis and visual/haptic feedback. The system consists of an Android mobile application and a Wear OS app that work together to provide real-time alerts about vehicle sounds through visual cues and smartwatch vibration patterns.

## Features

### Mobile Application
- **Real-time Vehicle Sound Detection**: Identifies sirens, horns, and bicycle sounds
- **Direction Identification**: Shows whether sounds are coming from the left or right
- **Visual Alerts**: Clear visual indicators showing vehicle type and direction
- **Speech-to-Text**: Additional accessibility feature for transcribing speech
- **Continuous Audio Monitoring**: Background service that processes audio in intervals

### Wear OS Component
- **Haptic Feedback**: Provides distinct vibration patterns based on the type of vehicle detected
- **Direction Indication**: Communicates the direction of approaching vehicles through different vibration patterns
- **Low-power Operation**: Minimizes battery consumption while maintaining connectivity
- **Standalone UI**: Simple interface showing connection status

## Technical Details

### Mobile Application
- **Audio Processing**: Stereo channel recording with amplitude-based direction detection
- **Machine Learning Integration**: Connects to a Python backend that runs a Vision Transformer model for classification
- **Continuous Monitoring**: Records and analyzes audio in 3-5 second intervals
- **Low Resource Usage**: Optimized for battery efficiency during continuous usage

### Wear OS Component
- **Wearable Messaging API**: Uses Google's Wearable Message API for reliable communication
- **Custom Vibration Patterns**:
  - Sirens: Rapid, urgent patterns
  - Bicycle bells: Gentle, repeating patterns
  - Car horns: Strong, attention-grabbing patterns
- **Built with Wear OS Design Principles**: Follows material design for wearables

## Requirements
- Android 11+ (API level 30) for mobile app
- Wear OS 3.0+ for watch app
- Stereo microphone support on mobile device
- Bluetooth connectivity between phone and watch

## Installation

### Mobile Application
1. Clone the repository
2. Open the project in Android Studio Iguana or later
3. Update the server URL in `AudioRecorder.java` if using a custom backend
4. Build and run the application

### Wear OS App
1. The Wear OS app will be automatically installed on your paired watch when you install the mobile app
2. Alternatively, you can manually install the Wear OS APK from the release page

## Project Structure

### Mobile App Structure
- `/mobile/src/main/java/edu/skku/cs/visualvroomandroid/`
  - `MainActivity.java`: Main application entry point and tab controller
  - `AudioRecorderFragment.java`: UI for sound detection and visualization
  - `SpeechToTextFragment.java`: Speech transcription functionality
  - `AudioRecorder.java`: Core audio recording and processing
  - `AudioRecordingService.java`: Background service for continuous monitoring
  - `WearNotificationService.java`: Handles Wear OS communication

### Wear OS App Structure
- `/wear/src/main/java/edu/skku/cs/visualvroomandroid/presentation/`
  - `MainActivity.java`: Primary entry point and message receiver
- `/wear/src/main/res/layout/`
  - `activity_main.xml`: Main UI layout
  - `activity_main-round.xml`: Round watch optimization

## Permissions

### Mobile App Permissions
- `RECORD_AUDIO`: For sound detection
- `INTERNET`: For backend communication
- `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`: For future location-aware features
- `FOREGROUND_SERVICE_MICROPHONE`: For Android 14+ foreground service
- `POST_NOTIFICATIONS`: For Android 13+ notifications

### Wear OS App Permissions
- `VIBRATE`: For providing haptic feedback
- `WAKE_LOCK`: To ensure alerts are delivered even when the watch screen is off

## Backend Communication
The mobile app communicates with a PyTorch-based backend running a Vision Transformer model. The backend processes audio spectrograms and returns:
- Vehicle type classification
- Direction prediction
- Confidence score
- Notification decision

## Watch-Phone Communication
The system uses the Wearable Message API to send alerts from the phone to the watch when a vehicle is detected with high confidence.

### Message Paths
- `/vibration`: Triggers the watch to vibrate
- `/vehicle_alert`: Contains detailed information about detected vehicles

### Message Format
Messages use a JSON format containing:
- Vehicle type
- Direction
- Vibration pattern specifications

### Vibration Patterns
Different patterns are used based on the vehicle type:
- **Siren**: Urgent pattern with short pulses (100ms on, 100ms off, 100ms on, 100ms off, 300ms on)
- **Bike**: Moderate pattern with medium pulses (200ms on, 200ms off, 200ms on)
- **Horn**: Alert pattern with longer pulses (400ms on, 200ms off, 400ms on)

## Usage

### Mobile App
1. Launch the application
2. Navigate between the "Sound Detection" and "Speech to Text" tabs
3. On the Sound Detection tab, press the microphone button to start monitoring for vehicle sounds
4. When a vehicle is detected, the app will display the type and direction
5. Use the "Vibrate Watch" button to test the connection with your Wear OS device

### Wear OS App
1. The watch app runs automatically in the background
2. The main screen shows the current status of the connection
3. When a vehicle is detected by the phone, the watch will vibrate with the appropriate pattern

## Troubleshooting

### Watch Connectivity Issues
- Ensure Bluetooth is enabled on both devices
- Check that the mobile app is running
- Verify the watch is properly paired with the phone
- Disable battery optimization for the app on both devices

### Audio Detection Issues
- Ensure microphone permissions are granted
- Check that the device has a stereo microphone
- Position the phone with clear line of sight to potential sound sources
- Increase volume or adjust the phone position if sounds are too quiet
  

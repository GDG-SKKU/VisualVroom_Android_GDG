package edu.skku.cs.visualvroomandroid;

import android.animation.Animator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.airbnb.lottie.LottieAnimationView;
import android.widget.ImageButton;
import android.widget.ImageView;

public class AudioRecorderFragment extends Fragment {
    private static final String TAG = "AudioRecorderFragment";
    private static final int PROCESSING_INTERVAL_MS = 3000; // 3 seconds

    // UI Components
    private ImageButton micButton;
    private ImageView background;
    private TextView statusText;
    private LottieAnimationView vehicleAnimation;
    private View leftDirectionPanel;
    private View rightDirectionPanel;
    private static final float DIRECTION_PANEL_ALPHA = 0.3f;

    // State Management
    private enum RecordingState {
        IDLE,
        RECORDING,
        PROCESSING
    }
    private RecordingState currentState = RecordingState.IDLE;

    // Core Components
    private AudioRecorder audioRecorder;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable processingIntervalRunnable;
    private boolean isContinuousProcessing = false;

    // Animation control
    private Runnable hideAnimationRunnable;
    private boolean isAnimationShowing = false;
    private static final int ANIMATION_DURATION_MS = 3000; // How long to show the animation

    // Track quiet audio samples to prevent too many notifications
    private int consecutiveQuietSamples = 0;
    private static final int MAX_QUIET_NOTIFICATIONS = 2;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_audio_recorder_off, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeViews(view);
        initializeComponents();
        setupClickListeners();
    }

    private void initializeViews(View view) {
        micButton = view.findViewById(R.id.micButton);
        background = view.findViewById(R.id.centerIcon);
        statusText = view.findViewById(R.id.statusText);
        vehicleAnimation = view.findViewById(R.id.vehicleAnimation);
        leftDirectionPanel = view.findViewById(R.id.leftDirectionPanel);
        rightDirectionPanel = view.findViewById(R.id.rightDirectionPanel);

        // Set initial UI state
        updateUIState(RecordingState.IDLE);
        vehicleAnimation.setVisibility(View.GONE);
        leftDirectionPanel.setAlpha(0);
        rightDirectionPanel.setAlpha(0);

        // Animation control runnable
        hideAnimationRunnable = () -> {
            if (isAdded() && isAnimationShowing) {
                vehicleAnimation.cancelAnimation();
                vehicleAnimation.setVisibility(View.GONE);
                isAnimationShowing = false;
            }
        };
    }

    private void initializeComponents() {
        audioRecorder = new AudioRecorder(requireContext());

        // Create a runnable for periodic processing
        processingIntervalRunnable = new Runnable() {
            @Override
            public void run() {
                if (currentState == RecordingState.RECORDING && isContinuousProcessing) {
                    processCurrentRecording();
                    // Schedule next processing
                    mainHandler.postDelayed(this, PROCESSING_INTERVAL_MS);
                }
            }
        };
    }

    private void setupClickListeners() {
        micButton.setOnClickListener(v -> {
            switch (currentState) {
                case IDLE:
                    startRecording();
                    break;
                case RECORDING:
                    stopRecording();
                    break;
                case PROCESSING:
                    // Disable button during processing
                    break;
            }
        });
    }

    private void startRecording() {
        try {
            // Reset the quiet samples counter
            consecutiveQuietSamples = 0;

            // Cancel any existing vehicle animation
            cancelCurrentAnimation();

            // Reset direction panels
            leftDirectionPanel.animate().alpha(0).setDuration(300);
            rightDirectionPanel.animate().alpha(0).setDuration(300);

            // Start recording with continuous mode
            audioRecorder.startRecording();
            updateUIState(RecordingState.RECORDING);

            // Start continuous processing
            isContinuousProcessing = true;
            mainHandler.postDelayed(processingIntervalRunnable, PROCESSING_INTERVAL_MS);

            Log.d(TAG, "Recording started successfully with continuous processing");
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied: " + e.getMessage());
            showError("Recording permission denied");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recording: " + e.getMessage());
            showError("Failed to start recording");
        }
    }

    private void stopRecording() {
        try {
            // Stop continuous processing
            isContinuousProcessing = false;
            mainHandler.removeCallbacks(processingIntervalRunnable);
            mainHandler.removeCallbacks(hideAnimationRunnable);

            // Stop the recording
            audioRecorder.stopRecording();
            updateUIState(RecordingState.PROCESSING);

            // Cancel any running animations
            cancelCurrentAnimation();

            // Process the final recording
            processRecording();

            Log.d(TAG, "Recording stopped successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage());
            showError("Error stopping recording");
            updateUIState(RecordingState.IDLE);
        }
    }

    private void cancelCurrentAnimation() {
        // Cancel animation
        if (vehicleAnimation != null) {
            vehicleAnimation.cancelAnimation();
            vehicleAnimation.setVisibility(View.GONE);
        }
        isAnimationShowing = false;
        mainHandler.removeCallbacks(hideAnimationRunnable);
    }

    private void processCurrentRecording() {
        try {
            // Create a snapshot of the current recording without stopping
            audioRecorder.createSnapshot(new AudioRecorder.AudioRecorderCallback() {
                @Override
                public void onSuccess(AudioRecorder.InferenceResult result) {
                    mainHandler.post(() -> {
                        // Reset quiet samples counter
                        consecutiveQuietSamples = 0;

                        // Log inference details
                        Log.d(TAG, String.format("Inference result (continuous) - %s from %s (confidence: %.2f, shouldNotify: %b)",
                                result.getVehicleType(),
                                result.getDirection(),
                                result.getConfidence(),
                                result.getShouldNotify()));

                        // Force notification if confidence is high enough, regardless of shouldNotify flag
                        boolean shouldShow = result.getShouldNotify() || result.getConfidence() > 0.97;

                        if (shouldShow) {
                            String message = String.format("%s detected from %s (%.2f)",
                                    result.getVehicleType(),
                                    result.getDirection(),
                                    result.getConfidence());

                            // Show toast notification
                            showToast(message);

                            // Update status text to provide additional feedback
                            statusText.setText("Detected: " + result.getVehicleType() + " from " + result.getDirection());

                            // Always update animations when confidence is high
                            Log.d(TAG, "Showing animation for high confidence detection: " + result.getConfidence());
                            showVehicleAnimation(result.getVehicleType(), true);
                            updateDirectionIndicator(result.getDirection());

                            // Also send to watch through MainActivity if possible
                            try {
                                MainActivity activity = (MainActivity) getActivity();
                                if (activity != null) {
                                    activity.sendAlertToWatch(result.getVehicleType(), result.getDirection());
                                    Log.d(TAG, "Sent alert to watch via MainActivity");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to send alert to watch: " + e.getMessage());
                            }
                        }

                        // Remain in recording state
                        // We don't change the UI state since we're still recording
                    });
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error in continuous processing: " + error);
                    // We don't show errors to the user during continuous processing
                    // to avoid disrupting the user experience
                }

                @Override
                public void onQuietAudio() {
                    mainHandler.post(() -> {
                        consecutiveQuietSamples++;

                        // Only notify the user about quiet audio occasionally
                        if (consecutiveQuietSamples <= MAX_QUIET_NOTIFICATIONS) {
                            Log.d(TAG, "Audio too quiet for processing");
                            statusText.setText("Recording... (sound level low)");
                        }

                        // We remain in recording state
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in continuous processing: " + e.getMessage());
        }
    }

    private void processRecording() {
        audioRecorder.sendToBackend(new AudioRecorder.AudioRecorderCallback() {
            @Override
            public void onSuccess(AudioRecorder.InferenceResult result) {
                mainHandler.post(() -> {
                    // Log inference details
                    Log.d(TAG, String.format("Inference result - %s from %s (confidence: %.2f)",
                            result.getVehicleType(),
                            result.getDirection(),
                            result.getConfidence()));

                    // Show result to user
                    String message = String.format("%s detected from %s",
                            result.getVehicleType(),
                            result.getDirection());
                    showToast(message);

                    // Handle high-confidence detection
                    if (result.getShouldNotify()) {
                        Log.d(TAG, "High confidence detection: " + message);
                        showVehicleAnimation(result.getVehicleType(), false);
                        updateDirectionIndicator(result.getDirection());
                    } else {
                        cancelCurrentAnimation();
                        leftDirectionPanel.animate().alpha(0).setDuration(300);
                        rightDirectionPanel.animate().alpha(0).setDuration(300);
                    }

                    updateUIState(RecordingState.IDLE);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    handleProcessingError(error);
                });
            }

            @Override
            public void onQuietAudio() {
                mainHandler.post(() -> {
                    Log.d(TAG, "Final recording was too quiet");
                    showToast("Recording was too quiet - no sounds detected");
                    updateUIState(RecordingState.IDLE);
                });
            }
        });
    }

    private void showVehicleAnimation(String vehicleType, boolean isDuringRecording) {
        if (!isAdded()) return;

        // Remove any pending animation hide callbacks
        mainHandler.removeCallbacks(hideAnimationRunnable);

        int animationResource;
        switch (vehicleType.toLowerCase()) {
            case "siren":
                animationResource = R.raw.siren;
                break;
            case "bike":
                animationResource = R.raw.bike;
                break;
            case "horn":
                animationResource = R.raw.car_horn;
                break;
            default:
                cancelCurrentAnimation();
                return;
        }

        // First, cancel any existing animation
        vehicleAnimation.cancelAnimation();
        vehicleAnimation.clearAnimation();
        vehicleAnimation.removeAllAnimatorListeners();

        // Set up new animation
        vehicleAnimation.setAnimation(animationResource);
        vehicleAnimation.setVisibility(View.VISIBLE);

        if (isDuringRecording) {
            // During recording, we want to auto-hide after a few seconds
            vehicleAnimation.setRepeatCount(1);
            isAnimationShowing = true;

            // Schedule hiding the animation
            mainHandler.postDelayed(hideAnimationRunnable, ANIMATION_DURATION_MS);
        } else {
            // When recording stops, we show the animation for a bit longer
            vehicleAnimation.setRepeatCount(3);

            // Add animation end listener
            vehicleAnimation.addAnimatorListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {}

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (isAdded()) {
                        vehicleAnimation.setVisibility(View.GONE);
                    }
                    vehicleAnimation.removeAllAnimatorListeners();
                }

                @Override
                public void onAnimationCancel(Animator animation) {}

                @Override
                public void onAnimationRepeat(Animator animation) {}
            });
        }

        // Start the animation
        vehicleAnimation.playAnimation();
        Log.d(TAG, "Started vehicle animation: " + vehicleType + " (during recording: " + isDuringRecording + ")");
    }

    private void updateDirectionIndicator(String direction) {
        if (!isAdded()) return;

        // Reset both panels
        leftDirectionPanel.animate().alpha(0).setDuration(300);
        rightDirectionPanel.animate().alpha(0).setDuration(300);

        // Activate the appropriate panel
        if ("L".equalsIgnoreCase(direction)) {
            leftDirectionPanel.animate()
                    .alpha(DIRECTION_PANEL_ALPHA)
                    .setDuration(300)
                    .withEndAction(() -> {
                        // Auto-hide after 3 seconds
                        mainHandler.postDelayed(() -> {
                            if (isAdded()) {
                                leftDirectionPanel.animate()
                                        .alpha(0)
                                        .setDuration(300);
                            }
                        }, 3000);
                    });
        } else if ("R".equalsIgnoreCase(direction)) {
            rightDirectionPanel.animate()
                    .alpha(DIRECTION_PANEL_ALPHA)
                    .setDuration(300)
                    .withEndAction(() -> {
                        // Auto-hide after 3 seconds
                        mainHandler.postDelayed(() -> {
                            if (isAdded()) {
                                rightDirectionPanel.animate()
                                        .alpha(0)
                                        .setDuration(300);
                            }
                        }, 3000);
                    });
        }
    }

    private void handleProcessingError(String error) {
        Log.e(TAG, "Processing error: " + error);
        showError("Processing failed: " + error);
        updateUIState(RecordingState.IDLE);
    }

    private void updateUIState(RecordingState newState) {
        if (!isAdded()) return;

        currentState = newState;

        switch (newState) {
            case IDLE:
                // Stop the mic animation when idle
                micButton.setImageResource(R.drawable.group_15);
                background.setImageResource(R.drawable.frame_1);
                micButton.setEnabled(true);

                // Update other UI elements
                statusText.setText("Ready to record");
                break;

            case RECORDING:
                // Start the mic animation when recording
                micButton.setImageResource(R.drawable.group_16);
                background.setImageResource(R.drawable.frame_1__1_);
                micButton.setEnabled(true);

                // Update other UI elements
                statusText.setText("Recording...");
                break;

            case PROCESSING:
                // Disable mic button during processing
                micButton.setEnabled(false);

                // Update other UI elements
                statusText.setText("Processing...");
                break;
        }
    }

    private void showError(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showToast(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        // Clean up our continuous processing timer
        mainHandler.removeCallbacks(processingIntervalRunnable);
        mainHandler.removeCallbacks(hideAnimationRunnable);

        if (currentState == RecordingState.RECORDING) {
            audioRecorder.stopRecording();
        }

        if (vehicleAnimation != null) {
            vehicleAnimation.cancelAnimation();
        }


        // Reset direction panels
        if (leftDirectionPanel != null) {
            leftDirectionPanel.animate().alpha(0).setDuration(0);
        }
        if (rightDirectionPanel != null) {
            rightDirectionPanel.animate().alpha(0).setDuration(0);
        }

        super.onDestroyView();
    }
}

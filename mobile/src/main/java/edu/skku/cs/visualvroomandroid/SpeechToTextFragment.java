package edu.skku.cs.visualvroomandroid;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class SpeechToTextFragment extends Fragment {
    private static final String TAG = "SpeechToTextFragment";

    // Backend URLs - now we'll have a single backend for both transcription and image generation
    private static final String BACKEND_URL = "http://211.211.177.45:8017";
    private static final String TRANSCRIBE_ENDPOINT = BACKEND_URL + "/transcribe";
    private static final String GENERATE_IMAGE_ENDPOINT = BACKEND_URL + "/generate_image";

    // Audio recording constants
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;

    // UI Components
    private EditText transcribedText;
    private FloatingActionButton micButton;
    private ImageButton clearButton;
    private ImageButton copyButton;
    private ImageView generatedImageView;
    private ProgressBar progressBar;

    // Recording components
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private ByteArrayOutputStream audioBuffer;

    // HTTP client
    private final OkHttpClient client;

    public SpeechToTextFragment() {
        // Initialize OkHttpClient with longer timeouts for API calls
        client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_speech_text, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find views
        transcribedText = view.findViewById(R.id.transcribedText);
        micButton = view.findViewById(R.id.micButton);
        clearButton = view.findViewById(R.id.clearButton);
        copyButton = view.findViewById(R.id.copyButton);

        // These views need to be added to your layout
        generatedImageView = view.findViewById(R.id.generatedImageView);
        progressBar = view.findViewById(R.id.loadingIndicator);

        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }

        if (generatedImageView != null) {
            generatedImageView.setVisibility(View.GONE);
        }

        // Set up click listeners
        micButton.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });

        clearButton.setOnClickListener(v -> {
            transcribedText.setText("");
            if (generatedImageView != null) {
                generatedImageView.setVisibility(View.GONE);
            }
        });

        copyButton.setOnClickListener(v -> {
            String text = transcribedText.getText().toString();
            if (!TextUtils.isEmpty(text)) {
                ClipboardManager clipboard = (ClipboardManager)
                        requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Transcribed Text", text);
                clipboard.setPrimaryClip(clip);
                showToast("Text copied to clipboard");
            }
        });
    }

    private void startRecording() {
        if (isRecording) return;

        try {
            // Create new buffer for recording
            audioBuffer = new ByteArrayOutputStream();

            audioRecord = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build())
                    .setBufferSizeInBytes(BUFFER_SIZE)
                    .build();

            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("Failed to initialize AudioRecord");
            }

            isRecording = true;
            micButton.setImageResource(R.drawable.ic_mic_active);
            showToast("Recording started");

            recordingThread = new Thread(() -> {
                byte[] buffer = new byte[BUFFER_SIZE];
                audioRecord.startRecording();

                while (isRecording) {
                    int bytesRead = audioRecord.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        synchronized (audioBuffer) {
                            audioBuffer.write(buffer, 0, bytesRead);
                        }
                    }
                }
            });
            recordingThread.start();

        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied: " + e.getMessage());
            showToast("Permission denied for audio recording");
            resetRecordingState();
        } catch (Exception e) {
            Log.e(TAG, "Error starting recording: " + e.getMessage());
            showToast("Error starting audio recording");
            resetRecordingState();
        }
    }

    private void stopRecording() {
        if (!isRecording) return;

        try {
            isRecording = false;
            micButton.setImageResource(R.drawable.ic_mic);
            showToast("Processing audio...");

            // Stop the AudioRecord
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
            }

            // Wait for recording thread to finish
            if (recordingThread != null) {
                recordingThread.join();
            }

            // Get the final audio data
            byte[] audioData;
            synchronized (audioBuffer) {
                audioData = audioBuffer.toByteArray();
            }

            // Send data to backend only if we have audio
            if (audioData.length > 0) {
                Log.d(TAG, "Sending audio data, size: " + audioData.length + " bytes");
                sendAudioToBackend(audioData);
            } else {
                showToast("No audio recorded");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error stopping recording: " + e.getMessage());
            showToast("Error processing audio");
        } finally {
            resetRecordingState();
        }
    }

    private void resetRecordingState() {
        isRecording = false;
        audioRecord = null;
        recordingThread = null;
        audioBuffer = null;
        if (micButton != null) {
            micButton.setImageResource(R.drawable.ic_mic);
        }
    }

    private void sendAudioToBackend(byte[] audioData) {
        try {
            // Create multipart request
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("sample_rate", String.valueOf(SAMPLE_RATE))
                    .addFormDataPart("audio_data", "audio.raw",
                            RequestBody.create(MediaType.parse("audio/raw"), audioData))
                    .build();

            // Build request
            Request request = new Request.Builder()
                    .url(TRANSCRIBE_ENDPOINT)
                    .post(requestBody)
                    .build();

            // Send the request
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to send audio data: " + e.getMessage());
                    showToast("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            Log.e(TAG, "Server error: " + response.code());
                            showToast("Server error: " + response.code());
                            return;
                        }

                        String responseString = responseBody.string();
                        Log.d(TAG, "Transcription response: " + responseString);

                        JSONObject result = new JSONObject(responseString);
                        if ("success".equals(result.getString("status"))) {
                            String transcribedString = result.getString("text");
                            updateTranscribedText(transcribedString);
                            showToast("Transcription complete");

                            // Request image generation with the transcribed text
                            requestImageGeneration(transcribedString);
                        } else {
                            showToast("Transcription error: " + result.getString("error"));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing response: " + e.getMessage());
                        showToast("Error processing response");
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error creating request: " + e.getMessage());
            showToast("Error creating request");
        }
    }

    private void requestImageGeneration(String prompt) {
        if (getActivity() == null) return;

        // Show progress indicator
        getActivity().runOnUiThread(() -> {
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
            }
        });

        try {
            // Create JSON request body with the prompt
            JSONObject requestJson = new JSONObject();
            requestJson.put("prompt", prompt);

            // Build the request
            Request request = new Request.Builder()
                    .url(GENERATE_IMAGE_ENDPOINT)
                    .post(RequestBody.create(
                            MediaType.parse("application/json"), requestJson.toString()))
                    .build();

            // Send the request
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Image generation request failed: " + e.getMessage());
                    showToast("Failed to generate image: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (ResponseBody responseBody = response.body()) {
                        if (!response.isSuccessful() || responseBody == null) {
                            Log.e(TAG, "Image generation error: " + response.code());
                            showToast("Image generation failed: " + response.code());
                            return;
                        }

                        String responseString = responseBody.string();
                        Log.d(TAG, "Image generation response received");

                        try {
                            JSONObject result = new JSONObject(responseString);

                            if ("success".equals(result.getString("status"))) {
                                // Extract base64 image data from the response
                                String base64Image = result.getString("image_data");
                                displayImage(base64Image);
                            } else {
                                showToast("Image generation error: " + result.getString("error"));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing image response: " + e.getMessage());
                            showToast("Error parsing image response");
                        }
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error creating image request: " + e.getMessage());
            showToast("Error requesting image generation");
        }
    }

    private void displayImage(String base64Data) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            try {
                // Hide progress bar
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }

                Log.d(TAG, "Received base64 data of length: " + base64Data.length());

                // Decode and create bitmap
                byte[] decodedString = Base64.decode(base64Data, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                if (bitmap != null) {
                    Log.d(TAG, "Successfully decoded bitmap: " + bitmap.getWidth() + "x" + bitmap.getHeight());

                    // Display the image
                    generatedImageView.setImageBitmap(bitmap);
                    generatedImageView.setVisibility(View.VISIBLE);
                    showToast("Image generated successfully");

                    // Optionally save to local storage
//                    saveImageToDevice(bitmap);
                } else {
                    Log.e(TAG, "Failed to decode bitmap from base64 string");
                    showToast("Could not decode image");
                }
            } catch (IllegalArgumentException e) {
                // This exception is thrown when Base64 decoder encounters invalid data
                Log.e(TAG, "Invalid base64 data: " + e.getMessage());
                showToast("Invalid image data received");
            } catch (Exception e) {
                Log.e(TAG, "Error displaying image: " + e.getMessage(), e);
                showToast("Error displaying image");
            }
        });
    }

    private void updateTranscribedText(String newText) {
        if (getActivity() == null) return;

        getActivity().runOnUiThread(() -> {
            if (!TextUtils.isEmpty(newText)) {
                String currentText = transcribedText.getText().toString();
                String updatedText = TextUtils.isEmpty(currentText) ?
                        newText : currentText + "\n" + newText;
                transcribedText.setText(updatedText);
                transcribedText.setSelection(updatedText.length());
            }
        });
    }

    private void showToast(String message) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() ->
                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (isRecording) {
            stopRecording();
        }
    }
}
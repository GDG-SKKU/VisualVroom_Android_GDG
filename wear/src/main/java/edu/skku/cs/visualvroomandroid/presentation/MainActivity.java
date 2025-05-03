package edu.skku.cs.visualvroomandroid.presentation;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.skku.cs.visualvroomandroid.R;

public class MainActivity extends AppCompatActivity implements MessageClient.OnMessageReceivedListener {

    private static final String TAG = "WearMainActivity";
    private static final String VIBRATION_PATH = "/vibration";
    private static final String VEHICLE_ALERT_PATH = "/vehicle_alert";

    // Animation display duration in milliseconds - changed from 5000 to 4000
    private static final int ANIMATION_DURATION = 4000;

    private TextView statusTextView;
    private TextView directionTextView;
    private LottieAnimationView vehicleAnimationView;
    private Vibrator vibrator;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable hideAnimationRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

// Hide action bar if it exists
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Remove title bar to get full screen


        // Initialize views
//        statusTextView = findViewById(R.id.status_text);
        directionTextView = findViewById(R.id.direction_text);
        vehicleAnimationView = findViewById(R.id.vehicle_animation);

        // Get vibrator service
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            vibrator = getSystemService(Vibrator.class);
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }

        // Initialize the runnable to hide animations after they've played
        hideAnimationRunnable = () -> {
            vehicleAnimationView.cancelAnimation();
            vehicleAnimationView.setVisibility(View.GONE);
            directionTextView.setVisibility(View.GONE);
//            statusTextView.setText("Ready to receive alerts");
        };

    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register the message listener
        Wearable.getMessageClient(this).addListener(this);
//        statusTextView.setText("Ready to receive alerts");
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the message listener
        Wearable.getMessageClient(this).removeListener(this);
        // Remove any pending callbacks
        mainHandler.removeCallbacks(hideAnimationRunnable);
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        Log.d(TAG, "Message received: " + path);

        if (path.equals(VIBRATION_PATH)) {
            // Handle simple vibration command
            handleVibrationCommand(messageEvent.getData());
        } else if (path.equals(VEHICLE_ALERT_PATH)) {
            // Handle vehicle alert (which now includes vibration)
            handleVehicleAlert(messageEvent.getData());
        }
    }

    private void handleVibrationCommand(byte[] data) {
        Log.d(TAG, "Basic vibration command received");

        // Update UI
//        runOnUiThread(() -> statusTextView.setText("Vibrating..."));

        // Parse the vibration pattern
        final long[] vibrationPattern = parseVibrationPatternFromData(data);

        // Vibrate the watch with the determined pattern
        if (vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1));
            } else {
                // Deprecated in API 26
                vibrator.vibrate(vibrationPattern, -1);
            }
        }

        // Reset status text after vibration
        runOnUiThread(() -> {
            // Wait for vibration to complete before updating text
            long totalVibrationTime = 0;
            for (long duration : vibrationPattern) {
                totalVibrationTime += duration;
            }

            mainHandler.postDelayed(() -> {
//                statusTextView.setText("Ready to receive alerts");
            }, totalVibrationTime + 500); // Add a small extra delay
        });
    }

    private void handleVehicleAlert(byte[] data) {
        try {
            String jsonData = new String(data);
            Log.d(TAG, "Vehicle alert received: " + jsonData);

            JSONObject alertJson = new JSONObject(jsonData);
            String vehicleType = alertJson.getString("vehicle_type");
            String direction = alertJson.getString("direction");

            // Parse vibration pattern if provided
            final long[] vibrationPattern = parseVibrationPattern(alertJson);

            runOnUiThread(() -> {
                // Update status text
//                statusTextView.setText("Alert: " + vehicleType);

                // Update direction text
                if (direction.equalsIgnoreCase("L")) {
                    directionTextView.setText("← LEFT");
                } else if (direction.equalsIgnoreCase("R")) {
                    directionTextView.setText("RIGHT →");
                } else {
                    directionTextView.setText(direction);
                }
                directionTextView.setVisibility(View.VISIBLE);

                // Show the appropriate animation
                showAnimation(vehicleType);

                // Remove any existing callbacks
                mainHandler.removeCallbacks(hideAnimationRunnable);

                // Set up new callback to hide the animation after it's played
                mainHandler.postDelayed(hideAnimationRunnable, ANIMATION_DURATION);
            });

            // Vibrate the watch with the specific pattern
            if (vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1));
                } else {
                    // Deprecated in API 26
                    vibrator.vibrate(vibrationPattern, -1);
                }
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing vehicle alert JSON", e);
        }
    }

    private long[] parseVibrationPatternFromData(byte[] data) {
        // Default pattern if parsing fails
        long[] defaultPattern = new long[]{0, 300, 200, 300};

        if (data == null || data.length == 0) {
            return defaultPattern;
        }

        try {
            // Try to parse custom pattern from JSON
            String jsonData = new String(data);
            JSONObject command = new JSONObject(jsonData);

            if (command.has("pattern")) {
                JSONArray patternArray = command.getJSONArray("pattern");
                long[] pattern = new long[patternArray.length()];

                for (int i = 0; i < patternArray.length(); i++) {
                    pattern[i] = patternArray.getLong(i);
                }

                Log.d(TAG, "Using custom vibration pattern");
                return pattern;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing vibration data, using default pattern", e);
        }

        return defaultPattern;
    }

    private long[] parseVibrationPattern(JSONObject alertJson) {
        try {
            if (alertJson.has("vibration_pattern")) {
                JSONArray patternArray = alertJson.getJSONArray("vibration_pattern");
                long[] pattern = new long[patternArray.length()];

                for (int i = 0; i < patternArray.length(); i++) {
                    pattern[i] = patternArray.getLong(i);
                }

                return pattern;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing vibration pattern", e);
        }

        // Default pattern if none provided or error occurs
        return new long[]{0, 500, 100, 500};
    }

    private void showAnimation(String vehicleType) {
        // Cancel any existing animation
        vehicleAnimationView.cancelAnimation();

        // Set the animation resource based on vehicle type
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
                animationResource = R.raw.siren; // Default animation
        }

        // Set and play the animation
        vehicleAnimationView.setAnimation(animationResource);
        vehicleAnimationView.setVisibility(View.VISIBLE);
        vehicleAnimationView.playAnimation();
    }
}
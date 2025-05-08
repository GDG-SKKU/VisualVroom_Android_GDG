package edu.skku.cs.visualvroomandroid;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String VIBRATION_PATH = "/vibration";

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private WearNotificationService wearService;
    private boolean isServiceBound = false;
    private BroadcastReceiver messageReceiver;
    private boolean isRecording = false;
    private Button vibrateWatchButton;

    // Reference to fragments
    private SpeechToTextFragment speechToTextFragment;
    private AudioRecorderFragment audioRecorderFragment;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            WearNotificationService.LocalBinder binder = (WearNotificationService.LocalBinder) service;
            wearService = binder.getService();
            isServiceBound = true;
            Log.d(TAG, "WearNotificationService bound successfully");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isServiceBound = false;
            wearService = null;
            Log.d(TAG, "WearNotificationService disconnected");
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize ViewPager and TabLayout
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

//        vibrateWatchButton = findViewById(R.id.vibrateWatchButton);
//        vibrateWatchButton.setOnClickListener(v -> {
//            new Thread(this::sendRightVibrationRequest).start();
//        });

        // Set up the adapter
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    View customTabView = LayoutInflater.from(this).inflate(R.layout.custom_tab, null);
                    TextView tabText = customTabView.findViewById(R.id.tabText);

                    switch (position) {
                        case 0:
                            tabText.setText("Audio Recorder");
                            break;
                        case 1:
                            tabText.setText("Speech to Text");
                            break;
                    }

                    tab.setCustomView(customTabView);
                }
        ).attach();

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                setTabBackground(tab, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                setTabBackground(tab, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    View customTabView = LayoutInflater.from(this).inflate(R.layout.custom_tab, null);
                    TextView tabText = customTabView.findViewById(R.id.tabText);

                    switch (position) {
                        case 0:
                            tabText.setText("Audio Recorder");
                            break;
                        case 1:
                            tabText.setText("Speech to Text");
                            break;
                    }

                    tab.setCustomView(customTabView);
                }
        ).attach();

// 초기 상태 설정 (첫 번째 탭 선택)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            TabLayout.Tab firstTab = tabLayout.getTabAt(0);
            TabLayout.Tab secondTab = tabLayout.getTabAt(1);
            if (firstTab != null) {
                setTabBackground(firstTab, true); // 첫 번째 탭 선택
            }
            if (secondTab != null) {
                setTabBackground(secondTab, false); // 두 번째 탭 선택되지 않음
            }
        }, 100);



        // Register broadcast receiver
        messageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("AUDIO_INFERENCE_RESULT".equals(intent.getAction())) {
                    String result = intent.getStringExtra("result");
                    handleInferenceResult(result);
                }
            }
        };

        registerReceiver(messageReceiver, new IntentFilter("AUDIO_INFERENCE_RESULT"),
                Context.RECEIVER_NOT_EXPORTED);

        // Initialize the vibrate watch button
//        vibrateWatchButton = findViewById(R.id.vibrateWatchButton);
//        vibrateWatchButton.setOnClickListener(v -> {
//            new Thread(this::sendRightVibrationRequest).start();
//        });

        // Check permissions before starting any services
        checkAndRequestPermissions();
    }

    private void setTabBackground(TabLayout.Tab tab, boolean isSelected) {
        View view = tab.getCustomView();
        if (view != null) {
            TextView tabText = view.findViewById(R.id.tabText);
            if (isSelected) {
                tabText.setTextColor(ContextCompat.getColor(this, android.R.color.white));
                view.setBackground(ContextCompat.getDrawable(this, R.drawable.tab_background));
            } else {
                tabText.setTextColor(ContextCompat.getColor(this, R.color.purple_custom));
                view.setBackground(ContextCompat.getDrawable(this, R.drawable.tab_unselected_background));
            }
        }
    }




    private class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(FragmentActivity activity) {
            super(activity);
        }

        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    audioRecorderFragment = new AudioRecorderFragment();
                    return audioRecorderFragment;
                case 1:
                    speechToTextFragment = new SpeechToTextFragment();
                    return speechToTextFragment;
                default:
                    throw new IllegalStateException("Unexpected position " + position);
            }
        }

        @Override
        public int getItemCount() {
            return 2; // Updated to include the new AudioRecorderFragment
        }
    }

    public void sendAlertToWatch(String vehicleType, String direction) {
        // Using direct message API instead of service
        new Thread(() -> {
            try {
                // 1. First send the vehicle alert information
                JSONObject alert = new JSONObject();
                alert.put("vehicle_type", vehicleType);
                alert.put("direction", direction);

                String alertJson = alert.toString();
                Log.d(TAG, "Sending alert to watch: " + alertJson);

                // Get all connected devices
                Task<List<Node>> nodesTask = Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();
                List<Node> nodes = Tasks.await(nodesTask);

                for (Node node : nodes) {
                    // Send message to each connected device
                    Task<Integer> sendTask = Wearable.getMessageClient(getApplicationContext())
                            .sendMessage(node.getId(), "/vehicle_alert", alertJson.getBytes());

                    Tasks.await(sendTask);
                    Log.d(TAG, "Alert sent to watch: " + node.getDisplayName());
                }

                // 2. Then trigger the vibration separately
                if(direction == "L" || direction == "Left" || direction == "left"){
                    sendLeftVibrationRequest();

                }else {
                    sendRightVibrationRequest();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to send alert to watch: " + e.getMessage());
            }
        }).start();
    }
    private void sendLeftVibrationRequest() {
        try {
            // Get all connected devices
            Task<List<Node>> nodesTask = Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();
            List<Node> nodes = Tasks.await(nodesTask);

            for (Node node : nodes) {
                // Send message to each connected device
                Task<Integer> sendTask = Wearable.getMessageClient(getApplicationContext())
                        .sendMessage(node.getId(), VIBRATION_PATH, new byte[0]);

                // Wait for the task to complete
                Tasks.await(sendTask);

                // Show feedback on UI thread
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "Vibration command sent to watch",
                            Toast.LENGTH_SHORT).show();
                });
            }
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();

            // Show error on UI thread
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this,
                        "Failed to send vibration command: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void sendRightVibrationRequest() {
        try {
            // Create JSON for vibration pattern
            JSONObject vibrationData = new JSONObject();
            JSONArray pattern = new JSONArray();
            pattern.put(0);    // Start immediately
            pattern.put(200);  // Medium vibrate
            pattern.put(200);  // Medium pause
            pattern.put(200);  // Medium vibrate
            vibrationData.put("pattern", pattern);
            vibrationData.put("repeat", 1); // No repeat
            byte[] messageData = vibrationData.toString().getBytes();

            // Get all connected devices
            Task<List<Node>> nodesTask = Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();
            List<Node> nodes = Tasks.await(nodesTask);

            for (Node node : nodes) {
                // Send message to each connected device
                Task<Integer> sendTask = Wearable.getMessageClient(getApplicationContext())
                        .sendMessage(node.getId(), VIBRATION_PATH, messageData);

                // Wait for the task to complete
                Tasks.await(sendTask);

                // Show feedback on UI thread
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            "Vibration command sent to watch",
                            Toast.LENGTH_SHORT).show();
                });
            }
        } catch (Exception e) {
            // Catch all exceptions including JSONException
            e.printStackTrace();

            // Show error on UI thread
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this,
                        "Failed to send vibration command: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();

        // Basic permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }

        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION);
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        } else {
            // We no longer need to start the wear service
            // startWearService();
        }
    }

    private boolean checkAllPermissionsGranted() {
        boolean allGranted = true;

        allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
                    == PackageManager.PERMISSION_GRANTED;
            allGranted &= ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
        }

        return allGranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                // We no longer need to start the wear service
                // startWearService();
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Required permissions not granted. The app may not function correctly.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startWearService() {
        // This method is kept for backward compatibility but we're not using it anymore
        Intent serviceIntent = new Intent(this, WearNotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }


    public void stopRecording() {
        Intent recordIntent = new Intent(this, AudioRecordingService.class);
        recordIntent.setAction("STOP_RECORDING");
        startService(recordIntent);
        isRecording = false;
    }

    private void handleInferenceResult(String result) {
        try {
            JSONObject resultJson = new JSONObject(result);
            JSONObject inferenceResult = resultJson.getJSONObject("inference_result");

            // Extract the relevant fields
            String vehicleType = inferenceResult.getString("vehicle_type");
            String direction = inferenceResult.getString("direction");
            double confidence = inferenceResult.getDouble("confidence");
            boolean shouldNotify = inferenceResult.getBoolean("should_notify");

            Log.d(TAG, String.format("Inference result: %s from %s, confidence: %.4f, should notify: %s",
                    vehicleType, direction, confidence, shouldNotify));

            // Only proceed if confidence is high enough (should_notify will be true if confidence > 0.97)
            if (shouldNotify) {
                // Update the sound detection fragment UI if needed
//                new Thread(this::sendVibrationRequest).start();

                // Send alert to watch with vibration
                sendAlertToWatch(vehicleType, direction);

                // Display a toast notification about the prediction
                runOnUiThread(() -> {
                    Toast.makeText(this,
                            String.format("Alert: %s detected from %s", vehicleType, direction),
                            Toast.LENGTH_SHORT).show();
                });
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing inference result: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        if (isRecording) {
            stopRecording();
        }

        if (isServiceBound) {
            unbindService(serviceConnection);
            isServiceBound = false;
        }

        if (messageReceiver != null) {
            unregisterReceiver(messageReceiver);
        }

        super.onDestroy();
    }
}
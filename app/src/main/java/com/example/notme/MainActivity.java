package com.example.notme;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.notme.data.DataRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NotMe_MainActivity";
    private static final int READ_INTERVAL = 2000; // Read data every 2 seconds

    private TextView txtView;
    private TextView statusText;
    private Button checkPermissionBtn;
    private Button openSettingsBtn;
    private Button testBtn;
    private Button clearBtn;

    private Handler dataReadHandler;
    private Runnable dataReadRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: App started");

        // Find all views
        txtView = findViewById(R.id.notificationLog);
        statusText = findViewById(R.id.statusText);
        checkPermissionBtn = findViewById(R.id.checkPermissionBtn);
        openSettingsBtn = findViewById(R.id.openSettingsBtn);
        testBtn = findViewById(R.id.testBtn);
        clearBtn = findViewById(R.id.clearBtn);

        // Set up button click listeners
        checkPermissionBtn.setOnClickListener(v -> checkPermissionStatus());
        openSettingsBtn.setOnClickListener(v -> openNotificationSettings());
        testBtn.setOnClickListener(v -> testBroadcast());
        clearBtn.setOnClickListener(v -> clearLog());

        // Set up data reading handler - reads from repository every 2 seconds
        dataReadHandler = new Handler();
        dataReadRunnable = new Runnable() {
            @Override
            public void run() {
                readNotificationData();
                dataReadHandler.postDelayed(this, READ_INTERVAL);
            }
        };
        Log.d(TAG, "onCreate: Data reading handler initialized (Mode: " + DataRepository.getStorageMode() + ")");

        // Check permission on startup
        checkPermissionStatus();
    }

    // Helper method to check if notification access is granted
    private boolean isNotificationServiceEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && pkgName.equals(cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    // Button handler: Check permission status
    private void checkPermissionStatus() {
        boolean hasPermission = isNotificationServiceEnabled();
        Log.d(TAG, "checkPermissionStatus: " + hasPermission);

        if (hasPermission) {
            statusText.setText("Status: ✓ Permission GRANTED - Ready!");
            statusText.setTextColor(0xFF4CAF50); // Green
            Toast.makeText(this, "Permission is granted! Listening for notifications.", Toast.LENGTH_SHORT).show();
        } else {
            statusText.setText("Status: ✗ Permission DENIED - Click button below");
            statusText.setTextColor(0xFFF44336); // Red
            Toast.makeText(this, "Permission not granted. Please enable in settings.", Toast.LENGTH_LONG).show();
        }
    }

    // Button handler: Open notification settings
    private void openNotificationSettings() {
        Log.d(TAG, "openNotificationSettings: Opening settings");
        Toast.makeText(this, "Opening settings... Find 'NotMe' and toggle it ON", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        startActivity(intent);
    }

    // Button handler: Test the broadcast receiver
    private void testBroadcast() {
        Log.d(TAG, "testBroadcast: Sending test notification");

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        // Save test notification using DataRepository
        DataRepository.save(this, "com.example.test", "Test", "This is a test notification!", timestamp);

        // Refresh UI after short delay
        new Handler().postDelayed(this::readNotificationData, 300);

        Toast.makeText(this, "Test notification saved (" + DataRepository.getStorageMode() + ")", Toast.LENGTH_SHORT).show();
    }

    // Button handler: Clear the notification log
    private void clearLog() {
        Log.d(TAG, "clearLog: Clearing notification log");
        txtView.setText("Waiting for notifications...");

        // Clear using DataRepository
        DataRepository.clear(this);

        // Refresh UI after short delay
        new Handler().postDelayed(this::readNotificationData, 300);

        Toast.makeText(this, "Log cleared (" + DataRepository.getStorageMode() + ")", Toast.LENGTH_SHORT).show();
    }

    // Read notifications from repository and display in TextView
    private void readNotificationData() {
        // Run on background thread (database operations must be off main thread)
        new Thread(() -> {
            String data = DataRepository.getAllLogs(this);

            // Update UI on main thread
            runOnUiThread(() -> {
                txtView.setText(data);
            });
        }).start();
    }

    // Re-check permission when app becomes visible (e.g., returning from Settings)
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: App visible, re-checking permission and starting data reader");
        checkPermissionStatus();

        // Start data reading when app becomes visible
        dataReadHandler.post(dataReadRunnable);
    }

    // App going to background - stop data reading to save battery
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: App going to background, stopping data reader");

        // Stop data reading when app goes to background
        dataReadHandler.removeCallbacks(dataReadRunnable);
    }

    // Stop data reading when activity is destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Stopping data reader");

        // Stop the handler to prevent memory leaks
        dataReadHandler.removeCallbacks(dataReadRunnable);
    }
}
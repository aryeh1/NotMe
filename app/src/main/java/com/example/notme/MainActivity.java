package com.example.notme;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "NotMe_MainActivity";
    private static final String LOG_FILE = "notifications.txt";
    private static final int READ_INTERVAL = 2000; // Read file every 2 seconds

    private TextView txtView;
    private TextView statusText;
    private Button checkPermissionBtn;
    private Button openSettingsBtn;
    private Button testBtn;
    private Button clearBtn;

    private Handler fileReadHandler;
    private Runnable fileReadRunnable;

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

        // Set up file reading handler - reads notifications.txt every 2 seconds
        fileReadHandler = new Handler();
        fileReadRunnable = new Runnable() {
            @Override
            public void run() {
                readNotificationFile();
                fileReadHandler.postDelayed(this, READ_INTERVAL);
            }
        };
        Log.d(TAG, "onCreate: File reading handler initialized");

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

        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String testMessage = "TEST at " + timestamp + "\n" +
                            "Package: com.example.test\n" +
                            "Title: Test\n" +
                            "Text: This is a test notification!\n" +
                            "------\n";

        // Simulate a notification by directly updating the TextView
        String currentText = txtView.getText().toString();
        if (currentText.equals("Waiting for notifications...")) {
            txtView.setText(testMessage);
        } else {
            txtView.setText(testMessage + currentText);
        }

        Toast.makeText(this, "Test message added to log", Toast.LENGTH_SHORT).show();
    }

    // Button handler: Clear the notification log
    private void clearLog() {
        Log.d(TAG, "clearLog: Clearing notification log and file");
        txtView.setText("Waiting for notifications...");

        // Clear the file
        try {
            File file = new File(getFilesDir(), LOG_FILE);
            if (file.exists()) {
                file.delete();
                Log.d(TAG, "clearLog: File deleted successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "clearLog: Failed to delete file", e);
        }

        Toast.makeText(this, "Log cleared", Toast.LENGTH_SHORT).show();
    }

    // Read notifications from file and display in TextView
    private void readNotificationFile() {
        try {
            File file = new File(getFilesDir(), LOG_FILE);

            if (!file.exists()) {
                // File doesn't exist yet - no notifications recorded
                return;
            }

            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();

            // Update TextView with file contents
            String fileContent = content.toString();
            if (fileContent.isEmpty()) {
                txtView.setText("Waiting for notifications...");
            } else {
                txtView.setText(fileContent);
                Log.d(TAG, "readNotificationFile: Updated UI with " + fileContent.length() + " characters");
            }

        } catch (IOException e) {
            Log.e(TAG, "readNotificationFile: Failed to read file", e);
        }
    }

    // Re-check permission when app becomes visible (e.g., returning from Settings)
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: App visible, re-checking permission and starting file reader");
        checkPermissionStatus();

        // Start file reading when app becomes visible
        fileReadHandler.post(fileReadRunnable);
    }

    // App going to background - stop file reading to save battery
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: App going to background, stopping file reader");

        // Stop file reading when app goes to background
        fileReadHandler.removeCallbacks(fileReadRunnable);
    }

    // Stop file reading when activity is destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Stopping file reader");

        // Stop the handler to prevent memory leaks
        fileReadHandler.removeCallbacks(fileReadRunnable);
    }
}
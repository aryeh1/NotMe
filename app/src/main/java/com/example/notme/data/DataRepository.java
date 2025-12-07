package com.example.notme.data;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataRepository {

    private static final String TAG = "DataRepository";

    // CHANGE THIS TO TRUE TO ACTIVATE DATABASE
    private static final boolean USE_DB = true;

    private static final String LOG_FILE = "notifications.txt";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Save a notification
    public static void save(Context context, String packageName, String title, String text, String timestamp) {
        if (USE_DB) {
            // Save to Database
            executor.execute(() -> {
                try {
                    NotificationEntity entity = new NotificationEntity(packageName, title, text, timestamp);
                    AppDatabase.getInstance(context).dao().insert(entity);
                    Log.d(TAG, "save: Saved to DATABASE");
                } catch (Exception e) {
                    Log.e(TAG, "save: Database error", e);
                }
            });
        } else {
            // Save to File (Old logic)
            executor.execute(() -> {
                try {
                    File file = new File(context.getFilesDir(), LOG_FILE);
                    FileWriter writer = new FileWriter(file, true);

                    String logEntry = timestamp + "\n" +
                            "App: " + packageName + "\n" +
                            "Title: " + title + "\n" +
                            "Text: " + text + "\n" +
                            "------\n";

                    writer.write(logEntry);
                    writer.close();
                    Log.d(TAG, "save: Saved to FILE");
                } catch (IOException e) {
                    Log.e(TAG, "save: File error", e);
                }
            });
        }
    }

    // Get all logs as formatted string
    public static String getAllLogs(Context context) {
        if (USE_DB) {
            // Get from Database
            try {
                List<NotificationEntity> entities = AppDatabase.getInstance(context).dao().getAll();

                if (entities.isEmpty()) {
                    return "Waiting for notifications...";
                }

                StringBuilder sb = new StringBuilder();
                for (NotificationEntity entity : entities) {
                    sb.append(entity.getTimestamp()).append("\n")
                      .append("App: ").append(entity.getPackageName()).append("\n")
                      .append("Title: ").append(entity.getTitle()).append("\n")
                      .append("Text: ").append(entity.getText()).append("\n")
                      .append("------\n");
                }

                Log.d(TAG, "getAllLogs: Retrieved from DATABASE (" + entities.size() + " items)");
                return sb.toString();

            } catch (Exception e) {
                Log.e(TAG, "getAllLogs: Database error", e);
                return "Error reading database";
            }
        } else {
            // Get from File (Old logic)
            try {
                File file = new File(context.getFilesDir(), LOG_FILE);

                if (!file.exists()) {
                    return "Waiting for notifications...";
                }

                StringBuilder content = new StringBuilder();
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;

                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();

                String fileContent = content.toString();
                Log.d(TAG, "getAllLogs: Retrieved from FILE");
                return fileContent.isEmpty() ? "Waiting for notifications..." : fileContent;

            } catch (IOException e) {
                Log.e(TAG, "getAllLogs: File error", e);
                return "Error reading file";
            }
        }
    }

    // Clear all logs
    public static void clear(Context context) {
        if (USE_DB) {
            // Clear Database
            executor.execute(() -> {
                try {
                    AppDatabase.getInstance(context).dao().deleteAll();
                    Log.d(TAG, "clear: Cleared DATABASE");
                } catch (Exception e) {
                    Log.e(TAG, "clear: Database error", e);
                }
            });
        } else {
            // Clear File (Old logic)
            executor.execute(() -> {
                try {
                    File file = new File(context.getFilesDir(), LOG_FILE);
                    if (file.exists()) {
                        file.delete();
                        Log.d(TAG, "clear: Cleared FILE");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "clear: File error", e);
                }
            });
        }
    }

    // Get current storage mode for display
    public static String getStorageMode() {
        return USE_DB ? "Database" : "File";
    }
}

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

    // Get database statistics
    public static String getStats(Context context) {
        if (!USE_DB) {
            return "Stats only available in Database mode";
        }

        try {
            List<NotificationEntity> all = AppDatabase.getInstance(context).dao().getAll();
            int count = all.size();

            if (count == 0) {
                return "No notifications in database";
            }

            // Get oldest and newest
            String newest = all.get(0).getTimestamp();
            String oldest = all.get(all.size() - 1).getTimestamp();

            // Get DB file size
            File dbFile = context.getDatabasePath("notifications.db");
            long sizeBytes = dbFile.exists() ? dbFile.length() : 0;
            String sizeKB = String.format("%.2f KB", sizeBytes / 1024.0);

            return String.format(
                "📊 Database Statistics\n" +
                "━━━━━━━━━━━━━━━━━━━\n" +
                "Total: %d notifications\n" +
                "Size: %s\n" +
                "Oldest: %s\n" +
                "Newest: %s\n" +
                "Storage: %s ✓\n" +
                "━━━━━━━━━━━━━━━━━━━",
                count, sizeKB, oldest, newest, getStorageMode()
            );

        } catch (Exception e) {
            Log.e(TAG, "getStats: Error", e);
            return "Error getting stats";
        }
    }

    // Get sender statistics (app name with counts)
    public static String getSenders(Context context) {
        if (!USE_DB) {
            return "Senders only available in Database mode";
        }

        try {
            List<NotificationEntity> all = AppDatabase.getInstance(context).dao().getAll();

            if (all.isEmpty()) {
                return "No notifications to analyze";
            }

            // Count notifications per app
            java.util.Map<String, Integer> senderCounts = new java.util.HashMap<>();
            for (NotificationEntity entity : all) {
                String appName = extractAppName(entity.getPackageName());
                senderCounts.put(appName, senderCounts.getOrDefault(appName, 0) + 1);
            }

            // Sort by count descending
            java.util.List<java.util.Map.Entry<String, Integer>> sorted =
                new java.util.ArrayList<>(senderCounts.entrySet());
            sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            // Build output
            StringBuilder sb = new StringBuilder("📱 Notification Senders\n━━━━━━━━━━━━━━━━━━━\n");
            int maxCount = sorted.isEmpty() ? 1 : sorted.get(0).getValue();

            for (java.util.Map.Entry<String, Integer> entry : sorted) {
                String appName = entry.getKey();
                int count = entry.getValue();
                int barLength = (count * 10) / maxCount;
                String bar = "█".repeat(Math.max(1, barLength)) + "░".repeat(10 - barLength);
                sb.append(String.format("%-15s (%3d) %s\n", appName, count, bar));
            }

            sb.append("━━━━━━━━━━━━━━━━━━━");
            return sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "getSenders: Error", e);
            return "Error analyzing senders";
        }
    }

    // Extract app name from package (e.g., "com.whatsapp" -> "whatsapp")
    private static String extractAppName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "unknown";
        }
        String[] parts = packageName.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : packageName;
    }

    // Export to CSV file
    public static String exportToCSV(Context context) {
        if (!USE_DB) {
            return "Export only available in Database mode";
        }

        try {
            List<NotificationEntity> all = AppDatabase.getInstance(context).dao().getAll();

            if (all.isEmpty()) {
                return "No notifications to export";
            }

            // Create CSV file in Downloads
            File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
            String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",
                java.util.Locale.getDefault()).format(new java.util.Date());
            File csvFile = new File(downloadsDir, "notme_export_" + timestamp + ".csv");

            FileWriter writer = new FileWriter(csvFile);

            // Write header
            writer.write("Timestamp,Package,App,Title,Text\n");

            // Write data
            for (NotificationEntity entity : all) {
                String appName = extractAppName(entity.getPackageName());
                writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                    escapeCsv(entity.getTimestamp()),
                    escapeCsv(entity.getPackageName()),
                    escapeCsv(appName),
                    escapeCsv(entity.getTitle()),
                    escapeCsv(entity.getText())
                ));
            }

            writer.close();
            Log.d(TAG, "exportToCSV: Exported to " + csvFile.getAbsolutePath());

            return "Exported " + all.size() + " notifications to:\n" + csvFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "exportToCSV: Error", e);
            return "Error exporting: " + e.getMessage();
        }
    }

    // Escape CSV special characters
    private static String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"").replace("\n", " ").replace("\r", "");
    }

    // Search/filter notifications
    public static String search(Context context, String query) {
        if (!USE_DB) {
            return "Search only available in Database mode";
        }

        if (query == null || query.trim().isEmpty()) {
            return "Enter search term";
        }

        try {
            List<NotificationEntity> all = AppDatabase.getInstance(context).dao().getAll();
            String lowerQuery = query.toLowerCase();

            StringBuilder sb = new StringBuilder();
            int count = 0;

            for (NotificationEntity entity : all) {
                boolean matches =
                    entity.getPackageName().toLowerCase().contains(lowerQuery) ||
                    entity.getTitle().toLowerCase().contains(lowerQuery) ||
                    entity.getText().toLowerCase().contains(lowerQuery);

                if (matches) {
                    sb.append(entity.getTimestamp()).append("\n")
                      .append("App: ").append(entity.getPackageName()).append("\n")
                      .append("Title: ").append(entity.getTitle()).append("\n")
                      .append("Text: ").append(entity.getText()).append("\n")
                      .append("------\n");
                    count++;
                }
            }

            if (count == 0) {
                return "No results for: " + query;
            }

            return "Found " + count + " results:\n\n" + sb.toString();

        } catch (Exception e) {
            Log.e(TAG, "search: Error", e);
            return "Error searching";
        }
    }

    // Compact database (VACUUM)
    public static String compactDB(Context context) {
        if (!USE_DB) {
            return "Compact only available in Database mode";
        }

        try {
            File dbFileBefore = context.getDatabasePath("notifications.db");
            long sizeBefore = dbFileBefore.exists() ? dbFileBefore.length() : 0;

            // Run VACUUM
            AppDatabase.getInstance(context).getOpenHelper()
                .getWritableDatabase().execSQL("VACUUM");

            File dbFileAfter = context.getDatabasePath("notifications.db");
            long sizeAfter = dbFileAfter.exists() ? dbFileAfter.length() : 0;
            long saved = sizeBefore - sizeAfter;

            Log.d(TAG, "compactDB: Before=" + sizeBefore + " After=" + sizeAfter + " Saved=" + saved);

            return String.format(
                "Database compacted!\n\n" +
                "Before: %.2f KB\n" +
                "After: %.2f KB\n" +
                "Saved: %.2f KB",
                sizeBefore / 1024.0,
                sizeAfter / 1024.0,
                saved / 1024.0
            );

        } catch (Exception e) {
            Log.e(TAG, "compactDB: Error", e);
            return "Error compacting: " + e.getMessage();
        }
    }
}

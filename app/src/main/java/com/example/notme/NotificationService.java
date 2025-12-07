package com.example.notme;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationService extends NotificationListenerService {

    private static final String TAG = "NotMe_NotifService";
    private static final String LOG_FILE = "notifications.txt";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: NotificationService started!");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "onNotificationPosted: Notification received!");

        // Extract information from the notification
        String packageName = sbn.getPackageName();
        CharSequence title = sbn.getNotification().extras.getCharSequence("android.title");
        CharSequence text = sbn.getNotification().extras.getCharSequence("android.text");

        // Safe conversion to String
        String titleStr = (title != null) ? title.toString() : "No title";
        String textStr = (text != null) ? text.toString() : "No text";

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        Log.d(TAG, "Package: " + packageName);
        Log.d(TAG, "Title: " + titleStr);
        Log.d(TAG, "Text: " + textStr);

        // Write to file
        String logEntry = timestamp + "\n" +
                         "App: " + packageName + "\n" +
                         "Title: " + titleStr + "\n" +
                         "Text: " + textStr + "\n" +
                         "------\n";

        writeToFile(logEntry);
    }

    private void writeToFile(String data) {
        try {
            File file = new File(getFilesDir(), LOG_FILE);
            FileWriter writer = new FileWriter(file, true); // append mode
            writer.write(data);
            writer.close();
            Log.d(TAG, "writeToFile: SUCCESS - wrote to " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "writeToFile: FAILED", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "onNotificationRemoved: " + sbn.getPackageName());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: NotificationService stopped");
    }
}
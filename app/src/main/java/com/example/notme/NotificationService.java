package com.example.notme;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.example.notme.data.DataRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationService extends NotificationListenerService {

    private static final String TAG = "NotMe_NotifService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: NotificationService started");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Extract basic information from the notification
        String packageName = sbn.getPackageName();
        CharSequence title = sbn.getNotification().extras.getCharSequence("android.title");
        CharSequence text = sbn.getNotification().extras.getCharSequence("android.text");

        // Safe conversion to String
        String titleStr = (title != null) ? title.toString() : "No title";
        String textStr = (text != null) ? text.toString() : "No text";

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        // Extract new metadata fields
        boolean isOngoing = (sbn.getNotification().flags & android.app.Notification.FLAG_ONGOING_EVENT) != 0;

        // Get category (Social, Email, Service, etc.)
        String category = sbn.getNotification().category;
        if (category == null) {
            category = "Uncategorized";
        }

        // Get action count
        int actionCount = 0;
        if (sbn.getNotification().actions != null) {
            actionCount = sbn.getNotification().actions.length;
        }

        // Log consolidated notification info
        Log.d(TAG, String.format("onNotificationPosted: pkg=%s, title='%s', ongoing=%b, category=%s, actions=%d",
            packageName, titleStr, isOngoing, category, actionCount));

        // Save using DataRepository with new metadata fields
        DataRepository.save(this, packageName, titleStr, textStr, timestamp, isOngoing, category, actionCount);
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
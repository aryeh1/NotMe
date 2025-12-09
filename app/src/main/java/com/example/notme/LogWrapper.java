package com.example.notme;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * LogWrapper provides an internal log buffer for the app.
 * All logs are written to both standard Logcat and an in-memory circular buffer.
 */
public class LogWrapper {
    private static final int MAX_BUFFER_SIZE = 500;
    private static LogWrapper instance;

    private final List<LogEntry> buffer;
    private final Object lock = new Object();

    private LogWrapper() {
        buffer = new ArrayList<>();
    }

    public static LogWrapper getInstance() {
        if (instance == null) {
            synchronized (LogWrapper.class) {
                if (instance == null) {
                    instance = new LogWrapper();
                }
            }
        }
        return instance;
    }

    /**
     * Log a debug message
     */
    public static void d(String tag, String message) {
        Log.d(tag, message);
        getInstance().addToBuffer("D", tag, message);
    }

    /**
     * Log an info message
     */
    public static void i(String tag, String message) {
        Log.i(tag, message);
        getInstance().addToBuffer("I", tag, message);
    }

    /**
     * Log a warning message
     */
    public static void w(String tag, String message) {
        Log.w(tag, message);
        getInstance().addToBuffer("W", tag, message);
    }

    /**
     * Log an error message
     */
    public static void e(String tag, String message) {
        Log.e(tag, message);
        getInstance().addToBuffer("E", tag, message);
    }

    /**
     * Log an error message with exception
     */
    public static void e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);

        // Capture full stack trace
        StringBuilder fullMessage = new StringBuilder(message);
        fullMessage.append("\n  Exception: ").append(throwable.toString());

        // Add stack trace elements
        StackTraceElement[] stackTrace = throwable.getStackTrace();
        int maxFrames = Math.min(stackTrace.length, 10); // Limit to 10 frames
        for (int i = 0; i < maxFrames; i++) {
            fullMessage.append("\n    at ").append(stackTrace[i].toString());
        }

        if (stackTrace.length > maxFrames) {
            fullMessage.append("\n    ... ").append(stackTrace.length - maxFrames).append(" more");
        }

        // Add cause if present
        Throwable cause = throwable.getCause();
        if (cause != null) {
            fullMessage.append("\n  Caused by: ").append(cause.toString());
            StackTraceElement[] causeTrace = cause.getStackTrace();
            int maxCauseFrames = Math.min(causeTrace.length, 5);
            for (int i = 0; i < maxCauseFrames; i++) {
                fullMessage.append("\n    at ").append(causeTrace[i].toString());
            }
            if (causeTrace.length > maxCauseFrames) {
                fullMessage.append("\n    ... ").append(causeTrace.length - maxCauseFrames).append(" more");
            }
        }

        getInstance().addToBuffer("E", tag, fullMessage.toString());
    }

    private void addToBuffer(String level, String tag, String message) {
        synchronized (lock) {
            String timestamp = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());

            LogEntry entry = new LogEntry(timestamp, level, tag, message);
            buffer.add(entry);

            // Remove oldest entries if buffer is full
            while (buffer.size() > MAX_BUFFER_SIZE) {
                buffer.remove(0);
            }
        }
    }

    /**
     * Get all log entries
     */
    public List<LogEntry> getAllLogs() {
        synchronized (lock) {
            return new ArrayList<>(buffer);
        }
    }

    /**
     * Get filtered logs by level
     */
    public List<LogEntry> getFilteredLogs(String level) {
        synchronized (lock) {
            if (level == null || level.equals("ALL")) {
                return new ArrayList<>(buffer);
            }

            List<LogEntry> filtered = new ArrayList<>();
            for (LogEntry entry : buffer) {
                if (entry.level.equals(level)) {
                    filtered.add(entry);
                }
            }
            return filtered;
        }
    }

    /**
     * Clear the buffer
     */
    public void clear() {
        synchronized (lock) {
            buffer.clear();
        }
    }

    /**
     * Get buffer size
     */
    public int getSize() {
        synchronized (lock) {
            return buffer.size();
        }
    }

    /**
     * Log entry data class
     */
    public static class LogEntry {
        public final String timestamp;
        public final String level;
        public final String tag;
        public final String message;

        public LogEntry(String timestamp, String level, String tag, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.tag = tag;
            this.message = message;
        }

        @Override
        public String toString() {
            return timestamp + " " + level + "/" + tag + ": " + message;
        }

        public int getColor() {
            switch (level) {
                case "D": return 0xFF4CAF50; // Green
                case "I": return 0xFF2196F3; // Blue
                case "W": return 0xFFFFA726; // Orange
                case "E": return 0xFFF44336; // Red
                default: return 0xFF212121;  // Dark gray
            }
        }
    }
}

package com.example.notme;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogcatActivity extends AppCompatActivity {
    private static final String TAG = "NotMe_LogcatActivity";
    private static final int MAX_LINES = 1000; // Maximum lines to keep in memory

    private TextView logText;
    private TextView statusText;
    private TextView logCountText;
    private ScrollView logScrollView;
    private Button filterAll, filterDebug, filterInfo, filterWarn, filterError;

    private ExecutorService executorService;
    private Process logcatProcess;
    private BufferedReader logcatReader;
    private Handler mainHandler;

    private SpannableStringBuilder logBuffer;
    private int lineCount = 0;
    private String currentFilter = "ALL";
    private boolean autoScroll = true;
    private int myPid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Logcat console started");

        setContentView(R.layout.activity_logcat);

        myPid = android.os.Process.myPid();
        Log.d(TAG, "onCreate: Current process PID=" + myPid);

        initializeViews();
        setupFilterButtons();
        mainHandler = new Handler(Looper.getMainLooper());
        logBuffer = new SpannableStringBuilder();

        executorService = Executors.newSingleThreadExecutor();
        startLogcatReader();
    }

    private void initializeViews() {
        logText = findViewById(R.id.logText);
        statusText = findViewById(R.id.statusText);
        logCountText = findViewById(R.id.logCountText);
        logScrollView = findViewById(R.id.logScrollView);

        filterAll = findViewById(R.id.filterAll);
        filterDebug = findViewById(R.id.filterDebug);
        filterInfo = findViewById(R.id.filterInfo);
        filterWarn = findViewById(R.id.filterWarn);
        filterError = findViewById(R.id.filterError);

        Button backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> {
            Log.d(TAG, "Back button pressed, finishing activity");
            finish();
        });

        Button clearBtn = findViewById(R.id.clearBtn);
        clearBtn.setOnClickListener(v -> {
            Log.d(TAG, "Clear button pressed, clearing log buffer");
            clearLogs();
        });

        Button scrollDownBtn = findViewById(R.id.scrollDownBtn);
        scrollDownBtn.setOnClickListener(v -> scrollToBottom());

        // Detect manual scrolling
        logScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            int scrollY = logScrollView.getScrollY();
            int maxScrollY = logText.getHeight() - logScrollView.getHeight();
            autoScroll = (maxScrollY <= 0 || scrollY >= maxScrollY - 50);
        });
    }

    private void setupFilterButtons() {
        View.OnClickListener filterListener = v -> {
            Button clicked = (Button) v;

            // Update filter state
            if (clicked == filterAll) {
                currentFilter = "ALL";
            } else if (clicked == filterDebug) {
                currentFilter = "D";
            } else if (clicked == filterInfo) {
                currentFilter = "I";
            } else if (clicked == filterWarn) {
                currentFilter = "W";
            } else if (clicked == filterError) {
                currentFilter = "E";
            }

            Log.d(TAG, "Filter changed to: " + currentFilter);
            updateFilterButtons();
            restartLogcatReader();
        };

        filterAll.setOnClickListener(filterListener);
        filterDebug.setOnClickListener(filterListener);
        filterInfo.setOnClickListener(filterListener);
        filterWarn.setOnClickListener(filterListener);
        filterError.setOnClickListener(filterListener);
    }

    private void updateFilterButtons() {
        // Reset all to outlined style
        resetButtonStyle(filterAll);
        resetButtonStyle(filterDebug);
        resetButtonStyle(filterInfo);
        resetButtonStyle(filterWarn);
        resetButtonStyle(filterError);

        // Highlight active filter
        Button activeButton = null;
        switch (currentFilter) {
            case "ALL": activeButton = filterAll; break;
            case "D": activeButton = filterDebug; break;
            case "I": activeButton = filterInfo; break;
            case "W": activeButton = filterWarn; break;
            case "E": activeButton = filterError; break;
        }

        if (activeButton != null) {
            activeButton.setAlpha(1.0f);
        }
    }

    private void resetButtonStyle(Button button) {
        button.setAlpha(0.6f);
    }

    private void startLogcatReader() {
        Log.d(TAG, "startLogcatReader: Starting logcat reader with filter=" + currentFilter);

        executorService.execute(() -> {
            try {
                // Clear previous logcat buffer and start fresh
                String[] clearCmd = {"logcat", "-c"};
                Runtime.getRuntime().exec(clearCmd).waitFor();

                // Build logcat command with PID filter
                String filterSpec = currentFilter.equals("ALL")
                    ? "*:D"
                    : "*:" + currentFilter;

                String[] logcatCmd = {
                    "logcat",
                    "-v", "time",  // Include timestamp
                    "--pid=" + myPid,  // Filter by our process ID
                    filterSpec
                };

                Log.d(TAG, "startLogcatReader: Executing logcat command");
                logcatProcess = Runtime.getRuntime().exec(logcatCmd);
                logcatReader = new BufferedReader(
                    new InputStreamReader(logcatProcess.getInputStream()), 8192);

                mainHandler.post(() -> {
                    statusText.setText("Streaming logs (PID: " + myPid + ") - Filter: " + currentFilter);
                    Log.d(TAG, "startLogcatReader: Logcat streaming started");
                });

                String line;
                while ((line = logcatReader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    final String logLine = line;
                    mainHandler.post(() -> appendLogLine(logLine));
                }

            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "startLogcatReader: Error reading logcat", e);
                mainHandler.post(() ->
                    statusText.setText("Error: " + e.getMessage()));
            }
        });
    }

    private void appendLogLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return;
        }

        // Parse log level and colorize
        SpannableStringBuilder coloredLine = new SpannableStringBuilder();
        int color = Color.WHITE;

        // Detect log level (format: MM-DD HH:MM:SS.mmm D/TAG: message)
        if (line.contains(" D/")) {
            color = 0xFF4CAF50; // Green for Debug
        } else if (line.contains(" I/")) {
            color = 0xFF2196F3; // Blue for Info
        } else if (line.contains(" W/")) {
            color = 0xFFFFC107; // Amber for Warning
        } else if (line.contains(" E/")) {
            color = 0xFFF44336; // Red for Error
        }

        coloredLine.append(line);
        coloredLine.append("\n");

        // Apply color to entire line
        coloredLine.setSpan(
            new ForegroundColorSpan(color),
            0,
            line.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        // Make log level bold
        int levelStart = line.indexOf(" D/");
        if (levelStart < 0) levelStart = line.indexOf(" I/");
        if (levelStart < 0) levelStart = line.indexOf(" W/");
        if (levelStart < 0) levelStart = line.indexOf(" E/");

        if (levelStart > 0 && levelStart + 3 < line.length()) {
            coloredLine.setSpan(
                new StyleSpan(Typeface.BOLD),
                levelStart + 1,
                levelStart + 2,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        logBuffer.append(coloredLine);
        lineCount++;

        // Trim buffer if too large
        if (lineCount > MAX_LINES) {
            int firstNewline = logBuffer.toString().indexOf('\n');
            if (firstNewline > 0) {
                logBuffer.delete(0, firstNewline + 1);
                lineCount--;
            }
        }

        logText.setText(logBuffer);
        logCountText.setText("Lines: " + lineCount);

        if (autoScroll) {
            scrollToBottom();
        }
    }

    private void clearLogs() {
        logBuffer.clear();
        lineCount = 0;
        logText.setText("Logs cleared. Waiting for new logs...");
        logCountText.setText("Lines: 0");
        Log.d(TAG, "clearLogs: Log buffer cleared");
    }

    private void scrollToBottom() {
        logScrollView.post(() ->
            logScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void restartLogcatReader() {
        Log.d(TAG, "restartLogcatReader: Restarting with new filter");
        stopLogcatReader();
        clearLogs();
        executorService.execute(() -> {
            try {
                Thread.sleep(100); // Small delay before restart
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            startLogcatReader();
        });
    }

    private void stopLogcatReader() {
        Log.d(TAG, "stopLogcatReader: Stopping logcat reader");

        if (logcatReader != null) {
            try {
                logcatReader.close();
            } catch (IOException e) {
                Log.e(TAG, "stopLogcatReader: Error closing reader", e);
            }
            logcatReader = null;
        }

        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Cleaning up resources");

        stopLogcatReader();

        if (executorService != null) {
            executorService.shutdownNow();
        }

        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Logcat console visible");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Logcat console paused");
    }
}

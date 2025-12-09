package com.example.notme;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogcatActivity extends AppCompatActivity {
    private static final String TAG = "NotMe_LogcatActivity";
    private static final int MAX_LINES = 500;

    private TextView logText;
    private TextView statusText;
    private TextView logCountText;
    private TextView currentFilterText;
    private TextView expandIcon;
    private ScrollView logScrollView;
    private LinearLayout filterPanel;

    private ExecutorService executorService;
    private Process logcatProcess;
    private BufferedReader logcatReader;
    private Handler mainHandler;

    private SpannableStringBuilder logBuffer;
    private int lineCount = 0;
    private String currentFilter = "ALL";
    private boolean autoScroll = true;
    private boolean filterExpanded = false;
    private int myPid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Logcat console started");

        setContentView(R.layout.activity_logcat);

        myPid = android.os.Process.myPid();
        Log.d(TAG, "onCreate: Current process PID=" + myPid);

        initializeViews();
        setupListeners();
        mainHandler = new Handler(Looper.getMainLooper());
        logBuffer = new SpannableStringBuilder();

        executorService = Executors.newSingleThreadExecutor();
        startLogcatReader();
    }

    private void initializeViews() {
        logText = findViewById(R.id.txt_log_content);
        statusText = findViewById(R.id.txt_status);
        logCountText = findViewById(R.id.txt_log_count);
        currentFilterText = findViewById(R.id.txt_current_filter);
        expandIcon = findViewById(R.id.txt_expand_icon);
        logScrollView = findViewById(R.id.log_scroll_view);
        filterPanel = findViewById(R.id.filter_panel);

        Button backBtn = findViewById(R.id.btn_back);
        backBtn.setOnClickListener(v -> finish());

        Button clearBtn = findViewById(R.id.btn_clear);
        clearBtn.setOnClickListener(v -> clearLogs());

        // Detect manual scrolling
        logScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            int scrollY = logScrollView.getScrollY();
            int maxScrollY = logText.getHeight() - logScrollView.getHeight();
            autoScroll = (maxScrollY <= 0 || scrollY >= maxScrollY - 50);
        });
    }

    private void setupListeners() {
        // Toggle filter panel
        View filterHeader = findViewById(R.id.filter_header);
        filterHeader.setOnClickListener(v -> toggleFilterPanel());

        // Filter buttons
        findViewById(R.id.btn_filter_all).setOnClickListener(v -> setFilter("ALL"));
        findViewById(R.id.btn_filter_debug).setOnClickListener(v -> setFilter("D"));
        findViewById(R.id.btn_filter_info).setOnClickListener(v -> setFilter("I"));
        findViewById(R.id.btn_filter_warn).setOnClickListener(v -> setFilter("W"));
        findViewById(R.id.btn_filter_error).setOnClickListener(v -> setFilter("E"));
    }

    private void toggleFilterPanel() {
        if (filterExpanded) {
            collapse(filterPanel);
            expandIcon.setText("▼");
        } else {
            expand(filterPanel);
            expandIcon.setText("▲");
        }
        filterExpanded = !filterExpanded;
    }

    private void expand(final View view) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        final int targetHeight = view.getMeasuredHeight();

        view.getLayoutParams().height = 1;
        view.setVisibility(View.VISIBLE);

        Animation animation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                view.getLayoutParams().height = interpolatedTime == 1
                        ? LinearLayout.LayoutParams.WRAP_CONTENT
                        : (int)(targetHeight * interpolatedTime);
                view.requestLayout();
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        animation.setDuration(200);
        view.startAnimation(animation);
    }

    private void collapse(final View view) {
        final int initialHeight = view.getMeasuredHeight();

        Animation animation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    view.setVisibility(View.GONE);
                } else {
                    view.getLayoutParams().height = initialHeight - (int)(initialHeight * interpolatedTime);
                    view.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        animation.setDuration(200);
        view.startAnimation(animation);
    }

    private void setFilter(String filter) {
        currentFilter = filter;
        currentFilterText.setText(filter);
        Log.d(TAG, "Filter changed to: " + filter);

        // Collapse filter panel after selection
        if (filterExpanded) {
            toggleFilterPanel();
        }

        restartLogcatReader();
    }

    private void startLogcatReader() {
        Log.d(TAG, "startLogcatReader: Starting with filter=" + currentFilter);

        executorService.execute(() -> {
            try {
                // Clear previous logcat
                Runtime.getRuntime().exec(new String[]{"logcat", "-c"}).waitFor();

                // Build logcat command
                String filterSpec = currentFilter.equals("ALL") ? "*:D" : "*:" + currentFilter;
                String[] cmd = {"logcat", "-v", "time", "--pid=" + myPid, filterSpec};

                logcatProcess = Runtime.getRuntime().exec(cmd);
                logcatReader = new BufferedReader(
                    new InputStreamReader(logcatProcess.getInputStream()), 8192);

                mainHandler.post(() -> {
                    statusText.setText("Streaming logs (PID: " + myPid + ")");
                    Log.d(TAG, "startLogcatReader: Streaming started");
                });

                String line;
                while ((line = logcatReader.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) break;
                    final String logLine = line;
                    mainHandler.post(() -> appendLogLine(logLine));
                }

            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "startLogcatReader: Error", e);
                mainHandler.post(() -> statusText.setText("Error: " + e.getMessage()));
            }
        });
    }

    private void appendLogLine(String line) {
        if (line == null || line.trim().isEmpty()) return;

        SpannableStringBuilder coloredLine = new SpannableStringBuilder();
        int color = 0xFF212121; // Default dark gray

        // Colorize by log level
        if (line.contains(" D/")) {
            color = 0xFF4CAF50; // Green
        } else if (line.contains(" I/")) {
            color = 0xFF2196F3; // Blue
        } else if (line.contains(" W/")) {
            color = 0xFFFFA726; // Orange
        } else if (line.contains(" E/")) {
            color = 0xFFF44336; // Red
        }

        coloredLine.append(line);
        coloredLine.append("\n");

        coloredLine.setSpan(
            new ForegroundColorSpan(color),
            0, line.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        logBuffer.append(coloredLine);
        lineCount++;

        // Trim if too large
        if (lineCount > MAX_LINES) {
            int firstNewline = logBuffer.toString().indexOf('\n');
            if (firstNewline > 0) {
                logBuffer.delete(0, firstNewline + 1);
                lineCount--;
            }
        }

        logText.setText(logBuffer);
        logCountText.setText(lineCount + " lines");

        if (autoScroll) {
            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void clearLogs() {
        logBuffer.clear();
        lineCount = 0;
        logText.setText("Logs cleared. Waiting for new logs...");
        logCountText.setText("0 lines");
        Log.d(TAG, "clearLogs: Cleared");
    }

    private void restartLogcatReader() {
        Log.d(TAG, "restartLogcatReader: Restarting");
        stopLogcatReader();
        clearLogs();
        executorService.execute(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            startLogcatReader();
        });
    }

    private void stopLogcatReader() {
        Log.d(TAG, "stopLogcatReader: Stopping");

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
        Log.d(TAG, "onDestroy: Cleaning up");

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
        Log.d(TAG, "onResume: Console visible");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Console paused");
    }
}

package com.example.notme;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class LogcatActivity extends AppCompatActivity {
    private static final String TAG = "NotMe_LogcatActivity";
    private static final int UPDATE_INTERVAL_MS = 3000; // 3 seconds

    private TextView logText;
    private TextView statusText;
    private TextView logCountText;
    private TextView currentFilterText;
    private TextView expandIcon;
    private ScrollView logScrollView;
    private LinearLayout filterPanel;

    private Handler updateHandler;
    private Runnable updateRunnable;

    private String currentFilter = "ALL";
    private boolean autoScroll = true;
    private boolean filterExpanded = false;
    private int myPid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogWrapper.d(TAG, "onCreate: Logcat console started");

        setContentView(R.layout.activity_logcat);

        myPid = android.os.Process.myPid();
        LogWrapper.d(TAG, "onCreate: Current process PID=" + myPid);

        initializeViews();
        setupListeners();

        updateHandler = new Handler(Looper.getMainLooper());

        // Start periodic updates
        startPeriodicUpdates();
    }

    private void initializeViews() {
        logText = findViewById(R.id.txt_log_content);
        statusText = findViewById(R.id.txt_status);
        logCountText = findViewById(R.id.txt_log_count);
        currentFilterText = findViewById(R.id.txt_current_filter);
        expandIcon = findViewById(R.id.txt_expand_icon);
        logScrollView = findViewById(R.id.log_scroll_view);
        filterPanel = findViewById(R.id.filter_panel);

        statusText.setText("Showing app logs since launch (PID: " + myPid + ", updates every 3s)");

        // Detect manual scrolling
        logScrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            int scrollY = logScrollView.getScrollY();
            int maxScrollY = logText.getHeight() - logScrollView.getHeight();
            autoScroll = (maxScrollY <= 0 || scrollY >= maxScrollY - 50);
        });
    }

    private void setupListeners() {
        Button backBtn = findViewById(R.id.btn_back);
        backBtn.setOnClickListener(v -> finish());

        Button clearBtn = findViewById(R.id.btn_clear);
        clearBtn.setOnClickListener(v -> clearLogs());

        Button copyBtn = findViewById(R.id.btn_copy);
        copyBtn.setOnClickListener(v -> copyToClipboard());

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

    private void startPeriodicUpdates() {
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateLogDisplay();
                updateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        };
        updateHandler.post(updateRunnable);
    }

    private void stopPeriodicUpdates() {
        if (updateHandler != null && updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }

    private void updateLogDisplay() {
        List<LogWrapper.LogEntry> entries = LogWrapper.getInstance().getFilteredLogs(currentFilter);

        SpannableStringBuilder builder = new SpannableStringBuilder();

        for (int i = 0; i < entries.size(); i++) {
            LogWrapper.LogEntry entry = entries.get(i);
            String line = entry.toString();
            int start = builder.length();

            // Add visual separator line
            builder.append("─────────────────────────────────\n");

            builder.append(line);
            builder.append("\n");
            int end = builder.length() - 1;

            // Color the entire log entry
            builder.setSpan(
                new ForegroundColorSpan(entry.getColor()),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        // Add final separator
        if (entries.size() > 0) {
            builder.append("─────────────────────────────────\n");
        }

        logText.setText(builder);
        logCountText.setText(entries.size() + " lines");

        if (autoScroll) {
            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        }
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
        LogWrapper.d(TAG, "Filter changed to: " + filter);

        // Collapse filter panel after selection
        if (filterExpanded) {
            toggleFilterPanel();
        }

        // Immediately update display
        updateLogDisplay();
    }

    private void clearLogs() {
        LogWrapper.getInstance().clear();
        logText.setText("Logs cleared. Waiting for new logs...");
        logCountText.setText("0 lines");
        Toast.makeText(this, "Internal buffer cleared", Toast.LENGTH_SHORT).show();
        LogWrapper.d(TAG, "clearLogs: Internal buffer cleared");
    }

    private void copyToClipboard() {
        List<LogWrapper.LogEntry> entries = LogWrapper.getInstance().getFilteredLogs(currentFilter);

        if (entries.isEmpty()) {
            Toast.makeText(this, "No logs to copy", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder text = new StringBuilder();
        for (LogWrapper.LogEntry entry : entries) {
            text.append(entry.toString()).append("\n");
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Logcat Console", text.toString());
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, "Copied " + entries.size() + " lines to clipboard", Toast.LENGTH_SHORT).show();
        LogWrapper.d(TAG, "copyToClipboard: Copied " + entries.size() + " lines");
    }

    @Override
    protected void onResume() {
        super.onResume();
        LogWrapper.d(TAG, "onResume: Console visible");
        startPeriodicUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        LogWrapper.d(TAG, "onPause: Console paused");
        stopPeriodicUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogWrapper.d(TAG, "onDestroy: Cleaning up");

        stopPeriodicUpdates();

        if (updateHandler != null) {
            updateHandler.removeCallbacksAndMessages(null);
        }
    }
}

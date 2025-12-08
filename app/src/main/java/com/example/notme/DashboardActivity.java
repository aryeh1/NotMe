package com.example.notme;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.notme.data.AppDatabase;
import com.example.notme.data.NotificationDao;
import com.example.notme.data.NotificationEntity;
import com.google.android.material.card.MaterialCardView;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardActivity extends AppCompatActivity {

    private TextView txtTotalCount;
    private TextView txtOngoingCount;
    private TextView txtRegularCount;
    private TextView txtTopApps;
    private TextView txtCategories;
    private TextView txtHourly;
    private TextView txtLast7Days;

    private MaterialCardView cardTopApps;
    private MaterialCardView cardCategories;
    private MaterialCardView cardHourly;
    private MaterialCardView cardLast7Days;

    private ExecutorService executor;
    private NotificationDao dao;

    private List<NotificationDao.PackageCount> topPackages;
    private List<NotificationDao.CategoryCount> categories;
    private List<NotificationDao.DayCount> last7Days;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize views
        txtTotalCount = findViewById(R.id.txt_total_count);
        txtOngoingCount = findViewById(R.id.txt_ongoing_count);
        txtRegularCount = findViewById(R.id.txt_regular_count);
        txtTopApps = findViewById(R.id.txt_top_apps);
        txtCategories = findViewById(R.id.txt_categories);
        txtHourly = findViewById(R.id.txt_hourly);
        txtLast7Days = findViewById(R.id.txt_last_7_days);

        cardTopApps = findViewById(R.id.card_top_apps);
        cardCategories = findViewById(R.id.card_categories);
        cardHourly = findViewById(R.id.card_hourly);
        cardLast7Days = findViewById(R.id.card_last_7_days);

        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Initialize database access
        executor = Executors.newSingleThreadExecutor();
        dao = AppDatabase.getInstance(this).dao();

        // Set up click listeners for drill-down
        setupClickListeners();

        // Load dashboard data
        loadDashboardData();
    }

    private void setupClickListeners() {
        cardTopApps.setOnClickListener(v -> showTopAppsDetails());
        cardCategories.setOnClickListener(v -> showCategoriesDetails());
        cardHourly.setOnClickListener(v -> showHourlyDetails());
        cardLast7Days.setOnClickListener(v -> showDaysDetails());
    }

    private void loadDashboardData() {
        executor.execute(() -> {
            // Get counts
            int totalCount = dao.getTotalCount();
            int ongoingCount = dao.getOngoingCount();
            int regularCount = dao.getRegularCount();

            // Get top packages
            topPackages = dao.getTopPackages();
            StringBuilder topAppsText = new StringBuilder();
            int maxCount = topPackages.isEmpty() ? 1 : topPackages.get(0).count;
            for (NotificationDao.PackageCount pc : topPackages) {
                String appName = extractAppName(pc.packageName);
                String bar = createBar(pc.count, maxCount);
                topAppsText.append(String.format(Locale.getDefault(),
                    "%s %s (%,d)\n", bar, appName, pc.count));
            }

            // Get categories
            categories = dao.getCategoryBreakdown();
            StringBuilder categoriesText = new StringBuilder();
            maxCount = categories.isEmpty() ? 1 : categories.get(0).count;
            for (NotificationDao.CategoryCount cc : categories) {
                String category = cc.category == null || cc.category.isEmpty() ?
                    "Uncategorized" : cc.category;
                String bar = createBar(cc.count, maxCount);
                categoriesText.append(String.format(Locale.getDefault(),
                    "%s %s (%,d)\n", bar, category, cc.count));
            }

            // Get hourly distribution
            List<NotificationDao.HourCount> hourly = dao.getHourlyDistribution();
            StringBuilder hourlyText = new StringBuilder();
            maxCount = 1;
            for (NotificationDao.HourCount hc : hourly) {
                if (hc.count > maxCount) maxCount = hc.count;
            }

            // Show only top 8 hours
            hourly.sort((a, b) -> Integer.compare(b.count, a.count));
            for (int i = 0; i < Math.min(8, hourly.size()); i++) {
                NotificationDao.HourCount hc = hourly.get(i);
                String bar = createBar(hc.count, maxCount);
                String timeLabel = String.format(Locale.getDefault(), "%s:00", hc.hour);
                hourlyText.append(String.format(Locale.getDefault(),
                    "%s %s (%,d)\n", bar, timeLabel, hc.count));
            }

            // Get last 7 days
            last7Days = dao.getLast7Days();
            StringBuilder daysText = new StringBuilder();
            maxCount = 1;
            for (NotificationDao.DayCount dc : last7Days) {
                if (dc.count > maxCount) maxCount = dc.count;
            }
            for (NotificationDao.DayCount dc : last7Days) {
                String bar = createBar(dc.count, maxCount);
                daysText.append(String.format(Locale.getDefault(),
                    "%s %s (%,d)\n", bar, dc.date, dc.count));
            }

            // Update UI on main thread
            runOnUiThread(() -> {
                txtTotalCount.setText(String.format(Locale.getDefault(), "%,d", totalCount));
                txtOngoingCount.setText(String.format(Locale.getDefault(), "%,d", ongoingCount));
                txtRegularCount.setText(String.format(Locale.getDefault(), "%,d", regularCount));

                txtTopApps.setText(topAppsText.length() > 0 ?
                    topAppsText.toString().trim() : "No data yet");
                txtCategories.setText(categoriesText.length() > 0 ?
                    categoriesText.toString().trim() : "No data yet");
                txtHourly.setText(hourlyText.length() > 0 ?
                    hourlyText.toString().trim() : "No data yet");
                txtLast7Days.setText(daysText.length() > 0 ?
                    daysText.toString().trim() : "No data yet");
            });
        });
    }

    private void showTopAppsDetails() {
        if (topPackages == null || topPackages.isEmpty()) {
            showMessage("No Data", "No app data available yet.");
            return;
        }

        CharSequence[] items = new CharSequence[topPackages.size()];
        for (int i = 0; i < topPackages.size(); i++) {
            NotificationDao.PackageCount pc = topPackages.get(i);
            items[i] = String.format(Locale.getDefault(), "%s (%,d notifications)",
                extractAppName(pc.packageName), pc.count);
        }

        new AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("📱 Top Apps - Select to View History")
            .setItems(items, (dialog, which) -> {
                NotificationDao.PackageCount selected = topPackages.get(which);
                showAppHistory(selected.packageName);
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void showAppHistory(String packageName) {
        executor.execute(() -> {
            List<NotificationDao.DayCount> history = dao.getPackageHistory(packageName);
            StringBuilder historyText = new StringBuilder();

            if (history.isEmpty()) {
                historyText.append("No history available");
            } else {
                int maxCount = history.get(0).count;
                for (NotificationDao.DayCount dc : history) {
                    String bar = createBar(dc.count, maxCount);
                    historyText.append(String.format(Locale.getDefault(),
                        "%s  %s (%,d)\n", bar, dc.date, dc.count));
                }
            }

            runOnUiThread(() -> {
                new AlertDialog.Builder(this, R.style.DialogTheme)
                    .setTitle("📊 " + extractAppName(packageName) + " - 30 Day History")
                    .setMessage(historyText.toString().trim())
                    .setPositiveButton("OK", null)
                    .show();
            });
        });
    }

    private void showCategoriesDetails() {
        if (categories == null || categories.isEmpty()) {
            showMessage("No Data", "No category data available yet.");
            return;
        }

        CharSequence[] items = new CharSequence[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            NotificationDao.CategoryCount cc = categories.get(i);
            String category = cc.category == null || cc.category.isEmpty() ?
                "Uncategorized" : cc.category;
            items[i] = String.format(Locale.getDefault(), "%s (%,d notifications)",
                category, cc.count);
        }

        new AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("📂 Categories - Select to View History")
            .setItems(items, (dialog, which) -> {
                NotificationDao.CategoryCount selected = categories.get(which);
                showCategoryHistory(selected.category);
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void showCategoryHistory(String category) {
        executor.execute(() -> {
            List<NotificationDao.DayCount> history = dao.getCategoryHistory(category);
            StringBuilder historyText = new StringBuilder();

            if (history.isEmpty()) {
                historyText.append("No history available");
            } else {
                int maxCount = history.get(0).count;
                for (NotificationDao.DayCount dc : history) {
                    String bar = createBar(dc.count, maxCount);
                    historyText.append(String.format(Locale.getDefault(),
                        "%s  %s (%,d)\n", bar, dc.date, dc.count));
                }
            }

            String categoryName = category == null || category.isEmpty() ?
                "Uncategorized" : category;

            runOnUiThread(() -> {
                new AlertDialog.Builder(this, R.style.DialogTheme)
                    .setTitle("📊 " + categoryName + " - 30 Day History")
                    .setMessage(historyText.toString().trim())
                    .setPositiveButton("OK", null)
                    .show();
            });
        });
    }

    private void showHourlyDetails() {
        executor.execute(() -> {
            List<NotificationDao.HourCount> hourly = dao.getHourlyDistribution();
            StringBuilder detailText = new StringBuilder();

            if (hourly.isEmpty()) {
                detailText.append("No hourly data available");
            } else {
                int maxCount = 1;
                for (NotificationDao.HourCount hc : hourly) {
                    if (hc.count > maxCount) maxCount = hc.count;
                }

                // Sort by hour
                hourly.sort((a, b) -> Integer.compare(
                    Integer.parseInt(a.hour), Integer.parseInt(b.hour)));

                for (NotificationDao.HourCount hc : hourly) {
                    String bar = createBar(hc.count, maxCount);
                    String timeLabel = String.format(Locale.getDefault(), "%s:00", hc.hour);
                    detailText.append(String.format(Locale.getDefault(),
                        "%s  %s (%,d)\n", bar, timeLabel, hc.count));
                }
            }

            runOnUiThread(() -> {
                new AlertDialog.Builder(this, R.style.DialogTheme)
                    .setTitle("🕐 24-Hour Activity Distribution")
                    .setMessage(detailText.toString().trim())
                    .setPositiveButton("OK", null)
                    .show();
            });
        });
    }

    private void showDaysDetails() {
        if (last7Days == null || last7Days.isEmpty()) {
            showMessage("No Data", "No daily data available yet.");
            return;
        }

        CharSequence[] items = new CharSequence[last7Days.size()];
        for (int i = 0; i < last7Days.size(); i++) {
            NotificationDao.DayCount dc = last7Days.get(i);
            items[i] = String.format(Locale.getDefault(), "%s (%,d notifications)",
                dc.date, dc.count);
        }

        new AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("📅 Last 7 Days - Select Day to View Details")
            .setItems(items, (dialog, which) -> {
                NotificationDao.DayCount selected = last7Days.get(which);
                showDayDetails(selected.date);
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void showDayDetails(String date) {
        executor.execute(() -> {
            List<NotificationEntity> notifications = dao.getNotificationsByDate(date);
            StringBuilder detailText = new StringBuilder();

            if (notifications.isEmpty()) {
                detailText.append("No notifications for this date");
            } else {
                detailText.append(String.format(Locale.getDefault(),
                    "Total: %,d notifications\n\n", notifications.size()));

                // Show first 20 notifications
                int limit = Math.min(20, notifications.size());
                for (int i = 0; i < limit; i++) {
                    NotificationEntity n = notifications.get(i);
                    String time = n.getTimestamp().substring(11, 16); // HH:MM
                    String app = extractAppName(n.getPackageName());
                    String title = n.getTitle() != null && !n.getTitle().isEmpty() ?
                        n.getTitle() : "No title";

                    detailText.append(String.format(Locale.getDefault(),
                        "%s • %s\n%s\n\n", time, app, title));
                }

                if (notifications.size() > 20) {
                    detailText.append(String.format(Locale.getDefault(),
                        "...and %d more", notifications.size() - 20));
                }
            }

            runOnUiThread(() -> {
                new AlertDialog.Builder(this, R.style.DialogTheme)
                    .setTitle("📅 " + date)
                    .setMessage(detailText.toString().trim())
                    .setPositiveButton("OK", null)
                    .show();
            });
        });
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    private String extractAppName(String packageName) {
        if (packageName == null) return "Unknown";

        // Remove common prefixes
        String name = packageName;
        if (name.startsWith("com.")) name = name.substring(4);
        if (name.startsWith("android.")) name = name.substring(8);
        if (name.startsWith("google.android.")) name = name.substring(15);

        // Get first part after prefix
        int dotIndex = name.indexOf('.');
        if (dotIndex > 0) {
            name = name.substring(0, dotIndex);
        }

        return name;
    }

    private String createBar(int count, int maxCount) {
        if (maxCount == 0) return "";

        int barLength = (int) ((count * 15.0) / maxCount);
        if (barLength < 1 && count > 0) barLength = 1;

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            bar.append("█");
        }

        // Add lighter bars for remaining space
        for (int i = barLength; i < 15; i++) {
            bar.append("░");
        }

        return bar.toString();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}

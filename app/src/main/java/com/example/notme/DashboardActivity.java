package com.example.notme;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.notme.data.AppDatabase;
import com.example.notme.data.NotificationDao;
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

    private ExecutorService executor;
    private NotificationDao dao;

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

        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Initialize database access
        executor = Executors.newSingleThreadExecutor();
        dao = AppDatabase.getInstance(this).dao();

        // Load dashboard data
        loadDashboardData();
    }

    private void loadDashboardData() {
        executor.execute(() -> {
            // Get counts
            int totalCount = dao.getTotalCount();
            int ongoingCount = dao.getOngoingCount();
            int regularCount = dao.getRegularCount();

            // Get top packages
            List<NotificationDao.PackageCount> topPackages = dao.getTopPackages();
            StringBuilder topAppsText = new StringBuilder();
            int maxCount = topPackages.isEmpty() ? 1 : topPackages.get(0).count;
            for (NotificationDao.PackageCount pc : topPackages) {
                String appName = extractAppName(pc.packageName);
                String bar = createBar(pc.count, maxCount);
                topAppsText.append(String.format(Locale.getDefault(),
                    "%s  %s (%,d)\n", bar, appName, pc.count));
            }

            // Get categories
            List<NotificationDao.CategoryCount> categories = dao.getCategoryBreakdown();
            StringBuilder categoriesText = new StringBuilder();
            maxCount = categories.isEmpty() ? 1 : categories.get(0).count;
            for (NotificationDao.CategoryCount cc : categories) {
                String category = cc.category == null || cc.category.isEmpty() ?
                    "Uncategorized" : cc.category;
                String bar = createBar(cc.count, maxCount);
                categoriesText.append(String.format(Locale.getDefault(),
                    "%s  %s (%,d)\n", bar, category, cc.count));
            }

            // Get hourly distribution
            List<NotificationDao.HourCount> hourly = dao.getHourlyDistribution();
            StringBuilder hourlyText = new StringBuilder();
            maxCount = 1;
            for (NotificationDao.HourCount hc : hourly) {
                if (hc.count > maxCount) maxCount = hc.count;
            }
            for (NotificationDao.HourCount hc : hourly) {
                String hour = hc.hour;
                String bar = createBar(hc.count, maxCount);
                String timeLabel = String.format(Locale.getDefault(), "%s:00", hour);
                hourlyText.append(String.format(Locale.getDefault(),
                    "%s  %s (%,d)\n", bar, timeLabel, hc.count));
            }

            // Get last 7 days
            List<NotificationDao.DayCount> last7Days = dao.getLast7Days();
            StringBuilder daysText = new StringBuilder();
            maxCount = 1;
            for (NotificationDao.DayCount dc : last7Days) {
                if (dc.count > maxCount) maxCount = dc.count;
            }
            for (NotificationDao.DayCount dc : last7Days) {
                String bar = createBar(dc.count, maxCount);
                daysText.append(String.format(Locale.getDefault(),
                    "%s  %s (%,d)\n", bar, dc.date, dc.count));
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

        int barLength = (int) ((count * 20.0) / maxCount);
        if (barLength < 1 && count > 0) barLength = 1;

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            bar.append("█");
        }

        // Add lighter bars for remaining space
        for (int i = barLength; i < 20; i++) {
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

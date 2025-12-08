package com.example.notme;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.example.notme.data.AppDatabase;
import com.example.notme.data.NotificationDao;
import com.example.notme.data.NotificationEntity;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResearchActivity extends AppCompatActivity {

    private static final int EXPORT_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "ResearchFilters";

    private EditText editDateFrom, editDateTo, editTextSearch;
    private Spinner spinnerApp, spinnerCategory, spinnerOngoing, spinnerSort;
    private TextView txtResultsCount, txtFilterCount, txtExpandIcon;
    private RecyclerView resultsList;
    private LinearLayout filterPanel;
    private Button btnApplyFilters, btnClearFilters, btnExport;

    private NotificationDao dao;
    private ExecutorService executor;
    private NotificationAdapter adapter;
    private List<NotificationEntity> currentResults = new ArrayList<>();

    private List<String> allPackages = new ArrayList<>();
    private List<String> allCategories = new ArrayList<>();
    private boolean filtersPanelExpanded = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_research);

        // Initialize views
        editDateFrom = findViewById(R.id.edit_date_from);
        editDateTo = findViewById(R.id.edit_date_to);
        editTextSearch = findViewById(R.id.edit_text_search);
        spinnerApp = findViewById(R.id.spinner_app);
        spinnerCategory = findViewById(R.id.spinner_category);
        spinnerOngoing = findViewById(R.id.spinner_ongoing);
        spinnerSort = findViewById(R.id.spinner_sort);
        txtResultsCount = findViewById(R.id.txt_results_count);
        txtFilterCount = findViewById(R.id.txt_filter_count);
        txtExpandIcon = findViewById(R.id.txt_expand_icon);
        resultsList = findViewById(R.id.results_list);
        filterPanel = findViewById(R.id.filter_panel);
        btnApplyFilters = findViewById(R.id.btn_apply_filters);
        btnClearFilters = findViewById(R.id.btn_clear_filters);
        btnExport = findViewById(R.id.btn_export);

        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Initialize database
        executor = Executors.newSingleThreadExecutor();
        dao = AppDatabase.getInstance(this).dao();

        // Setup RecyclerView
        adapter = new NotificationAdapter(currentResults);
        resultsList.setLayoutManager(new LinearLayoutManager(this));
        resultsList.setAdapter(adapter);

        // Setup filter collapse/expand
        findViewById(R.id.filter_header).setOnClickListener(v -> toggleFiltersPanel());

        // Setup date pickers
        editDateFrom.setOnClickListener(v -> showDatePicker(editDateFrom));
        editDateTo.setOnClickListener(v -> showDatePicker(editDateTo));

        // Setup buttons
        btnApplyFilters.setOnClickListener(v -> applyFilters());
        btnClearFilters.setOnClickListener(v -> clearFilters());
        btnExport.setOnClickListener(v -> exportResults());

        // Sort change listener
        spinnerSort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) applyFilters(); // Skip first "select" if it's a change
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Load filter options
        loadFilterOptions();
    }

    private void loadFilterOptions() {
        executor.execute(() -> {
            allPackages = dao.getAllPackages();
            allCategories = dao.getAllCategories();

            runOnUiThread(() -> {
                setupSpinners();
                loadSavedFilters();
                applyFilters();
            });
        });
    }

    private void setupSpinners() {
        // App spinner
        List<String> appOptions = new ArrayList<>();
        appOptions.add("All Apps");
        for (String pkg : allPackages) {
            appOptions.add(extractAppName(pkg));
        }
        spinnerApp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, appOptions));
        ((ArrayAdapter) spinnerApp.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Category spinner
        List<String> categoryOptions = new ArrayList<>();
        categoryOptions.add("All Categories");
        categoryOptions.addAll(allCategories);
        spinnerCategory.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categoryOptions));
        ((ArrayAdapter) spinnerCategory.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Ongoing spinner
        List<String> ongoingOptions = new ArrayList<>();
        ongoingOptions.add("All Types");
        ongoingOptions.add("Ongoing Only");
        ongoingOptions.add("Regular Only");
        spinnerOngoing.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ongoingOptions));
        ((ArrayAdapter) spinnerOngoing.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Sort spinner
        List<String> sortOptions = new ArrayList<>();
        sortOptions.add("Newest First");
        sortOptions.add("Oldest First");
        sortOptions.add("App A-Z");
        sortOptions.add("App Z-A");
        spinnerSort.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sortOptions));
        ((ArrayAdapter) spinnerSort.getAdapter()).setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    private void toggleFiltersPanel() {
        filtersPanelExpanded = !filtersPanelExpanded;
        filterPanel.setVisibility(filtersPanelExpanded ? View.VISIBLE : View.GONE);
        txtExpandIcon.setText(filtersPanelExpanded ? "▼" : "▶");
    }

    private void showDatePicker(EditText target) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            String date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
            target.setText(date);
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void applyFilters() {
        saveFilters();

        executor.execute(() -> {
            // Build dynamic SQL query
            StringBuilder sql = new StringBuilder("SELECT * FROM notifications WHERE 1=1");
            List<Object> args = new ArrayList<>();

            // Date from
            String dateFrom = editDateFrom.getText().toString().trim();
            if (!dateFrom.isEmpty()) {
                sql.append(" AND timestamp >= ?");
                args.add(dateFrom + " 00:00:00");
            }

            // Date to
            String dateTo = editDateTo.getText().toString().trim();
            if (!dateTo.isEmpty()) {
                sql.append(" AND timestamp <= ?");
                args.add(dateTo + " 23:59:59");
            }

            // App filter
            int appPos = spinnerApp.getSelectedItemPosition();
            if (appPos > 0) {
                String packageName = allPackages.get(appPos - 1);
                sql.append(" AND packageName = ?");
                args.add(packageName);
            }

            // Category filter
            int catPos = spinnerCategory.getSelectedItemPosition();
            if (catPos > 0) {
                String category = allCategories.get(catPos - 1);
                sql.append(" AND category = ?");
                args.add(category);
            }

            // Text search
            String textSearch = editTextSearch.getText().toString().trim();
            if (!textSearch.isEmpty()) {
                sql.append(" AND (title LIKE ? OR text LIKE ?)");
                String searchPattern = "%" + textSearch + "%";
                args.add(searchPattern);
                args.add(searchPattern);
            }

            // Ongoing filter
            int ongoingPos = spinnerOngoing.getSelectedItemPosition();
            if (ongoingPos == 1) { // Ongoing Only
                sql.append(" AND isOngoing = 1");
            } else if (ongoingPos == 2) { // Regular Only
                sql.append(" AND isOngoing = 0");
            }

            // Sort order
            int sortPos = spinnerSort.getSelectedItemPosition();
            switch (sortPos) {
                case 0: sql.append(" ORDER BY id DESC"); break;
                case 1: sql.append(" ORDER BY id ASC"); break;
                case 2: sql.append(" ORDER BY packageName ASC, id DESC"); break;
                case 3: sql.append(" ORDER BY packageName DESC, id DESC"); break;
            }

            // Execute query
            SimpleSQLiteQuery query = new SimpleSQLiteQuery(sql.toString(), args.toArray());
            List<NotificationEntity> results = dao.searchWithFilters(query);

            runOnUiThread(() -> {
                currentResults.clear();
                currentResults.addAll(results);
                adapter.notifyDataSetChanged();
                txtResultsCount.setText(String.format(Locale.getDefault(), "%,d results", results.size()));
                updateFilterCount();
            });
        });
    }

    private void clearFilters() {
        editDateFrom.setText("");
        editDateTo.setText("");
        editTextSearch.setText("");
        spinnerApp.setSelection(0);
        spinnerCategory.setSelection(0);
        spinnerOngoing.setSelection(0);
        spinnerSort.setSelection(0);
        applyFilters();
    }

    private void updateFilterCount() {
        int count = 0;
        if (!editDateFrom.getText().toString().trim().isEmpty()) count++;
        if (!editDateTo.getText().toString().trim().isEmpty()) count++;
        if (spinnerApp.getSelectedItemPosition() > 0) count++;
        if (spinnerCategory.getSelectedItemPosition() > 0) count++;
        if (!editTextSearch.getText().toString().trim().isEmpty()) count++;
        if (spinnerOngoing.getSelectedItemPosition() > 0) count++;

        txtFilterCount.setText(count > 0 ? count + " active" : "none");
    }

    private void saveFilters() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("dateFrom", editDateFrom.getText().toString());
        editor.putString("dateTo", editDateTo.getText().toString());
        editor.putString("textSearch", editTextSearch.getText().toString());
        editor.putInt("appPos", spinnerApp.getSelectedItemPosition());
        editor.putInt("catPos", spinnerCategory.getSelectedItemPosition());
        editor.putInt("ongoingPos", spinnerOngoing.getSelectedItemPosition());
        editor.putInt("sortPos", spinnerSort.getSelectedItemPosition());
        editor.apply();
    }

    private void loadSavedFilters() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        editDateFrom.setText(prefs.getString("dateFrom", ""));
        editDateTo.setText(prefs.getString("dateTo", ""));
        editTextSearch.setText(prefs.getString("textSearch", ""));
        spinnerApp.setSelection(prefs.getInt("appPos", 0));
        spinnerCategory.setSelection(prefs.getInt("catPos", 0));
        spinnerOngoing.setSelection(prefs.getInt("ongoingPos", 0));
        spinnerSort.setSelection(prefs.getInt("sortPos", 0));
        updateFilterCount();
    }

    private void exportResults() {
        if (currentResults.isEmpty()) {
            Toast.makeText(this, "No results to export", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "research_" +
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new java.util.Date()) + ".csv");
        startActivityForResult(intent, EXPORT_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                executor.execute(() -> {
                    try {
                        OutputStream os = getContentResolver().openOutputStream(uri);
                        if (os != null) {
                            // Write CSV header
                            os.write("Timestamp,Package,App,Title,Text,IsOngoing,Category,ActionCount\n".getBytes());

                            // Write data
                            for (NotificationEntity n : currentResults) {
                                String line = String.format(Locale.US, "\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%d\"\n",
                                    n.getTimestamp(),
                                    n.getPackageName(),
                                    extractAppName(n.getPackageName()),
                                    n.getTitle() != null ? n.getTitle().replace("\"", "\"\"") : "",
                                    n.getText() != null ? n.getText().replace("\"", "\"\"") : "",
                                    n.isOngoing() ? "TRUE" : "FALSE",
                                    n.getCategory() != null ? n.getCategory() : "",
                                    n.getActionCount());
                                os.write(line.getBytes());
                            }
                            os.close();

                            runOnUiThread(() -> Toast.makeText(this,
                                "Exported " + currentResults.size() + " records", Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this,
                            "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
            }
        }
    }

    private String extractAppName(String packageName) {
        if (packageName == null) return "Unknown";
        String name = packageName;
        if (name.startsWith("com.")) name = name.substring(4);
        if (name.startsWith("android.")) name = name.substring(8);
        if (name.startsWith("google.android.")) name = name.substring(15);
        int dotIndex = name.indexOf('.');
        if (dotIndex > 0) name = name.substring(0, dotIndex);
        return name;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    // RecyclerView Adapter
    class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
        private List<NotificationEntity> items;

        NotificationAdapter(List<NotificationEntity> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            NotificationEntity item = items.get(position);

            // Time (HH:MM)
            String time = item.getTimestamp().length() >= 16 ?
                item.getTimestamp().substring(11, 16) : item.getTimestamp();
            holder.txtTime.setText(time);

            // App name
            holder.txtApp.setText(extractAppName(item.getPackageName()));

            // Category
            String category = item.getCategory();
            if (category != null && !category.isEmpty()) {
                holder.txtCategory.setText(category);
                holder.txtCategory.setVisibility(View.VISIBLE);
            } else {
                holder.txtCategory.setVisibility(View.GONE);
            }

            // Ongoing indicator
            holder.txtOngoing.setVisibility(item.isOngoing() ? View.VISIBLE : View.GONE);

            // Title
            String title = item.getTitle();
            if (title != null && !title.isEmpty()) {
                holder.txtTitle.setText(title);
                holder.txtTitle.setVisibility(View.VISIBLE);
            } else {
                holder.txtTitle.setText("No title");
                holder.txtTitle.setVisibility(View.VISIBLE);
            }

            // Text
            String text = item.getText();
            if (text != null && !text.isEmpty()) {
                holder.txtText.setText(text);
                holder.txtText.setVisibility(View.VISIBLE);
            } else {
                holder.txtText.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtTime, txtApp, txtCategory, txtOngoing, txtTitle, txtText;

            ViewHolder(View itemView) {
                super(itemView);
                txtTime = itemView.findViewById(R.id.txt_time);
                txtApp = itemView.findViewById(R.id.txt_app);
                txtCategory = itemView.findViewById(R.id.txt_category);
                txtOngoing = itemView.findViewById(R.id.txt_ongoing);
                txtTitle = itemView.findViewById(R.id.txt_title);
                txtText = itemView.findViewById(R.id.txt_text);
            }
        }
    }
}

package com.example.notme.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;
import java.util.Map;

@Dao
public interface NotificationDao {

    @Insert
    void insert(NotificationEntity notification);

    @Query("SELECT * FROM notifications ORDER BY id DESC")
    LiveData<List<NotificationEntity>> getAll();

    @Query("SELECT * FROM notifications ORDER BY id DESC")
    List<NotificationEntity> getAllSync();

    @Query("DELETE FROM notifications")
    void deleteAll();

    // Dashboard queries
    @Query("SELECT COUNT(*) FROM notifications")
    int getTotalCount();

    @Query("SELECT packageName, COUNT(*) as count FROM notifications GROUP BY packageName ORDER BY count DESC LIMIT 10")
    List<PackageCount> getTopPackages();

    @Query("SELECT category, COUNT(*) as count FROM notifications GROUP BY category ORDER BY count DESC")
    List<CategoryCount> getCategoryBreakdown();

    @Query("SELECT COUNT(*) FROM notifications WHERE isOngoing = 1")
    int getOngoingCount();

    @Query("SELECT COUNT(*) FROM notifications WHERE isOngoing = 0")
    int getRegularCount();

    @Query("SELECT strftime('%Y-%m-%d', timestamp) as date, COUNT(*) as count FROM notifications GROUP BY date ORDER BY date DESC LIMIT 7")
    List<DayCount> getLast7Days();

    @Query("SELECT strftime('%H', timestamp) as hour, COUNT(*) as count FROM notifications GROUP BY hour ORDER BY hour")
    List<HourCount> getHourlyDistribution();

    // Helper classes for query results
    class PackageCount {
        public String packageName;
        public int count;
    }

    class CategoryCount {
        public String category;
        public int count;
    }

    class DayCount {
        public String date;
        public int count;
    }

    class HourCount {
        public String hour;
        public int count;
    }
}

package com.example.notme.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NotificationDao {

    @Insert
    void insert(NotificationEntity notification);

    @Query("SELECT * FROM notifications ORDER BY id DESC")
    List<NotificationEntity> getAll();

    @Query("DELETE FROM notifications")
    void deleteAll();
}

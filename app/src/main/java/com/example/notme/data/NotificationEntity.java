package com.example.notme.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notifications")
public class NotificationEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String packageName;
    private String title;
    private String text;
    private String timestamp;

    public NotificationEntity(String packageName, String title, String text, String timestamp) {
        this.packageName = packageName;
        this.title = title;
        this.text = text;
        this.timestamp = timestamp;
    }

    // Getters and Setters (Required by Room)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}

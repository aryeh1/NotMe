package com.example.notme.data;

import android.content.Context;
import android.util.Log;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

import net.zetetic.database.sqlcipher.SupportFactory;

@Database(entities = {NotificationEntity.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String TAG = "AppDatabase";
    private static volatile AppDatabase INSTANCE;

    public abstract NotificationDao dao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    // Load SQLCipher native library
                    System.loadLibrary("sqlcipher");

                    // Get or generate encryption passphrase
                    byte[] passphrase = DatabaseEncryptionHelper.getOrCreatePassphrase(context);

                    // Perform migration from unencrypted to encrypted database if needed
                    boolean migrationSuccess = DatabaseMigrationHelper.migrateIfNeeded(context, passphrase);
                    if (!migrationSuccess) {
                        Log.e(TAG, "Database migration failed! This may result in data loss.");
                        // Continue anyway - the app should still work for new data
                    }

                    // Create SupportFactory with the passphrase for SQLCipher
                    SupportFactory factory = new SupportFactory(passphrase, null, false);

                    // Build Room database with encryption
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "notifications.db"
                    )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration()
                    .build();

                    Log.i(TAG, "Encrypted database initialized successfully");
                }
            }
        }
        return INSTANCE;
    }
}

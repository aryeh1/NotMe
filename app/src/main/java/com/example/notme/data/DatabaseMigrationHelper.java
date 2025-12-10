package com.example.notme.data;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteException;

import java.io.File;

/**
 * Helper class to migrate from unencrypted SQLite database to encrypted SQLCipher database.
 * This ensures existing users don't lose their notification history when encryption is enabled.
 */
public class DatabaseMigrationHelper {
    private static final String TAG = "DBMigrationHelper";
    private static final String DB_NAME = "notifications.db";
    private static final String OLD_DB_NAME = "notifications.db";
    private static final String MIGRATION_MARKER = "db_encrypted";

    /**
     * Checks if migration has already been completed.
     */
    public static boolean isMigrationComplete(Context context) {
        return context.getSharedPreferences("db_migration", Context.MODE_PRIVATE)
                .getBoolean(MIGRATION_MARKER, false);
    }

    /**
     * Marks migration as complete so it won't run again.
     */
    private static void markMigrationComplete(Context context) {
        context.getSharedPreferences("db_migration", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(MIGRATION_MARKER, true)
                .apply();
    }

    /**
     * Performs migration from unencrypted database to encrypted database if needed.
     * This method should be called before initializing the Room database.
     *
     * @param context Application context
     * @param passphrase Encryption passphrase for the new encrypted database
     * @return true if migration was performed or not needed, false if migration failed
     */
    public static boolean migrateIfNeeded(Context context, byte[] passphrase) {
        // Check if migration already completed
        if (isMigrationComplete(context)) {
            Log.i(TAG, "Migration already completed, skipping");
            return true;
        }

        File dbPath = context.getDatabasePath(DB_NAME);

        // If no database exists at all, this is a new installation
        if (!dbPath.exists()) {
            Log.i(TAG, "No existing database found, marking as new installation");
            markMigrationComplete(context);
            return true;
        }

        // Check if the existing database is already encrypted
        if (isAlreadyEncrypted(dbPath, passphrase)) {
            Log.i(TAG, "Database is already encrypted, skipping migration");
            markMigrationComplete(context);
            return true;
        }

        Log.i(TAG, "Found unencrypted database, starting migration");

        try {
            // Perform the migration
            boolean success = migrateDatabase(context, dbPath, passphrase);

            if (success) {
                markMigrationComplete(context);
                Log.i(TAG, "Migration completed successfully");
                return true;
            } else {
                Log.e(TAG, "Migration failed");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Migration failed with exception", e);
            return false;
        }
    }

    /**
     * Checks if the database at the given path is already encrypted.
     */
    private static boolean isAlreadyEncrypted(File dbPath, byte[] passphrase) {
        SQLiteDatabase db = null;
        try {
            // Try to open with the passphrase
            db = SQLiteDatabase.openDatabase(
                dbPath.getAbsolutePath(),
                passphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
                null,
                null
            );

            // Try to query a table to confirm it's readable
            Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' LIMIT 1", null);
            cursor.close();

            Log.i(TAG, "Database opened successfully with passphrase - already encrypted");
            return true;
        } catch (SQLiteException e) {
            Log.i(TAG, "Database could not be opened with passphrase - likely unencrypted");
            return false;
        } finally {
            if (db != null && db.isOpen()) {
                db.close();
            }
        }
    }

    /**
     * Performs the actual migration from unencrypted to encrypted database.
     */
    private static boolean migrateDatabase(Context context, File unencryptedDbPath, byte[] passphrase) {
        File tempDbPath = new File(context.getDatabasePath(DB_NAME + ".temp").getAbsolutePath());
        SQLiteDatabase unencryptedDb = null;
        SQLiteDatabase encryptedDb = null;

        try {
            // 1. Open the unencrypted database (standard Android SQLite)
            Log.i(TAG, "Opening unencrypted database");
            unencryptedDb = SQLiteDatabase.openDatabase(
                unencryptedDbPath.getAbsolutePath(),
                (byte[]) null,  // No passphrase for unencrypted
                null,
                SQLiteDatabase.OPEN_READONLY,
                null,
                null
            );

            // 2. Create new encrypted database at temp location
            Log.i(TAG, "Creating new encrypted database");
            if (tempDbPath.exists()) {
                tempDbPath.delete();
            }

            encryptedDb = SQLiteDatabase.openOrCreateDatabase(
                tempDbPath.getAbsolutePath(),
                passphrase,
                null,
                null,
                null
            );

            // 3. Get the table structure from old database
            Log.i(TAG, "Reading schema from unencrypted database");
            Cursor schemaCursor = unencryptedDb.rawQuery(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name='notifications'",
                null
            );

            if (!schemaCursor.moveToFirst()) {
                Log.e(TAG, "notifications table not found in unencrypted database");
                schemaCursor.close();
                return false;
            }

            String createTableSql = schemaCursor.getString(0);
            schemaCursor.close();

            // 4. Create table in encrypted database
            Log.i(TAG, "Creating table in encrypted database");
            encryptedDb.execSQL(createTableSql);

            // 5. Copy all data from unencrypted to encrypted database
            Log.i(TAG, "Copying data from unencrypted to encrypted database");
            Cursor dataCursor = unencryptedDb.rawQuery("SELECT * FROM notifications", null);

            int rowCount = 0;
            encryptedDb.beginTransaction();
            try {
                while (dataCursor.moveToNext()) {
                    // Build INSERT statement for each row
                    StringBuilder insertSql = new StringBuilder("INSERT INTO notifications VALUES (");

                    for (int i = 0; i < dataCursor.getColumnCount(); i++) {
                        if (i > 0) insertSql.append(", ");

                        if (dataCursor.isNull(i)) {
                            insertSql.append("NULL");
                        } else {
                            int type = dataCursor.getType(i);
                            if (type == Cursor.FIELD_TYPE_INTEGER) {
                                insertSql.append(dataCursor.getLong(i));
                            } else if (type == Cursor.FIELD_TYPE_FLOAT) {
                                insertSql.append(dataCursor.getDouble(i));
                            } else {
                                // String or blob - escape single quotes
                                String value = dataCursor.getString(i);
                                value = value.replace("'", "''");
                                insertSql.append("'").append(value).append("'");
                            }
                        }
                    }
                    insertSql.append(")");

                    encryptedDb.execSQL(insertSql.toString());
                    rowCount++;
                }

                encryptedDb.setTransactionSuccessful();
                Log.i(TAG, "Successfully copied " + rowCount + " rows");
            } finally {
                encryptedDb.endTransaction();
                dataCursor.close();
            }

            // 6. Close both databases
            unencryptedDb.close();
            encryptedDb.close();
            unencryptedDb = null;
            encryptedDb = null;

            // 7. Backup the old unencrypted database
            File backupPath = new File(unencryptedDbPath.getAbsolutePath() + ".backup");
            if (backupPath.exists()) {
                backupPath.delete();
            }
            if (!unencryptedDbPath.renameTo(backupPath)) {
                Log.e(TAG, "Failed to backup old database");
                return false;
            }
            Log.i(TAG, "Backed up old database to: " + backupPath.getAbsolutePath());

            // 8. Move encrypted database to final location
            if (!tempDbPath.renameTo(unencryptedDbPath)) {
                Log.e(TAG, "Failed to move encrypted database to final location");
                // Restore backup
                backupPath.renameTo(unencryptedDbPath);
                return false;
            }
            Log.i(TAG, "Moved encrypted database to final location");

            // 9. Delete the backup of unencrypted database for security
            if (backupPath.exists()) {
                if (backupPath.delete()) {
                    Log.i(TAG, "Deleted unencrypted database backup for security");
                } else {
                    Log.w(TAG, "Failed to delete unencrypted database backup: " + backupPath.getAbsolutePath());
                }
            }

            // 10. Clean up any associated files (-wal, -shm)
            cleanupAssociatedFiles(backupPath);

            Log.i(TAG, "Migration completed successfully - copied " + rowCount + " rows");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Migration failed", e);

            // Clean up on failure
            if (tempDbPath.exists()) {
                tempDbPath.delete();
            }

            return false;
        } finally {
            // Ensure databases are closed
            if (unencryptedDb != null && unencryptedDb.isOpen()) {
                unencryptedDb.close();
            }
            if (encryptedDb != null && encryptedDb.isOpen()) {
                encryptedDb.close();
            }
        }
    }

    /**
     * Cleans up WAL and SHM files associated with the database.
     */
    private static void cleanupAssociatedFiles(File dbPath) {
        String basePath = dbPath.getAbsolutePath();
        File walFile = new File(basePath + "-wal");
        File shmFile = new File(basePath + "-shm");

        if (walFile.exists()) {
            walFile.delete();
            Log.i(TAG, "Deleted WAL file");
        }
        if (shmFile.exists()) {
            shmFile.delete();
            Log.i(TAG, "Deleted SHM file");
        }
    }
}

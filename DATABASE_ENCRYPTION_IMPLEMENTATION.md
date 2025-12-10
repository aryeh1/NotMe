# Database Encryption Implementation - SQLCipher

## Overview
This document describes the implementation of database encryption using SQLCipher for the NotMe Android application. The implementation includes automatic migration from unencrypted to encrypted databases, ensuring no data loss for existing users.

## Components

### 1. DatabaseEncryptionHelper.java
**Location:** `app/src/main/java/com/example/notme/data/DatabaseEncryptionHelper.java`

**Purpose:** Manages the database encryption passphrase using Android Keystore for secure storage.

**Key Features:**
- Generates a cryptographically secure 256-bit random passphrase
- Stores the passphrase encrypted in SharedPreferences
- Uses Android Keystore with AES-256-GCM encryption
- Automatically retrieves or creates the passphrase on demand

**Security:**
- Passphrase is never stored in plain text
- Uses hardware-backed Android Keystore when available
- AES-256-GCM provides authenticated encryption

### 2. DatabaseMigrationHelper.java
**Location:** `app/src/main/java/com/example/notme/data/DatabaseMigrationHelper.java`

**Purpose:** Handles migration from unencrypted SQLite database to encrypted SQLCipher database.

**Key Features:**
- Detects if an unencrypted database exists
- Copies all data from unencrypted to encrypted database
- Verifies database is not already encrypted before migration
- Safely backs up and deletes the old unencrypted database
- Idempotent - migration only runs once

**Migration Process:**
1. Check if migration already completed (using SharedPreferences marker)
2. If no database exists, mark as new installation (no migration needed)
3. Check if existing database is already encrypted
4. If unencrypted:
   - Open unencrypted database (read-only)
   - Create new encrypted database at temporary location
   - Copy table schema
   - Copy all data row-by-row in a transaction
   - Backup old database
   - Replace old database with encrypted version
   - Delete unencrypted backup
   - Clean up WAL/SHM files
5. Mark migration as complete

**Data Safety:**
- Uses transactions to ensure data integrity
- Creates backup before replacing database
- Restores backup if migration fails
- Logs all operations for debugging

### 3. AppDatabase.java (Modified)
**Location:** `app/src/main/java/com/example/notme/data/AppDatabase.java`

**Changes:**
- Loads SQLCipher native library
- Retrieves encryption passphrase using DatabaseEncryptionHelper
- Runs migration check before initializing database
- Uses SupportFactory to enable SQLCipher encryption
- Logs encryption status

**Before:**
```java
INSTANCE = Room.databaseBuilder(
    context.getApplicationContext(),
    AppDatabase.class,
    "notifications.db"
)
.fallbackToDestructiveMigration()
.build();
```

**After:**
```java
// Load SQLCipher native library
System.loadLibrary("sqlcipher");

// Get or generate encryption passphrase
byte[] passphrase = DatabaseEncryptionHelper.getOrCreatePassphrase(context);

// Perform migration from unencrypted to encrypted database if needed
boolean migrationSuccess = DatabaseMigrationHelper.migrateIfNeeded(context, passphrase);

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
```

### 4. DataRepository.java (Modified)
**Location:** `app/src/main/java/com/example/notme/data/DataRepository.java`

**Added Method:** `exportDBFile()`

**Purpose:** Debug/verification tool to export the encrypted database file to Downloads folder.

**Features:**
- Checkpoints WAL to flush all data to main database file
- Supports Android 10+ (API 29+) using MediaStore API
- Supports Android 9 and below using direct file access
- Exports to `Downloads/notifications_encrypted.db`
- Provides user-friendly success message with instructions

**Verification:**
Users can:
1. Use the "Export DB (Debug)" menu option
2. Find the file in Downloads folder
3. Try to open with "DB Browser for SQLite"
4. The file should fail to open or request a password, proving encryption is active

### 5. MainActivity.java (Modified)
**Location:** `app/src/main/java/com/example/notme/MainActivity.java`

**Changes:**
- Added menu handler for "Export DB" option
- Shows confirmation dialog before export
- Displays export results to user

### 6. more_menu.xml (Modified)
**Location:** `app/src/main/res/menu/more_menu.xml`

**Changes:**
- Added new menu item: "💾 Export DB (Debug)"

### 7. build.gradle.kts (Modified)
**Location:** `app/build.gradle.kts`

**Dependencies Added:**
```kotlin
// SQLCipher for encrypted database
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
implementation("androidx.sqlite:sqlite-framework:2.4.0")
```

## Testing & Verification

### For Existing Users:
1. App detects unencrypted database on first launch
2. Migration runs automatically in background
3. All notification history is preserved
4. Old unencrypted database is deleted
5. App continues to work normally with encrypted database

### For New Users:
1. App creates encrypted database from the start
2. No migration needed
3. All data is encrypted by default

### Verification Steps:
1. Launch app and navigate to "More (⋮)" menu
2. Select "💾 Export DB (Debug)"
3. Confirm export in dialog
4. Check Downloads folder for `notifications_encrypted.db`
5. Try to open with "DB Browser for SQLite" or any SQLite tool
6. File should fail to open or request a password
7. This proves the database is encrypted

## Security Benefits

1. **Data at Rest Protection:** All notification data is encrypted on disk
2. **Hardware-Backed Security:** Uses Android Keystore for key management
3. **Transparent to Users:** No user action required
4. **No Data Loss:** Automatic migration preserves all existing data
5. **Forward Secrecy:** Old unencrypted database is deleted after migration

## Technical Details

- **Encryption Algorithm:** AES-256 (SQLCipher default)
- **Key Size:** 256 bits (32 bytes)
- **Key Storage:** Android Keystore with AES-256-GCM
- **Migration Strategy:** One-time automatic migration on first launch
- **Database Library:** Room + SQLCipher 4.5.4

## Compatibility

- **Minimum API Level:** 35 (Android 15)
- **Target API Level:** 36
- **SQLCipher Version:** 4.5.4
- **Room Version:** 2.6.1

## Performance Impact

- **Initial Migration:** One-time cost, proportional to database size
- **Runtime:** Minimal overhead (~5-15% compared to unencrypted SQLite)
- **Storage:** Negligible increase in database file size

## Troubleshooting

### If Migration Fails:
- Check logs for error messages (tag: "DBMigrationHelper")
- Migration will be attempted again on next app restart
- If persistent failures, user data remains in original unencrypted database
- App continues to function but with unencrypted database

### If Database Becomes Corrupted:
- User can clear app data to start fresh (loses notification history)
- Export functionality allows backup before clearing data

## Future Improvements

1. Add passphrase backup/recovery mechanism
2. Implement database integrity checks
3. Add option to re-key database with new passphrase
4. Provide user-visible encryption status indicator
5. Add encrypted backup/restore functionality

## References

- [SQLCipher for Android](https://www.zetetic.net/sqlcipher/sqlcipher-for-android/)
- [Android Keystore System](https://developer.android.com/training/articles/keystore)
- [Room Database](https://developer.android.com/training/data-storage/room)

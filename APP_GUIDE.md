# NotMe - Notification Logger App

## What This App Does

**NotMe** is an Android app that captures ALL notifications from your phone and displays them in a log. When any app sends a notification (messages, emails, social media, etc.), this app records it with timestamp, app name, title, and text.

**Why it's useful:**
- See notification history - Android doesn't keep all notifications
- Track what apps are spamming you
- Review notifications you dismissed too quickly
- Learn how Android notifications work

---

## How It Works (Architecture)

The app has **TWO separate parts** that work together:

### 1. NotificationService (Background Worker)
**File:** `/app/src/main/java/com/example/notme/NotificationService.java`

**What it does:**
- Runs in the background 24/7 (even when app is closed)
- Listens for ALL notifications on your phone
- Writes each notification to a file: `notifications.txt`
- Android starts this service automatically when you grant permission

**Important:** This service is ALWAYS running once you enable notification access. It doesn't matter if the app is open or closed.

### 2. MainActivity (The UI You See)
**File:** `/app/src/main/java/com/example/notme/MainActivity.java`

**What it does:**
- Shows the app screen with buttons and notification log
- Reads from `notifications.txt` every 2 seconds
- Displays the contents in a scrollable text view
- Only reads when the app is VISIBLE (saves battery)

---

## The File-Based System

**How they communicate:**

```
NotificationService --> writes to --> notifications.txt
                                           ^
                                           |
MainActivity -------- reads every 2s ------+
```

**Why files instead of other methods:**
- Broadcasts were failing (Android restrictions)
- Files persist - you can close the app and reopen it later, history is still there
- Simple and reliable
- You can even manually read the file if needed

**File location:** `/data/data/com.example.notme/files/notifications.txt`
(Internal storage - only your app can access it)

---

## Important Files & Code

### CRITICAL - Don't Touch Unless You Know What You're Doing:

1. **AndroidManifest.xml** - `/app/src/main/AndroidManifest.xml`
   - Declares NotificationService with special permission
   - Without this, Android won't let the service run
   - Lines 14-20: Service declaration

2. **NotificationService.java** - The notification listener
   - Line 26: `onNotificationPosted()` - captures every notification
   - Line 54: `writeToFile()` - writes to notifications.txt
   - Line 56: `getFilesDir()` - where the file lives

3. **MainActivity.java** - The UI
   - Line 63: Handler setup - creates the file reader
   - Line 159: `readNotificationFile()` - reads and displays notifications
   - Line 193: `onResume()` - starts file reading when app opens
   - Line 204: `onPause()` - stops reading when app closes

### Safe to Change - UI/Cosmetic:

1. **activity_main.xml** - `/app/src/main/res/layout/activity_main.xml`
   - Button text, colors, sizes
   - Layout spacing and padding
   - Change anything here without breaking functionality

2. **Text/Messages:**
   - MainActivity.java: Toast messages, button text
   - Change wording, add emojis, etc.

3. **Read interval:**
   - MainActivity.java line 29: `READ_INTERVAL = 2000` (2 seconds)
   - You can change to 1000 (1 second) or 5000 (5 seconds)
   - Lower = more responsive but uses more battery

---

## What's Just POC (Proof of Concept)

### These Work But Are Basic:

1. **Test Button** - Just adds fake data to the screen
   - Doesn't write to file
   - Only for testing UI
   - Can be removed or improved

2. **File Reading Method** - Works but inefficient
   - Currently reads ENTIRE file every 2 seconds
   - If you have 1000 notifications, it re-reads all 1000 each time
   - **Better approach:** Only read new lines since last read
   - **Better approach:** Use file watchers instead of polling

3. **Clear Button** - Deletes entire file
   - No confirmation dialog
   - No undo
   - **Better approach:** Archive old notifications instead of deleting

4. **No Search/Filter** - You see ALL notifications
   - Can't search by app
   - Can't filter by date
   - Can't hide certain apps
   - **Future:** Add search bar and filters

5. **Permission Check** - Works but manual
   - User has to click "Settings" button
   - **Better approach:** Auto-open settings if permission denied

---

## The Intent (Why This Exists)

**Original problem:** You wanted to log notifications to track what's happening on your phone.

**Design decisions made:**
- **File-based** - Reliable, persistent, simple
- **Background service** - Captures everything even when app closed
- **Minimal UI** - Just works, no fancy features yet
- **Raw text display** - See everything unfiltered

**Future possibilities:**
- Database instead of text file (SQLite)
- Search and filter notifications
- Statistics (which app spams most?)
- Export notifications to share
- Dark mode
- Notification categories
- Delete individual notifications
- Set notification sound when certain apps notify you

---

## How to Use (Step by Step)

1. **Install & Open App**
   - App shows "Permission DENIED" in red

2. **Enable Permission**
   - Tap "Settings" button
   - Find "NotMe" in the list
   - Toggle it ON
   - Press back to return to app

3. **Verify It's Working**
   - Tap "Check" - should show green "Permission GRANTED"
   - Tap "Test" - fake notification appears
   - Send yourself a real notification (text message, etc.)
   - Within 2 seconds, it should appear in the log

4. **Leave It Running**
   - Close the app (swipe away or press home)
   - NotificationService keeps running in background
   - Next time you open the app, all captured notifications will be there

5. **Clear History**
   - Tap "Clear" button to delete all logged notifications

---

## Common Issues & Debugging

### Problem: No notifications appearing

**Check 1:** Permission enabled?
- Open app, tap "Check" button
- Should say "Permission GRANTED" in green
- If not, tap "Settings" and enable it

**Check 2:** Is service running?
- Connect phone to computer
- In Android Studio: View → Tool Windows → Logcat
- Filter: "NotMe"
- When notification arrives, you should see: `onNotificationPosted: Notification received!`
- If you see this, service is working

**Check 3:** Is file being written?
- In Logcat, look for: `writeToFile: SUCCESS`
- This means notifications are being saved

**Check 4:** Is MainActivity reading the file?
- Open the app
- In Logcat, look for: `readNotificationFile: Updated UI with X characters`
- If you see this every 2 seconds, file reading works

### Problem: App crashes when opening

**Most likely:** Permission or layout issue
- Check Logcat for red error messages
- Look for "ClassCastException" or "NullPointerException"

### Problem: Old notifications missing after reinstall

**Expected behavior:** Uninstalling the app deletes the file
- The file is in the app's private storage
- Reinstalling = fresh start
- If you want to keep history, need to add backup/export feature

---

## File Structure Overview

```
/android/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/notme/
│   │   │   │   ├── MainActivity.java          ← UI and file reading
│   │   │   │   └── NotificationService.java   ← Notification capture
│   │   │   ├── res/
│   │   │   │   └── layout/
│   │   │   │       └── activity_main.xml      ← UI layout/design
│   │   │   └── AndroidManifest.xml            ← App configuration
│   ├── build.gradle                            ← Dependencies
│   └── ...
└── APP_GUIDE.md                                ← This file!
```

---

## Key Android Concepts You're Using

### 1. NotificationListenerService
- Special Android service that intercepts notifications
- Requires explicit user permission (security feature)
- Runs independently of your app's UI
- System manages its lifecycle

### 2. Activity Lifecycle
- `onCreate()` - App starts, initialize everything
- `onResume()` - App becomes visible, start file reading
- `onPause()` - App goes to background, stop file reading
- `onDestroy()` - App is killed, cleanup

### 3. Handler & Runnable
- Way to schedule repeated tasks
- `Handler.post()` - run task
- `Handler.postDelayed()` - run task after delay
- `Handler.removeCallbacks()` - stop task

### 4. File I/O
- `getFilesDir()` - app's private file directory
- `FileWriter` - write to file
- `BufferedReader` / `FileReader` - read from file

---

## Next Steps (When You Come Back)

### Phase 1: Understanding (First week)
- Use the app normally for a week
- See what notifications you get
- Note what's annoying or missing

### Phase 2: Small Improvements (Learning)
- Change button colors/text
- Modify the layout spacing
- Add a counter showing total notifications
- Add timestamp to "Test" notifications

### Phase 3: New Features (Intermediate)
- Add search functionality
- Filter by app name
- Show only notifications from last 24 hours
- Add export to text file feature

### Phase 4: Advanced (If you get serious)
- Replace text file with SQLite database
- Add statistics/charts
- Notification categories
- Share notifications to other apps
- Backup/restore feature

---

## Important Notes

### Battery Usage
- NotificationService is lightweight, minimal battery impact
- MainActivity only reads when app is VISIBLE
- If battery drains, it's probably other apps, not this

### Privacy
- All notifications stored locally on your phone
- File is in private storage - other apps can't access it
- No internet connection, no data sent anywhere
- Uninstall the app = file is deleted

### Permissions
- Only needs Notification Access permission
- No location, camera, contacts, etc.
- Can revoke permission anytime in Settings

### Android Version
- Works on Android 4.3+ (API 18+)
- Tested on your phone's Android version
- May need adjustments for very old/new Android versions

---

## Glossary (Terms You'll See)

- **Activity** - A screen in your app (MainActivity = main screen)
- **Service** - Background task that runs without UI
- **Intent** - Message to launch activities or send data
- **Handler** - Tool for scheduling tasks on the main thread
- **Runnable** - A task that can be run
- **TextView** - UI element that displays text
- **Button** - UI element you can tap
- **Toast** - Small popup message at bottom of screen
- **Logcat** - Android's debug log viewer
- **APK** - Android Package - the installable app file
- **Gradle** - Build system that compiles your code

---

## Contact/Resources

- **Android Documentation:** https://developer.android.com
- **Stack Overflow:** Search Android questions
- **Your code:** `/mnt/c/repos/android/`
- **This guide:** `/mnt/c/repos/android/APP_GUIDE.md`

---

## Version History

**v1.0 - Current (File-based POC)**
- Basic notification capture
- File-based storage
- Simple UI with 4 buttons
- 2-second refresh rate
- Text-only display

**Future versions:**
- v2.0: Database + Search
- v3.0: Statistics + Filters
- v4.0: Advanced features

---

**Last updated:** 2025-12-07
**Author:** You (with AI assistance)
**Status:** Working POC - Ready for testing

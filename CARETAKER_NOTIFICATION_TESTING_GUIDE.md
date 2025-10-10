# Testing Guide: Caretaker Notification System

## Overview

This guide will help you test the cross-device caretaker notification system that alerts caregivers when patients don't complete their medication or task reminders.

## Prerequisites

### 1. Build Both Apps

```bash
# Build Patient App (AlzheimersCaregiver)
cd "c:\Users\mihir\OneDrive\Desktop\temp\AlzheimersCaregiver"
./gradlew :app:assembleDebug

# Build CaretakerApp
cd "c:\Users\mihir\OneDrive\Desktop\temp\AlzheimersCaregiver\CaretakerApp"
./gradlew :app:assembleDebug
```

### 2. Install on Two Devices

- **Patient Device**: Install the main AlzheimersCaregiver app
- **Caretaker Device**: Install the CaretakerApp

## Test Scenarios

### Test 1: Basic Incomplete Reminder Alert

#### Step 1: Setup

1. **Patient Device**: Open AlzheimersCaregiver app
2. **Patient Device**: Create a new medication reminder:
   - Title: "Take morning medication"
   - Time: Current time + 3 minutes
   - Check "Repeat daily" checkbox
   - Save the reminder

#### Step 2: Let Reminder Trigger

1. Wait for the reminder time to arrive
2. **Patient Device**: Alarm should trigger and show notification
3. **Important**: Do NOT mark the reminder as completed
4. Dismiss the alarm notification without completing

#### Step 3: Wait for Caretaker Alert

1. Wait **15 minutes** after the reminder time
2. **CaretakerApp Device**: Check for new notification or open app
3. **Expected Result**: Caretaker should receive alert about incomplete reminder

#### Step 4: Verify in Firestore (Optional)

1. Open Firebase Console → Firestore Database
2. Look for collection: `incomplete_reminder_alerts`
3. Should see document with:
   - `reminderTitle`: "Take morning medication"
   - `delayMinutes`: 15
   - `status`: "pending"
   - Patient ID and timestamp

### Test 2: Multiple Delay Levels

#### Setup

1. Create reminder for current time + 2 minutes
2. Let it trigger without completing

#### Timeline Testing

- **After 15 minutes**: First alert should be created
- **After 1 hour**: Second alert should be created
- **After 3 hours**: Final alert should be created

#### Verification

Check Firestore for multiple documents:

- `[reminderID]_alert_15min`
- `[reminderID]_alert_60min`
- `[reminderID]_alert_180min`

### Test 3: Late Completion (Resolving Alerts)

#### Step 1: Create Incomplete Reminder

1. Create reminder and let it trigger without completion
2. Wait 20 minutes → Verify caretaker alert is created

#### Step 2: Complete Late

1. **Patient Device**: Mark the reminder as completed
2. **Expected Result**:
   - Existing alert should be marked as "resolved"
   - No further alerts should be created

#### Step 3: Verify Resolution

1. Check Firestore: existing alert should have:
   - `status`: "resolved"
   - `resolvedTime`: timestamp when completed

### Test 4: On-Time Completion (No Alerts)

#### Setup

1. Create reminder for current time + 2 minutes
2. When alarm triggers, **immediately** mark as completed

#### Expected Result

- **After 15 minutes**: NO caretaker alert should be created
- Firestore should have no `incomplete_reminder_alerts` documents for this reminder

### Test 5: Checkbox Completion Fix

#### Test Completion UI

1. Create a daily repeating reminder
2. Mark it as completed using the checkbox
3. **Expected Result**: Checkbox should stay checked and show "✓ Completed"
4. **Issue Fixed**: Checkbox should not immediately uncheck itself

## Debugging and Troubleshooting

### Log Monitoring

Monitor these log tags for debugging:

```bash
# Patient Device Logs
adb logcat -s CaretakerNotificationScheduler
adb logcat -s CaretakerNotificationReceiver
adb logcat -s ReminderRepository

# Look for these log messages:
# "Scheduled caretaker notification checks for reminder: [title]"
# "Checking reminder completion for caretaker notification"
# "Creating caretaker alert for incomplete reminder: [title]"
```

### Common Issues and Solutions

#### Issue 1: No Caretaker Alerts Created

**Possible Causes**:

- AlarmManager permissions not granted
- Battery optimization blocking background alarms
- App not authenticated with Firebase

**Solutions**:

1. Check battery optimization settings
2. Grant "Schedule exact alarms" permission
3. Verify Firebase authentication is working

#### Issue 2: CaretakerApp Not Receiving Alerts

**Possible Causes**:

- CaretakerApp not monitoring Firestore properly
- Different Firebase project configurations
- Network connectivity issues

**Solutions**:

1. Verify both apps use same Firebase project
2. Check internet connectivity
3. Implement Firestore listener in CaretakerApp (see next section)

#### Issue 3: Multiple Alerts for Same Reminder

**Possible Causes**:

- Completion logic not working properly
- Alarm cancellation failing

**Solutions**:

1. Check logs for "Resolved incomplete reminder alert" messages
2. Verify reminder completion is being detected

## CaretakerApp Integration

### Required Code for CaretakerApp

The CaretakerApp needs to implement a Firestore listener to receive alerts:

```java
// Add this to CaretakerApp MainActivity or a background service
private void listenForIncompleteReminders() {
    FirebaseFirestore.getInstance()
        .collection("incomplete_reminder_alerts")
        .whereEqualTo("status", "pending")
        .addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w("CaretakerApp", "Listen failed.", e);
                return;
            }

            for (DocumentChange change : snapshots.getDocumentChanges()) {
                if (change.getType() == DocumentChange.Type.ADDED) {
                    // New incomplete reminder alert
                    Map<String, Object> alert = change.getDocument().getData();

                    String patientId = (String) alert.get("patientId");
                    String reminderTitle = (String) alert.get("reminderTitle");
                    String reminderType = (String) alert.get("reminderType");
                    int delayMinutes = ((Long) alert.get("delayMinutes")).intValue();

                    // Show notification to caretaker
                    showCaretakerNotification(
                        "Patient Reminder Missed",
                        "Patient hasn't completed: " + reminderTitle +
                        " (overdue by " + delayMinutes + " minutes)"
                    );
                }
            }
        });
}

private void showCaretakerNotification(String title, String message) {
    // Implement notification logic for caretaker
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "alerts")
        .setSmallIcon(R.drawable.ic_alert)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true);

    NotificationManagerCompat.from(this).notify(1, builder.build());
}
```

## Success Criteria

### ✅ Test 1: Basic Alert Creation

- [ ] Incomplete reminder creates Firestore alert after 15 minutes
- [ ] CaretakerApp receives notification about missed reminder
- [ ] Alert contains correct patient, reminder, and delay information

### ✅ Test 2: Multiple Delays

- [ ] First alert at 15 minutes
- [ ] Second alert at 1 hour
- [ ] Final alert at 3 hours
- [ ] Each alert has different `delayMinutes` value

### ✅ Test 3: Alert Resolution

- [ ] Late completion marks alerts as "resolved"
- [ ] No further alerts created after completion
- [ ] CaretakerApp stops showing notifications for resolved alerts

### ✅ Test 4: No False Alerts

- [ ] On-time completion prevents alert creation
- [ ] Completed reminders don't generate alerts

### ✅ Test 5: UI Fix Verification

- [ ] Completion checkboxes stay checked for repeating reminders
- [ ] Status displays correctly ("✓ Completed" vs "Scheduled")

## Advanced Testing

### Performance Testing

1. Create 10+ reminders and let them all go incomplete
2. Verify system handles multiple alerts efficiently
3. Check battery usage impact

### Edge Case Testing

1. **App Restart**: Create reminder, close app, reopen after delay
2. **Network Loss**: Create reminder, disconnect internet, reconnect after delay
3. **Time Zone Changes**: Create reminder, change device time zone
4. **Battery Optimization**: Enable aggressive battery optimization and test

## Next Steps

Once basic testing is complete, consider implementing:

1. Push notifications to CaretakerApp instead of just Firestore
2. Configurable delay times in CaretakerApp settings
3. Patient-specific alert preferences
4. Alert acknowledgment and dismissal by caretakers

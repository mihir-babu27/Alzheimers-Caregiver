# Daily Repeating Alarm System Implementation

## Overview

The alarm system has been enhanced to support daily repeating alarms with automatic rescheduling, robust reboot handling, and comprehensive maintenance. This ensures medication reminders and daily tasks repeat reliably without user intervention.

## Key Features

### 1. Daily Repeating Alarms

- **Automatic Rescheduling**: When a repeating alarm triggers, it automatically schedules for the same time next day
- **Past Time Handling**: If an alarm time is in the past, repeating alarms are scheduled for the next occurrence
- **SharedPreferences Storage**: Repeating alarm metadata is persistently stored for reboot recovery

### 2. Midnight Reset System

- **WorkManager Job**: Runs daily at 00:00 to refresh all alarms from Firestore
- **Timezone Handling**: Automatically adjusts for timezone changes
- **Data Sync**: Ensures local alarms stay synchronized with server data

### 3. Enhanced Boot Recovery

- **Comprehensive Restoration**: Restores both regular and repeating alarms after device restart
- **Multiple Recovery Paths**: Uses both SharedPreferences metadata and Firestore sync
- **Service Scheduling**: Automatically reschedules all background maintenance jobs

### 4. Advanced Logging

- **Centralized Logging**: AlarmLogger utility provides detailed, timestamped logs
- **Multiple Log Categories**: Separate logs for scheduling, triggering, rescheduling, boot restoration
- **Debug Support**: Comprehensive logging for troubleshooting alarm issues

## Implementation Details

### AlarmScheduler Enhancements

```java
// Schedule a repeating alarm
AlarmScheduler scheduler = new AlarmScheduler(context);
boolean success = scheduler.scheduleAlarm(reminder, true); // true = repeating

// Schedule with basic parameters
boolean success = scheduler.scheduleAlarm(
    "reminder_id",
    "Take Medicine",
    "Time for your morning medication",
    timeMillis,
    true  // repeating
);

// Check if alarm is set to repeat
boolean isRepeating = scheduler.isRepeatingAlarm("reminder_id");

// Manually reschedule a repeating alarm
boolean success = scheduler.rescheduleRepeatingAlarm(
    "reminder_id",
    "Take Medicine",
    "Time for your morning medication",
    "medication"
);
```

### AlarmReceiver Enhancements

The AlarmReceiver now automatically handles rescheduling:

```java
// When alarm triggers, it automatically:
// 1. Shows the alarm notification/activity
// 2. Checks if it's a repeating alarm
// 3. If repeating, schedules for next day
// 4. Logs all activities
```

### Midnight Reset Worker

Automatically runs daily at midnight:

```java
// Scheduled in MainActivity and BootReceiver
MidnightAlarmResetScheduler.scheduleMidnightReset(context);

// The worker performs:
// 1. Clears alarm tracker
// 2. Reschedules from Firestore
// 3. Validates repeating alarms
// 4. Logs maintenance activities
```

### Boot Recovery System

Enhanced BootReceiver provides comprehensive restoration:

```java
// Automatically triggered on boot:
// 1. Restores repeating alarm metadata
// 2. Reschedules all alarms from Firestore
// 3. Schedules midnight reset job
// 4. Schedules periodic sync job
// 5. Logs all restoration activities
```

## Usage Examples

### Setting Up Daily Medication Reminders

```java
// Create a daily medication reminder
ReminderEntity reminder = new ReminderEntity(
    "Morning Medicine",
    "Take your morning pills with water",
    System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1), // 1 hour from now
    "medication",
    patientId
);

// Schedule as repeating alarm
AlarmScheduler scheduler = new AlarmScheduler(context);
boolean success = scheduler.scheduleAlarm(reminder, true);

if (success) {
    Log.d("App", "Daily medication reminder set successfully");
} else {
    Log.e("App", "Failed to set daily medication reminder");
}
```

### Setting Up Daily Task Reminders

```java
// Schedule daily exercise reminder
boolean success = scheduler.scheduleAlarm(
    "daily_exercise",
    "Exercise Time",
    "Time for your daily 15-minute walk",
    getTimeForTomorrow9AM(),
    true  // repeating daily
);
```

### Checking Alarm Status

```java
// Check if alarm is scheduled
boolean isScheduled = scheduler.isAlarmScheduled("reminder_id");

// Check if alarm repeats daily
boolean isRepeating = scheduler.isRepeatingAlarm("reminder_id");

// Get original schedule time for repeating alarm
long originalTime = scheduler.getOriginalTime("reminder_id");
```

## Maintenance and Monitoring

### Logging and Debugging

The system provides comprehensive logging through AlarmLogger:

```java
// Log custom events
AlarmLogger.logDebug("MyComponent", "Custom debug message");

// Log errors
AlarmLogger.logError("MyComponent", "Operation", "Error details", exception);

// View logs in Android Studio Logcat:
// Filter by "AlarmSystem" to see all alarm-related logs
```

### Manual Maintenance

```java
// Manually trigger midnight reset (for testing)
MidnightAlarmResetWorker worker = new MidnightAlarmResetWorker(context, params);
Worker.Result result = worker.doWork();

// Clear and reschedule all alarms
AlarmScheduler scheduler = new AlarmScheduler(context);
scheduler.clearAlarmTracker();
ReminderRepository repository = new ReminderRepository(scheduler);
repository.rescheduleAllAlarms();
```

## Configuration

### Permissions Required

Already configured in AndroidManifest.xml:

- `SCHEDULE_EXACT_ALARM` - For precise alarm timing
- `USE_EXACT_ALARM` - Alternative exact alarm permission
- `RECEIVE_BOOT_COMPLETED` - For boot restoration
- `WAKE_LOCK` - To wake device for alarms
- `POST_NOTIFICATIONS` - For alarm notifications

### Background Job Scheduling

The system automatically schedules these background jobs:

- **Midnight Reset**: Daily at 00:00 via WorkManager
- **Periodic Sync**: Every 15 minutes via WorkManager (existing)
- **Boot Restoration**: On device restart via BroadcastReceiver

## Troubleshooting

### Common Issues

1. **Alarms not repeating**

   - Check if `isRepeating` parameter was set to `true`
   - Verify SharedPreferences contains repeating metadata
   - Check AlarmLogger logs for rescheduling attempts

2. **Alarms lost after reboot**

   - Verify RECEIVE_BOOT_COMPLETED permission
   - Check BootReceiver logs for restoration attempts
   - Ensure Firestore contains alarm data

3. **Midnight reset not working**
   - Check WorkManager status in device settings
   - Verify device isn't in battery optimization mode
   - Check AlarmLogger logs for midnight reset attempts

### Debug Commands

```java
// Log current system status
AlarmLogger.logSessionSummary(totalAlarms, repeatingAlarms, activeAlarms);

// Check permissions
boolean canSchedule = alarmManager.canScheduleExactAlarms();
AlarmLogger.logPermissions(canSchedule, hasNotificationPerm, Build.VERSION.SDK_INT);

// Force reschedule all alarms
repository.rescheduleAllAlarms();
```

## Performance Impact

- **Memory**: Minimal additional SharedPreferences storage for repeating alarm metadata
- **Battery**: WorkManager midnight job runs briefly once daily
- **Network**: No additional network calls beyond existing Firestore sync
- **Storage**: Small increase in log output for debugging

## Future Enhancements

Planned improvements:

1. **Custom Repeat Intervals**: Support for weekly, monthly schedules
2. **Smart Rescheduling**: Skip weekends for work-related reminders
3. **Snooze Support**: Allow users to snooze repeating alarms
4. **Analytics Dashboard**: View alarm reliability statistics
5. **Backup/Restore**: Export/import alarm configurations

## Testing

To test the daily repeating alarm system:

1. **Schedule Test Alarm**: Use the hidden debug feature (long-press user name in MainActivity)
2. **Verify Rescheduling**: Check logs after alarm triggers
3. **Test Boot Recovery**: Restart device and verify alarms restored
4. **Test Midnight Reset**: Manually advance system time to midnight
5. **Check Logs**: Monitor AlarmLogger output for all operations

The system is designed to be robust and self-healing, automatically recovering from various failure scenarios while providing detailed logging for troubleshooting.

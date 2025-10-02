# AlarmManager Reminder System Guide

## Overview

This document provides comprehensive information about the AlarmManager-based reminder system implemented in the Alzheimer's Caregiver application. The system uses local alarms for reliable reminders that work even when the app is closed or the device is rebooted.

## Architecture Components

1. **ReminderEntity**: Data model for reminders stored in Firestore
2. **ReminderRepository**: MVVM repository for CRUD operations, Firestore sync, and alarm scheduling
3. **AlarmScheduler**: Utility for scheduling and managing alarms with AlarmManager
4. **AlarmReceiver**: BroadcastReceiver that shows notifications when alarms trigger
5. **BootReceiver**: BroadcastReceiver that reschedules alarms after device reboot

## Required Permissions

The following permissions are required in the AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

## Permission Handling

For Android 12+ (API level 31+), you need to request SCHEDULE_EXACT_ALARM permission at runtime:

```java
// Check if permission is needed (Android 12+)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    if (!alarmManager.canScheduleExactAlarms()) {
        // Request permission
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        startActivity(intent);
        return;
    }
}
```

For Android 13+ (API level 33+), you need to request POST_NOTIFICATIONS permission:

```java
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this,
            new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
            REQUEST_NOTIFICATION_PERMISSION);
        return;
    }
}
```

## Debugging Tips

1. **Use the test alarm feature**: Call `alarmScheduler.scheduleTestAlarm()` to create a test alarm that triggers in 10 seconds

2. **Check permission status**:

   ```java
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
       AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
       Log.d(TAG, "Can schedule exact alarms: " + alarmManager.canScheduleExactAlarms());
   }
   ```

3. **Enhanced logging**: The system includes detailed logging at each step for easier troubleshooting

4. **Verify alarm scheduling**: Check `scheduledAlarms` in `AlarmScheduler` to see which alarms are currently scheduled

## Common Issues and Solutions

1. **Alarms not triggering**:

   - Check if exact alarm permission is granted
   - Verify device isn't in battery optimization mode
   - Ensure the device time is correct

2. **Missing notifications**:

   - Check if notification permission is granted
   - Verify notification channel is created with proper importance
   - Check system notification settings for the app

3. **Alarms not persisting through reboots**:

   - Verify `BootReceiver` is properly registered in the manifest
   - Check if `RECEIVE_BOOT_COMPLETED` permission is granted
   - Test with `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED`

4. **Inconsistent behavior across devices**:
   - Different manufacturers may have custom battery optimization settings
   - Some devices may restrict background services more aggressively
   - Consider providing instructions for disabling battery optimization

## Best Practices

1. **Use exact alarms judiciously**: They consume more battery power

2. **Provide clear UI feedback**: Show the user when alarms are scheduled/canceled

3. **Implement redundancy**: Use both local alarms and Firebase Cloud Messaging for critical reminders

4. **Regular testing**: Test alarm functionality regularly, especially after OS updates

5. **Error handling**: Implement robust error handling and provide user-friendly error messages

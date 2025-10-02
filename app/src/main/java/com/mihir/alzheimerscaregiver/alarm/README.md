# Alarm-Based Reminder System

## Overview

The Alzheimer's Caregiver application includes a robust reminder system that uses Android's AlarmManager to schedule local notifications. This system ensures that reminders are delivered on time, even if the app is closed or the device is in doze mode.

## Features

- **Local Alarms**: Uses `AlarmManager.setExactAndAllowWhileIdle()` to ensure timely delivery even in battery optimization mode
- **Persistent Reminders**: Alarms persist even after app closure
- **Boot Recovery**: All alarms are automatically rescheduled after device reboot
- **Firestore Integration**: Reminders are synced with Firestore for cross-device access
- **Material 3 UI**: Modern interface following Material Design 3 guidelines

## Implementation Details

### Core Components:

1. **ReminderEntity**: Data model for reminders stored in Firestore
2. **ReminderRepository**: MVVM repository pattern for CRUD operations and Firestore sync
3. **AlarmScheduler**: Utility for scheduling and canceling alarms with AlarmManager
4. **AlarmReceiver**: BroadcastReceiver for handling alarm events and showing notifications
5. **BootReceiver**: BroadcastReceiver for rescheduling alarms after device reboot
6. **ReminderViewModel**: ViewModel for UI operations and business logic

### Required Permissions:

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### Key Considerations:

1. **Android 12+ Compatibility**: The system requests appropriate permissions for exact alarm scheduling
2. **Battery Optimization**: Uses the recommended APIs for bypassing Doze mode when necessary
3. **Error Handling**: Robust error handling with user feedback
4. **MVVM Architecture**: Clean separation of concerns

## Usage

### Scheduling a Reminder:

```java
// Via the Repository
ReminderEntity entity = new ReminderEntity(title, description, scheduledTimeMillis, false);
reminderRepository.insert(entity);

// Or via the AlarmScheduler directly
AlarmScheduler scheduler = new AlarmScheduler(context);
scheduler.scheduleAlarm(reminderId, title, description, scheduledTimeMillis);
```

### Canceling a Reminder:

```java
// Via the Repository
reminderRepository.delete(entity);

// Or via the AlarmScheduler directly
AlarmScheduler scheduler = new AlarmScheduler(context);
scheduler.cancelAlarm(reminderId);
```

## Testing

The reminder system has been tested for:

- Timely notification delivery
- Persistence across app restarts
- Rescheduling after device reboot
- Various Android versions (API 24+)
- Battery optimization scenarios

## Future Improvements

- Add support for recurring reminders
- Implement reminder snoozing
- Add custom sounds for different reminder types
- Group related reminders

# Medication Alarm System Documentation

## Overview

The Alzheimer's Caregiver app includes a robust medication reminder system that functions like a traditional alarm clock. Unlike standard push notifications that can be easily missed, this alarm system:

1. **Wakes the device** - Works even when the screen is off or device is locked
2. **Shows a full-screen alarm** - Clear, large display with current time and medication details
3. **Plays continuous alarm sound** - Uses device's default alarm sound at full volume
4. **Vibrates continuously** - Provides additional sensory alert
5. **Requires explicit interaction** - Must be dismissed or snoozed (can't be swiped away)
6. **Works after device reboot** - Alarms are automatically restored when device restarts

## Technical Architecture

The alarm system consists of several key components:

### 1. AlarmScheduler

Responsible for scheduling alarms with Android's AlarmManager system service:

- Schedules exact alarms using `setExactAndAllowWhileIdle()` for reliable timing
- Generates unique PendingIntent for each reminder
- Passes reminder details (ID, title, message) to AlarmReceiver via Intent

### 2. AlarmReceiver

A BroadcastReceiver that triggers when an alarm time is reached:

- For medication reminders, launches MedicationAlarmActivity for a true alarm experience
- Also creates a high-priority notification as a backup
- Uses notification channels to ensure proper notification priority

### 3. MedicationAlarmActivity

A full-screen activity that provides the alarm clock experience:

- Displays current time in large digits
- Shows medication name and instructions
- Plays continuous alarm sound using MediaPlayer
- Implements continuous vibration pattern
- Provides Dismiss and Snooze (5 min) buttons
- Acquires wake lock to keep screen on
- Sets proper window flags to appear over lock screen

### 4. BootReceiver

Handles device reboots to restore scheduled alarms:

- Listens for BOOT_COMPLETED broadcast
- Reads all reminders from storage
- Reschedules active alarms using AlarmScheduler

## Required Permissions

The alarm system requires these Android permissions:

1. `SCHEDULE_EXACT_ALARM` - For precise timing of alarms
2. `POST_NOTIFICATIONS` - For showing notifications
3. `VIBRATE` - For alarm vibration
4. `WAKE_LOCK` - To keep screen on during alarm
5. `RECEIVE_BOOT_COMPLETED` - To restore alarms after reboot

## Troubleshooting Guide

### Alarms Not Firing

If alarms are not triggering properly:

1. **Check Permissions**

   - Go to Settings > Apps > Alzheimer's Caregiver > Permissions
   - Ensure "Alarms & Reminders" and "Notifications" are enabled

2. **Battery Optimization**

   - Go to Settings > Apps > Alzheimer's Caregiver > Battery
   - Set to "Unrestricted" or disable battery optimization
   - Android's aggressive battery optimization can prevent alarms

3. **Check Sound Settings**

   - Ensure the alarm volume is set to an audible level
   - Check that the device isn't in Do Not Disturb mode

4. **After Device Reboot**
   - Open the app once after reboot to ensure all alarms are restored
   - Some Android versions restrict automatic app startup

### Device-Specific Settings

Some manufacturers add extra restrictions that can affect alarms:

1. **Samsung Devices**

   - Go to Settings > Battery > App Power Management > Unmonitored Apps
   - Add Alzheimer's Caregiver to unmonitored apps

2. **Xiaomi/MIUI Devices**

   - Go to Security app > Permissions > Autostart
   - Enable autostart for Alzheimer's Caregiver

3. **Huawei Devices**
   - Go to Phone Manager > Protected apps
   - Add Alzheimer's Caregiver to protected apps

## Best Practices

1. **Do not force-close the app** - This can prevent alarms from triggering
2. **Open the app after reboot** - Ensures all alarms are properly restored
3. **Set medication times strategically** - Avoid times when device might be off
4. **Test alarms after setup** - Schedule a test alarm to verify functionality

## Technical Limitations

1. **Power off** - Alarms cannot fire if device is completely powered off
2. **Extreme battery saver modes** - May prevent alarms from functioning
3. **Android version differences** - Some features may work differently across Android versions
4. **Manufacturer customizations** - Some device manufacturers modify how alarms work

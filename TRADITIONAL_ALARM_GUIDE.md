# Traditional Alarm Clock System Guide

## Introduction

The Alzheimer's Caregiver app now features a traditional alarm clock system for medication reminders. Unlike regular push notifications, this system will:

1. **Wake up the device** - Even if the screen is locked or the device is in sleep mode
2. **Play continuous alarm sound** - Similar to a regular alarm clock
3. **Vibrate continuously** - To help get the patient's attention
4. **Show a full-screen alarm interface** - With large clock display and clear medication information
5. **Require user interaction** - Must be explicitly dismissed or snoozed
6. **Survive device reboots** - Alarms are restored after device restart

This guide explains how to use and troubleshoot the new alarm system.

## How the Alarm System Works

### Triggering Alarms

When a medication reminder is due:

1. The system wakes up the device if it's asleep
2. A full-screen alarm activity launches showing:
   - Current time in large digits
   - AM/PM indicator
   - Medication name
   - Medication instructions
   - Dismiss and Snooze buttons
3. A loud alarm sound plays continuously (using the system's default alarm sound)
4. The device vibrates in a pattern similar to alarm clocks
5. A notification is also shown as a backup
6. The screen stays on until user interaction

### User Actions

When an alarm triggers:

- **Dismiss**: Stops the alarm sound and closes the alarm screen
- **Snooze**: Stops the current alarm and schedules a new one for 5 minutes later

## Permissions Required

For the alarm system to work properly, the app needs these permissions:

1. **Schedule Exact Alarms** (android.permission.SCHEDULE_EXACT_ALARM)

   - Required for precise timing of medication reminders
   - Android 12+ requires explicit user permission

2. **Post Notifications** (android.permission.POST_NOTIFICATIONS)

   - Required for showing notifications
   - Android 13+ requires explicit user permission

3. **Receive Boot Completed** (android.permission.RECEIVE_BOOT_COMPLETED)
   - Required to restore alarms after device restart

The app will automatically request these permissions as needed.

## Troubleshooting

### Alarm Not Ringing

If medication alarms aren't triggering properly:

1. **Check Permissions**:

   - Go to Android Settings > Apps > Alzheimer's Caregiver
   - Tap Permissions and ensure "Alarms & Reminders" and "Notifications" are enabled

2. **Battery Optimization**:

   - Go to Android Settings > Apps > Alzheimer's Caregiver
   - Tap "Battery" and select "Not optimized" or "Unrestricted"
   - This prevents Android from killing the app's background processes

3. **Volume Settings**:

   - Make sure alarm volume is set to an audible level
   - Check if the device is in silent or vibrate mode

4. **Restart the App**:

   - Force close and restart the app
   - This can help refresh the alarm registration

5. **Use Debug Feature**:
   - From the app's main menu, access the debug menu
   - Use "Test Alarm" to trigger a test medication alarm
   - This can verify if the alarm system is working correctly

### After Device Restart

The app automatically registers a boot receiver that restores all alarms after device restart. However:

- On some devices, there might be a delay before alarms are restored
- If you've scheduled medication reminders but notice they're not working after a restart, open the app once to ensure proper restoration

### Device-Specific Settings

Some Android manufacturers add extra battery saving features that can interfere with alarms:

1. **Samsung Devices**:

   - Go to Settings > Apps > Alzheimer's Caregiver
   - Tap "Battery" > "Allow background activity"

2. **Xiaomi/MIUI Devices**:

   - Go to Settings > Apps > Manage Apps > Alzheimer's Caregiver
   - Set "Autostart" to enabled
   - Go to Security app > Battery > App battery saver > Alzheimer's Caregiver > "No restrictions"

3. **Huawei Devices**:
   - Go to Settings > Apps > Alzheimer's Caregiver
   - Enable "Allow auto-launch"
   - Go to Settings > Battery > Launch > Alzheimer's Caregiver > "Manage manually" and enable all toggles

## Technical Details

The alarm system consists of several components:

1. **AlarmScheduler**: Interfaces with Android's AlarmManager to schedule precise alarms
2. **AlarmReceiver**: BroadcastReceiver that handles alarm triggers
3. **BootReceiver**: Restores alarms after device restart
4. **AlarmActivity**: Full-screen activity with a traditional alarm clock interface
5. **Notification System**: Provides backup notification with full-screen intent

These components work together to ensure reliable medication reminders that don't rely on cloud services or push notifications.

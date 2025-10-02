# How to Use Alarms Instead of Push Notifications

This guide explains how to use the AlarmManager-based reminder system that has been implemented in your app. The system uses local alarms that trigger notifications even when the app is closed, instead of relying solely on push notifications.

## Getting Started

1. **Open the Reminders screen**:

   - Navigate to the Reminders section in your app
   - You'll see a list of your existing reminders

2. **Create a new reminder with an alarm**:

   - Tap the "+" button to add a new reminder
   - Fill in the title and description
   - Tap on the date/time field to set when you want the alarm to trigger
   - Tap "Add" to save the reminder

3. **Test the alarm system**:
   - Tap the bell icon in the top-right corner of the Reminders screen
   - This will schedule a test alarm that will trigger in 10 seconds
   - You should see a notification appear shortly

## Permissions Required

For the alarm system to work properly, you need to grant two important permissions:

1. **Exact Alarm Permission** (Android 12+):

   - The app will prompt you to grant this permission
   - This allows alarms to trigger at the exact time you set

2. **Notification Permission** (Android 13+):
   - The app will request permission to show notifications
   - Without this, you won't see the alarm notifications

## Understanding Local Alarms vs. Push Notifications

### Local Alarms (AlarmManager)

- Work even when the app is closed or the device rebooted
- Trigger exactly at the scheduled time
- Don't require an internet connection
- Are reliable for time-critical reminders like medications
- Use device resources efficiently

### Push Notifications

- Require an internet connection
- May be delayed if the device is offline
- Useful for updates from caregivers or remote events
- Good for non-time-critical information

The app now uses both systems: local alarms for reliable scheduling and push notifications for remote updates.

## Troubleshooting

If alarms aren't working:

1. **Check permissions**:

   - Go to Settings > Apps > [App Name] > Permissions
   - Ensure "Alarms & Reminders" permission is granted
   - Check that notifications are allowed

2. **Battery optimization**:

   - Some devices restrict background activities
   - Go to Settings > Apps > [App Name] > Battery
   - Disable any battery optimization for the app

3. **Try the test alarm**:

   - Use the bell icon to trigger a test alarm
   - If this works but scheduled alarms don't, check the time settings

4. **Restart your device**:
   - After a restart, alarms will be automatically rescheduled
   - This can fix issues with the AlarmManager service

## Technical Details

This implementation uses:

- AlarmManager with setExactAndAllowWhileIdle for reliable triggering
- BroadcastReceivers for handling alarms and device reboots
- High-priority notification channels for important reminders
- A debug feature to test alarm functionality

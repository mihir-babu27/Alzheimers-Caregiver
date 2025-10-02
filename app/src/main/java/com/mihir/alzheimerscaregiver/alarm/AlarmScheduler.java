package com.mihir.alzheimerscaregiver.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.mihir.alzheimerscaregiver.data.ReminderEntity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for scheduling and managing alarms using AlarmManager
 */
public class AlarmScheduler {
    private static final String TAG = "AlarmScheduler";
    private static final String EXTRA_REMINDER_ID = "reminder_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_TYPE = "type";
    
    private final Context context;
    private final AlarmManager alarmManager;
    private final Map<String, Boolean> scheduledAlarms; // Track which alarms are currently scheduled
    
    public AlarmScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.scheduledAlarms = new HashMap<>();
    }
    
    /**
     * Schedule an alarm for a reminder
     * @param reminder The reminder to schedule an alarm for
     * @return true if the alarm was scheduled successfully, false otherwise
     */
    public boolean scheduleAlarm(ReminderEntity reminder) {
        if (reminder == null || reminder.getId() == null) {
            Log.e(TAG, "Cannot schedule alarm for null reminder or reminder without ID");
            return false;
        }
        
        long timeMillis = reminder.getTimeMillis();
        long currentTimeMillis = System.currentTimeMillis();
        
        if (timeMillis <= currentTimeMillis) {
            Log.w(TAG, "Not scheduling alarm for past time: " + timeMillis);
            return false;
        }
        
        // Create intent for AlarmReceiver
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_REMINDER_ID, reminder.getId());
        intent.putExtra(EXTRA_TITLE, reminder.getTitle());
        intent.putExtra(EXTRA_MESSAGE, reminder.getMessage());
        intent.putExtra(EXTRA_TYPE, reminder.getType());
        
        // Create unique request code based on reminder ID hash
        int requestCode = reminder.getId().hashCode();
        
        // Create pending intent that will fire the BroadcastReceiver
        PendingIntent operation = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        try {
            // Prefer setAlarmClock for true alarm semantics (bypasses DND, shows alarm UI affordances)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Create a showIntent to open the full-screen AlarmActivity
                Intent alarmActivityIntent = new Intent(context, AlarmActivity.class);
                alarmActivityIntent.putExtra(EXTRA_REMINDER_ID, reminder.getId());
                alarmActivityIntent.putExtra(EXTRA_TITLE, reminder.getTitle());
                alarmActivityIntent.putExtra(EXTRA_MESSAGE, reminder.getMessage());
                alarmActivityIntent.putExtra(EXTRA_TYPE, reminder.getType());
                alarmActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent showIntent = PendingIntent.getActivity(
                        context,
                        requestCode,
                        alarmActivityIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(timeMillis, showIntent);
                alarmManager.setAlarmClock(info, operation);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, operation);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, operation);
            }
            
            scheduledAlarms.put(reminder.getId(), true);
            Log.d(TAG, "Alarm scheduled for " + reminder.getTitle() + " at " + timeMillis);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule alarm", e);
            return false;
        }
    }
    
    /**
     * Cancel an alarm for a reminder
     * @param reminderId The ID of the reminder to cancel the alarm for
     */
    public void cancelAlarm(String reminderId) {
        if (reminderId == null) {
            Log.e(TAG, "Cannot cancel alarm for null reminder ID");
            return;
        }
        
        // Create intent similar to the one used for scheduling
        Intent intent = new Intent(context, AlarmReceiver.class);
        
        // Use same request code as when scheduling
        int requestCode = reminderId.hashCode();
        
        // Get the pending intent
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Cancel the alarm if the pending intent exists
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            scheduledAlarms.remove(reminderId);
            Log.d(TAG, "Alarm canceled for reminder: " + reminderId);
        } else {
            Log.d(TAG, "No alarm found to cancel for reminder: " + reminderId);
        }
    }
    
    /**
     * Check if an alarm is currently scheduled for a reminder
     * @param reminderId The ID of the reminder to check
     * @return true if an alarm is scheduled, false otherwise
     */
    public boolean isAlarmScheduled(String reminderId) {
        return scheduledAlarms.containsKey(reminderId) && scheduledAlarms.get(reminderId);
    }
    
    /**
     * Schedule an alarm using basic parameters instead of a ReminderEntity
     * This is useful for backward compatibility with existing code
     * 
     * @param reminderId The unique ID of the reminder
     * @param title The title of the reminder to show in the notification
     * @param message The message to show in the notification
     * @param timeMillis The time at which to trigger the alarm
     * @return true if the alarm was scheduled successfully, false otherwise
     */
    public boolean scheduleAlarm(String reminderId, String title, String message, long timeMillis) {
        if (reminderId == null || reminderId.isEmpty()) {
            Log.e(TAG, "Cannot schedule alarm with null or empty reminder ID");
            return false;
        }
        
        if (timeMillis <= System.currentTimeMillis()) {
            Log.w(TAG, "Not scheduling alarm for past time: " + timeMillis);
            return false;
        }
        
        // Create intent for AlarmReceiver
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_REMINDER_ID, reminderId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_MESSAGE, message);
        
        // Determine type based on title (simple heuristic)
        String type = "task";
        if (title != null && (title.toLowerCase().contains("medicine") || 
                              title.toLowerCase().contains("medication") ||
                              title.toLowerCase().contains("pill") ||
                              title.toLowerCase().contains("drug"))) {
            type = "medication";
        }
        intent.putExtra(EXTRA_TYPE, type);
        
        // Create unique request code based on reminder ID hash
        int requestCode = reminderId.hashCode();
        
    // Create pending intent that will fire the BroadcastReceiver
    PendingIntent operation = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        try {
            // Enhanced permission and capability checks
            Log.d(TAG, "Android SDK version: " + Build.VERSION.SDK_INT);
            
            // Check permission on Android 12+ (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                boolean canScheduleExact = alarmManager.canScheduleExactAlarms();
                Log.d(TAG, "Can schedule exact alarms: " + canScheduleExact);
                if (!canScheduleExact) {
                    Log.e(TAG, "Cannot schedule exact alarms - permission not granted. User needs to enable in Settings.");
                    // Still try to schedule with setAndAllowWhileIdle as fallback
                }
            }
            
            // Use the most robust scheduling method available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Also create a showIntent that opens the AlarmActivity
                Intent alarmActivityIntent = new Intent(context, AlarmActivity.class);
                alarmActivityIntent.putExtra(EXTRA_REMINDER_ID, reminderId);
                alarmActivityIntent.putExtra(EXTRA_TITLE, title);
                alarmActivityIntent.putExtra(EXTRA_MESSAGE, message);
                alarmActivityIntent.putExtra(EXTRA_TYPE, type);
                alarmActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent showIntent = PendingIntent.getActivity(
                        context,
                        requestCode,
                        alarmActivityIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                Log.d(TAG, "Using setAlarmClock for true alarm behavior");
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(timeMillis, showIntent);
                alarmManager.setAlarmClock(info, operation);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.d(TAG, "Using setExactAndAllowWhileIdle for maximum reliability");
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, operation);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Log.d(TAG, "Using setExact for API " + Build.VERSION.SDK_INT);
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, operation);
            } else {
                Log.d(TAG, "Using legacy set() method for older Android");
                alarmManager.set(AlarmManager.RTC_WAKEUP, timeMillis, operation);
            }
            
            scheduledAlarms.put(reminderId, true);
            
            // Additional diagnostic info
            long currentTime = System.currentTimeMillis();
            long delaySeconds = (timeMillis - currentTime) / 1000;
            Log.d(TAG, "Alarm scheduled to trigger in " + delaySeconds + " seconds");
            
            // Enhanced logging for debugging
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String formattedTime = dateFormat.format(new Date(timeMillis));
            Log.d(TAG, "Alarm scheduled successfully! Details: " +
                  "\nID: " + reminderId +
                  "\nTitle: " + title + 
                  "\nMessage: " + message +
                  "\nType: " + type + 
                  "\nTime: " + formattedTime +
                  "\nMillis: " + timeMillis +
                  "\nRequest Code: " + requestCode);
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule alarm: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Schedule a test alarm that triggers shortly after being set (for debugging)
     * 
     * @return true if test alarm was scheduled, false otherwise
     */
    public boolean scheduleTestAlarm() {
        try {
            Log.d(TAG, "Scheduling test alarm...");
            String testId = "test_" + System.currentTimeMillis();
            
            // Schedule for 10 seconds from now
            long triggerTime = System.currentTimeMillis() + 10 * 1000; 
            
            return scheduleAlarm(
                testId,
                "Test Alarm",
                "This is a test alarm to verify notifications are working",
                triggerTime
            );
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling test alarm", e);
            return false;
        }
    }
    
    /**
     * Clear all tracked alarms (does not actually cancel the alarms)
     * This is useful when you want to force rescheduling of all alarms
     */
    public void clearAlarmTracker() {
        scheduledAlarms.clear();
    }
}
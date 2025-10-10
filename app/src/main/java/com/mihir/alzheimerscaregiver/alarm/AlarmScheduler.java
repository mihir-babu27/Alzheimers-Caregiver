package com.mihir.alzheimerscaregiver.alarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.mihir.alzheimerscaregiver.data.ReminderEntity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for scheduling and managing alarms using AlarmManager
 * Supports daily repeating alarms with automatic rescheduling
 */
public class AlarmScheduler {
    private static final String TAG = "AlarmScheduler";
    private static final String EXTRA_REMINDER_ID = "reminder_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_MESSAGE = "message";
    private static final String EXTRA_TYPE = "type";
    private static final String EXTRA_IS_REPEATING = "is_repeating";
    private static final String EXTRA_ORIGINAL_TIME = "original_time";
    
    // SharedPreferences keys for storing repeating alarm info
    private static final String PREFS_NAME = "AlarmSchedulerPrefs";
    private static final String KEY_REPEATING_ALARMS = "repeating_alarms";
    
    private final Context context;
    private final AlarmManager alarmManager;
    private final SharedPreferences prefs;
    private final Map<String, Boolean> scheduledAlarms; // Track which alarms are currently scheduled
    
    public AlarmScheduler(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.scheduledAlarms = new HashMap<>();
    }
    
    /**
     * Schedule an alarm for a reminder with daily repetition support
     * @param reminder The reminder to schedule an alarm for
     * @param isRepeating Whether this alarm should repeat daily
     * @return true if the alarm was scheduled successfully, false otherwise
     */
    public boolean scheduleAlarm(ReminderEntity reminder, boolean isRepeating) {
        if (reminder == null || reminder.getId() == null) {
            Log.e(TAG, "Cannot schedule alarm for null reminder or reminder without ID");
            return false;
        }
        
        long timeMillis = reminder.getTimeMillis();
        long currentTimeMillis = System.currentTimeMillis();
        
        // If time is in the past and this is a repeating alarm, schedule for next occurrence
        if (timeMillis <= currentTimeMillis && isRepeating) {
            timeMillis = getNextDailyOccurrence(timeMillis);
            Log.d(TAG, "Scheduling repeating alarm for next occurrence: " + new Date(timeMillis));
        } else if (timeMillis <= currentTimeMillis) {
            Log.w(TAG, "Not scheduling alarm for past time: " + timeMillis);
            return false;
        }
        
        // Store repeating alarm info if needed
        if (isRepeating) {
            storeRepeatingAlarmInfo(reminder.getId(), reminder.getTimeMillis());
        }
        
        return scheduleAlarmInternalWithExtras(reminder, reminder.getId(), reminder.getTitle(), reminder.getMessage(), 
                                   reminder.getType(), timeMillis, isRepeating, reminder.getTimeMillis());
    }

    /**
     * Schedule an alarm for a reminder (backward compatibility)
     * @param reminder The reminder to schedule an alarm for
     * @return true if the alarm was scheduled successfully, false otherwise
     */
    public boolean scheduleAlarm(ReminderEntity reminder) {
        // Default to non-repeating for backward compatibility
        return scheduleAlarm(reminder, false);
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
        return scheduleAlarm(reminderId, title, message, timeMillis, false);
    }
    
    /**
     * Schedule an alarm using basic parameters with daily repetition support
     * 
     * @param reminderId The unique ID of the reminder
     * @param title The title of the reminder to show in the notification
     * @param message The message to show in the notification
     * @param timeMillis The time at which to trigger the alarm
     * @param isRepeating Whether this alarm should repeat daily
     * @return true if the alarm was scheduled successfully, false otherwise
     */
    public boolean scheduleAlarm(String reminderId, String title, String message, long timeMillis, boolean isRepeating) {
        // Determine type based on title (simple heuristic)
        String type = "task";
        if (title != null && (title.toLowerCase().contains("medicine") || 
                              title.toLowerCase().contains("medication") ||
                              title.toLowerCase().contains("pill") ||
                              title.toLowerCase().contains("drug"))) {
            type = "medication";
        }
        
        long scheduledTime = timeMillis;
        
        // If time is in the past and this is a repeating alarm, schedule for next occurrence
        if (timeMillis <= System.currentTimeMillis() && isRepeating) {
            scheduledTime = getNextDailyOccurrence(timeMillis);
            Log.d(TAG, "Scheduling repeating alarm for next occurrence: " + new Date(scheduledTime));
        }
        
        // Store repeating alarm info if needed
        if (isRepeating) {
            storeRepeatingAlarmInfo(reminderId, timeMillis);
        }
        
        return scheduleAlarmInternal(reminderId, title, message, type, scheduledTime, isRepeating, timeMillis);
    }

    /**
     * Enhanced schedule alarm method that accepts medicine names and image URLs
     * @param reminderId The ID of the reminder
     * @param title The alarm title
     * @param message The alarm message  
     * @param timeMillis The scheduled time in milliseconds
     * @param isRepeating Whether this alarm should repeat daily
     * @param medicineNames List of medicine names
     * @param imageUrls List of image URLs
     * @return true if the alarm was scheduled successfully, false otherwise
     */
    public boolean scheduleAlarmWithExtras(String reminderId, String title, String message, long timeMillis, 
                                          boolean isRepeating, java.util.List<String> medicineNames, java.util.List<String> imageUrls) {
        if (reminderId == null) {
            Log.e(TAG, "Cannot schedule alarm with null reminder ID");
            return false;
        }
        
        long currentTimeMillis = System.currentTimeMillis();
        
        // If time is in the past and this is a repeating alarm, schedule for next occurrence
        if (timeMillis <= currentTimeMillis && isRepeating) {
            timeMillis = getNextDailyOccurrence(timeMillis);
            Log.d(TAG, "Scheduling repeating alarm for next occurrence: " + new Date(timeMillis));
        } else if (timeMillis <= currentTimeMillis) {
            Log.w(TAG, "Not scheduling alarm for past time: " + timeMillis);
            return false;
        }
        
        // Store repeating alarm info if needed
        if (isRepeating) {
            storeRepeatingAlarmInfo(reminderId, timeMillis);
        }
        
        return scheduleAlarmInternalWithExtras(reminderId, title, message, "medication", timeMillis, 
                                              isRepeating, timeMillis, medicineNames, imageUrls);
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
    
    /**
     * Internal method to schedule an alarm with all parameters
     */
    private boolean scheduleAlarmInternal(String reminderId, String title, String message, 
                                        String type, long timeMillis, boolean isRepeating, long originalTime) {
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
        intent.putExtra(EXTRA_TYPE, type);
        intent.putExtra(EXTRA_IS_REPEATING, isRepeating);
        intent.putExtra(EXTRA_ORIGINAL_TIME, originalTime);
        
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
            Log.d(TAG, "Scheduling alarm - Android SDK version: " + Build.VERSION.SDK_INT);
            
            // Check permission on Android 12+ (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                boolean canScheduleExact = alarmManager.canScheduleExactAlarms();
                Log.d(TAG, "Can schedule exact alarms: " + canScheduleExact);
                if (!canScheduleExact) {
                    Log.e(TAG, "Cannot schedule exact alarms - permission not granted. User needs to enable in Settings.");
                }
            }
            
            // Use the most robust scheduling method available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Create a showIntent to open the full-screen AlarmActivity
                Intent alarmActivityIntent = new Intent(context, AlarmActivity.class);
                alarmActivityIntent.putExtra(EXTRA_REMINDER_ID, reminderId);
                alarmActivityIntent.putExtra(EXTRA_TITLE, title);
                alarmActivityIntent.putExtra(EXTRA_MESSAGE, message);
                alarmActivityIntent.putExtra(EXTRA_TYPE, type);
                alarmActivityIntent.putExtra(EXTRA_IS_REPEATING, isRepeating);
                alarmActivityIntent.putExtra(EXTRA_ORIGINAL_TIME, originalTime);
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
            
            // Enhanced logging for debugging
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String formattedTime = dateFormat.format(new Date(timeMillis));
            Log.d(TAG, "Alarm scheduled successfully! Details: " +
                  "\nID: " + reminderId +
                  "\nTitle: " + title + 
                  "\nMessage: " + message +
                  "\nType: " + type + 
                  "\nRepeating: " + isRepeating +
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
     * Enhanced internal method to schedule an alarm with additional ReminderEntity data
     */
    private boolean scheduleAlarmInternalWithExtras(ReminderEntity reminder, String reminderId, String title, String message, 
                                        String type, long timeMillis, boolean isRepeating, long originalTime) {
        if (reminderId == null || reminderId.isEmpty()) {
            Log.e(TAG, "Cannot schedule alarm with null or empty reminder ID");
            return false;
        }
        
        if (timeMillis <= System.currentTimeMillis() && !isRepeating) {
            Log.w(TAG, "Not scheduling alarm for past time: " + timeMillis);
            return false;
        }
        
        // Create intent for AlarmReceiver
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_REMINDER_ID, reminderId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_TYPE, type);
        intent.putExtra(EXTRA_IS_REPEATING, isRepeating);
        intent.putExtra(EXTRA_ORIGINAL_TIME, originalTime);
        
        // Add enhanced data with debug logging
        if (reminder.medicineNames != null && !reminder.medicineNames.isEmpty()) {
            String[] names = reminder.medicineNames.toArray(new String[0]);
            intent.putExtra("medicine_names", names);
            Log.d(TAG, "AlarmScheduler: Adding medicine names to intent: " + java.util.Arrays.toString(names));
        } else {
            Log.d(TAG, "AlarmScheduler: No medicine names to add - list is " + (reminder.medicineNames == null ? "null" : "empty"));
        }
        if (reminder.imageUrls != null && !reminder.imageUrls.isEmpty()) {
            String[] urls = reminder.imageUrls.toArray(new String[0]);
            intent.putExtra("image_urls", urls);
            Log.d(TAG, "AlarmScheduler: Adding image URLs to intent: " + java.util.Arrays.toString(urls));
        } else {
            Log.d(TAG, "AlarmScheduler: No image URLs to add - list is " + (reminder.imageUrls == null ? "null" : "empty"));
        }
        
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
            Log.d(TAG, "Scheduling enhanced alarm - Android SDK version: " + Build.VERSION.SDK_INT);
            
            // Check permission on Android 12+ (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                boolean canScheduleExact = alarmManager.canScheduleExactAlarms();
                Log.d(TAG, "Can schedule exact alarms: " + canScheduleExact);
                if (!canScheduleExact) {
                    Log.e(TAG, "Cannot schedule exact alarms - permission not granted. User needs to enable in Settings.");
                }
            }
            
            // Use the most robust scheduling method available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Create a showIntent to open the full-screen AlarmActivity
                Intent alarmActivityIntent = new Intent(context, AlarmActivity.class);
                alarmActivityIntent.putExtra(EXTRA_REMINDER_ID, reminderId);
                alarmActivityIntent.putExtra(EXTRA_TITLE, title);
                alarmActivityIntent.putExtra(EXTRA_MESSAGE, message);
                alarmActivityIntent.putExtra(EXTRA_TYPE, type);
                alarmActivityIntent.putExtra(EXTRA_IS_REPEATING, isRepeating);
                alarmActivityIntent.putExtra(EXTRA_ORIGINAL_TIME, originalTime);
                
                // Add enhanced data to alarm activity intent
                if (reminder.medicineNames != null && !reminder.medicineNames.isEmpty()) {
                    alarmActivityIntent.putExtra("medicine_names", reminder.medicineNames.toArray(new String[0]));
                }
                if (reminder.imageUrls != null && !reminder.imageUrls.isEmpty()) {
                    alarmActivityIntent.putExtra("image_urls", reminder.imageUrls.toArray(new String[0]));
                }
                
                alarmActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent showIntent = PendingIntent.getActivity(
                        context,
                        requestCode,
                        alarmActivityIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                
                AlarmManager.AlarmClockInfo clockInfo = new AlarmManager.AlarmClockInfo(timeMillis, showIntent);
                Log.d(TAG, "Using setAlarmClock for enhanced alarm behavior");
                alarmManager.setAlarmClock(clockInfo, operation);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Log.d(TAG, "Using setExactAndAllowWhileIdle for maximum reliability");
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, operation);
            } else {
                Log.d(TAG, "Using setExact for API " + Build.VERSION.SDK_INT);
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeMillis, operation);
            }
            
            // Enhanced alarm scheduled - information logged below
            
            Log.d(TAG, "Enhanced alarm scheduled successfully" +
                  "\nReminder ID: " + reminderId +
                  "\nTitle: " + title +
                  "\nMessage: " + message +
                  "\nType: " + type +
                  "\nScheduled Time: " + new Date(timeMillis) +
                  "\nIs Repeating: " + isRepeating +
                  "\nOriginal Time: " + originalTime +
                  "\nMedicine Count: " + (reminder.medicineNames != null ? reminder.medicineNames.size() : 0) +
                  "\nImage Count: " + (reminder.imageUrls != null ? reminder.imageUrls.size() : 0) +
                  "\nRequest Code: " + requestCode);
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule enhanced alarm: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Enhanced alarm scheduling with direct medicine and image lists (for compatibility with different entity types)
     */
    private boolean scheduleAlarmInternalWithExtras(String reminderId, String title, String message, 
                                        String type, long timeMillis, boolean isRepeating, long originalTime,
                                        java.util.List<String> medicineNames, java.util.List<String> imageUrls) {
        if (reminderId == null || reminderId.isEmpty()) {
            Log.e(TAG, "Cannot schedule alarm with null or empty reminder ID");
            return false;
        }
        
        if (timeMillis <= System.currentTimeMillis() && !isRepeating) {
            Log.w(TAG, "Not scheduling alarm for past time: " + timeMillis);
            return false;
        }
        
        // Create intent for AlarmReceiver
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_REMINDER_ID, reminderId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_TYPE, type);
        intent.putExtra(EXTRA_IS_REPEATING, isRepeating);
        intent.putExtra(EXTRA_ORIGINAL_TIME, originalTime);
        
        // Add enhanced data with debug logging
        if (medicineNames != null && !medicineNames.isEmpty()) {
            String[] names = medicineNames.toArray(new String[0]);
            intent.putExtra("medicine_names", names);
            Log.d(TAG, "AlarmScheduler: Adding medicine names to intent: " + java.util.Arrays.toString(names));
        } else {
            Log.d(TAG, "AlarmScheduler: No medicine names to add - list is " + (medicineNames == null ? "null" : "empty"));
        }
        if (imageUrls != null && !imageUrls.isEmpty()) {
            String[] urls = imageUrls.toArray(new String[0]);
            intent.putExtra("image_urls", urls);
            Log.d(TAG, "AlarmScheduler: Adding image URLs to intent: " + java.util.Arrays.toString(urls));
        } else {
            Log.d(TAG, "AlarmScheduler: No image URLs to add - list is " + (imageUrls == null ? "null" : "empty"));
        }
        
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
            // Schedule the alarm
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.e(TAG, "Cannot schedule exact alarms - permission not granted. User needs to enable in Settings.");
                    return false;
                }
            }
            
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, operation);
            
            Log.d(TAG, "Enhanced alarm scheduled successfully" +
                  "\nReminder ID: " + reminderId +
                  "\nTitle: " + title +
                  "\nMessage: " + message +
                  "\nType: " + type +
                  "\nScheduled Time: " + new Date(timeMillis) +
                  "\nIs Repeating: " + isRepeating +
                  "\nOriginal Time: " + originalTime +
                  "\nMedicine Count: " + (medicineNames != null ? medicineNames.size() : 0) +
                  "\nImage Count: " + (imageUrls != null ? imageUrls.size() : 0) +
                  "\nRequest Code: " + requestCode);
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule enhanced alarm: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get the next daily occurrence of a given time
     */
    private long getNextDailyOccurrence(long originalTimeMillis) {
        Calendar original = Calendar.getInstance();
        original.setTimeInMillis(originalTimeMillis);
        
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, original.get(Calendar.HOUR_OF_DAY));
        next.set(Calendar.MINUTE, original.get(Calendar.MINUTE));
        next.set(Calendar.SECOND, original.get(Calendar.SECOND));
        next.set(Calendar.MILLISECOND, original.get(Calendar.MILLISECOND));
        
        // If the time has already passed today, schedule for tomorrow
        if (next.getTimeInMillis() <= now.getTimeInMillis()) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        return next.getTimeInMillis();
    }
    
    /**
     * Store repeating alarm information in SharedPreferences
     */
    private void storeRepeatingAlarmInfo(String reminderId, long originalTime) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(reminderId + "_original_time", originalTime);
            editor.putBoolean(reminderId + "_is_repeating", true);
            editor.apply();
            Log.d(TAG, "Stored repeating alarm info for: " + reminderId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to store repeating alarm info", e);
        }
    }
    
    /**
     * Check if an alarm is set to repeat daily
     */
    public boolean isRepeatingAlarm(String reminderId) {
        return prefs.getBoolean(reminderId + "_is_repeating", false);
    }
    
    /**
     * Get the original time for a repeating alarm
     */
    public long getOriginalTime(String reminderId) {
        return prefs.getLong(reminderId + "_original_time", 0);
    }
    
    /**
     * Reschedule a repeating alarm for its next daily occurrence
     */
    public boolean rescheduleRepeatingAlarm(String reminderId, String title, String message, String type) {
        if (!isRepeatingAlarm(reminderId)) {
            Log.d(TAG, "Alarm " + reminderId + " is not a repeating alarm");
            return false;
        }
        
        long originalTime = getOriginalTime(reminderId);
        if (originalTime == 0) {
            Log.e(TAG, "No original time found for repeating alarm: " + reminderId);
            return false;
        }
        
        long nextOccurrence = getNextDailyOccurrence(originalTime);
        Log.d(TAG, "Rescheduling repeating alarm " + reminderId + " for next occurrence: " + new Date(nextOccurrence));
        
        return scheduleAlarmInternal(reminderId, title, message, type, nextOccurrence, true, originalTime);
    }
    
    /**
     * Remove repeating alarm info from storage
     */
    public void removeRepeatingAlarmInfo(String reminderId) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(reminderId + "_original_time");
            editor.remove(reminderId + "_is_repeating");
            editor.apply();
            Log.d(TAG, "Removed repeating alarm info for: " + reminderId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove repeating alarm info", e);
        }
    }
}
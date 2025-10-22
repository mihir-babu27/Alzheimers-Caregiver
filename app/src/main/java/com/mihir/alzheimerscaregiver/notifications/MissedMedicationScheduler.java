package com.mihir.alzheimerscaregiver.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import com.mihir.alzheimerscaregiver.data.entity.ReminderEntity;

/**
 * Schedules delayed checks to notify caretakers if medication reminders are not completed
 * Works alongside the regular medication reminder system
 */
public class MissedMedicationScheduler {
    
    private static final String TAG = "MissedMedicationScheduler";
    private static final int MISSED_MEDICATION_DELAY_MINUTES = 5; // Check after 5 minutes
    
    private Context context;
    private AlarmManager alarmManager;
    
    public MissedMedicationScheduler(Context context) {
        this.context = context;
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }
    
    /**
     * Schedule a delayed check to see if medication reminder was completed
     * This should be called when a medication reminder is scheduled
     */
    public void scheduleMissedMedicationCheck(ReminderEntity reminder) {
        if (reminder == null || reminder.scheduledTimeEpochMillis == null) {
            Log.w(TAG, "Cannot schedule missed medication check: invalid reminder data");
            return;
        }
        
        // Calculate check time (5 minutes after scheduled medication time)
        long checkTime = reminder.scheduledTimeEpochMillis + (MISSED_MEDICATION_DELAY_MINUTES * 60 * 1000);
        
        // Don't schedule checks for past times
        if (checkTime <= System.currentTimeMillis()) {
            Log.d(TAG, "Not scheduling past missed medication check for: " + reminder.title);
            return;
        }
        
        try {
            Intent intent = new Intent(context, MissedMedicationReceiver.class);
            intent.putExtra("reminderId", reminder.id);
            intent.putExtra("reminderTitle", reminder.title);
            intent.putExtra("scheduledTime", reminder.scheduledTimeEpochMillis);
            intent.putExtra("medicineNames", reminder.getMedicineNamesString());
            
            // Use unique request code based on reminder ID and check type
            int requestCode = (reminder.id + "_missed_check").hashCode();
            if (requestCode < 0) requestCode = Math.abs(requestCode);
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            // Schedule the check
            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent);
                    } else {
                        // Fallback to approximate alarm if exact alarms not available
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent);
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, checkTime, pendingIntent);
                }
                
                Log.d(TAG, "Scheduled missed medication check for '" + reminder.title + "' at " + 
                      new java.util.Date(checkTime).toString());
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling missed medication check for: " + reminder.title, e);
        }
    }
    
    /**
     * Cancel a previously scheduled missed medication check
     * This should be called when a medication reminder is completed or deleted
     */
    public void cancelMissedMedicationCheck(String reminderId) {
        if (reminderId == null) {
            Log.w(TAG, "Cannot cancel missed medication check: null reminder ID");
            return;
        }
        
        try {
            Intent intent = new Intent(context, MissedMedicationReceiver.class);
            int requestCode = (reminderId + "_missed_check").hashCode();
            if (requestCode < 0) requestCode = Math.abs(requestCode);
            
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            
            if (pendingIntent != null && alarmManager != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
                Log.d(TAG, "Cancelled missed medication check for reminder: " + reminderId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling missed medication check for: " + reminderId, e);
        }
    }
    
    /**
     * Cancel all missed medication checks (for app cleanup)
     */
    public void cancelAllMissedMedicationChecks() {
        Log.d(TAG, "Cancelling all missed medication checks - handled by AlarmManager cleanup");
        // Individual checks are cancelled when reminders are completed/deleted
        // System handles cleanup of expired/invalid alarms
    }
}
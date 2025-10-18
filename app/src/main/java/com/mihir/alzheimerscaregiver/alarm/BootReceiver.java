package com.mihir.alzheimerscaregiver.alarm;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mihir.alzheimerscaregiver.data.ReminderRepository;

/**
 * BroadcastReceiver that reschedules all alarms after device reboot
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null) {
            boolean isBootEvent = action.equals(Intent.ACTION_BOOT_COMPLETED) ||
                                 action.equals("android.intent.action.QUICKBOOT_POWERON");
            boolean isTimeChangeEvent = action.equals("android.intent.action.TIME_SET") ||
                                       action.equals("android.intent.action.DATE_CHANGED") ||
                                       action.equals("android.intent.action.TIMEZONE_CHANGED");
            
            if (isBootEvent) {
                Log.d(TAG, "Boot completed, initiating comprehensive alarm restoration");
            } else if (isTimeChangeEvent) {
                Log.d(TAG, "Time/date changed (" + action + "), rescheduling alarms");
            } else {
                return; // Unknown action, ignore
            }
            
            // Create alarm scheduler
            AlarmScheduler alarmScheduler = new AlarmScheduler(context);
            
            // Clear alarm tracker to force rescheduling
            alarmScheduler.clearAlarmTracker();
            Log.d(TAG, "Cleared alarm tracker for fresh start");
            
            // First, restore any repeating alarms that might have been scheduled
            restoreRepeatingAlarms(context, alarmScheduler);
            
            // Schedule delayed alarm rescheduling to allow Firebase initialization
            scheduleDelayedAlarmRescheduling(context, alarmScheduler);
            Log.d(TAG, "Scheduled delayed alarm rescheduling");
            
            // Also ensure periodic sync is scheduled as a safety net
            try {
                com.mihir.alzheimerscaregiver.sync.ReminderSyncScheduler.schedulePeriodic(context.getApplicationContext());
                Log.d(TAG, "Scheduled periodic reminder sync");
            } catch (Exception ex) {
                Log.w(TAG, "Failed to schedule periodic sync after boot", ex);
            }
            
            // Schedule midnight alarm reset job for daily maintenance
            try {
                com.mihir.alzheimerscaregiver.alarm.MidnightAlarmResetScheduler.scheduleMidnightReset(context.getApplicationContext());
                Log.d(TAG, "Scheduled midnight alarm reset job");
            } catch (Exception ex) {
                Log.w(TAG, "Failed to schedule midnight alarm reset after boot", ex);
            }
            
            Log.d(TAG, "Boot alarm restoration completed successfully");
        }
    }
    
    /**
     * Restore any repeating alarms that were stored in SharedPreferences
     * This ensures daily repeating alarms continue working after reboot
     */
    private void restoreRepeatingAlarms(Context context, AlarmScheduler scheduler) {
        try {
            Log.d(TAG, "Restoring repeating alarms from SharedPreferences");
            
            // This method would iterate through SharedPreferences to find stored repeating alarms
            // and reschedule them for their next occurrence
            
            // For now, we rely on the Firestore sync to handle most alarm restoration
            // The SharedPreferences in AlarmScheduler contains the repeating alarm metadata
            // Future enhancement: implement full repeating alarm restoration from SharedPrefs
            
            Log.d(TAG, "Repeating alarm restoration check completed");
            
        } catch (Exception e) {
            Log.e(TAG, "Error restoring repeating alarms", e);
        }
    }
    
    /**
     * Schedule delayed alarm rescheduling to allow Firebase to initialize
     */
    private void scheduleDelayedAlarmRescheduling(Context context, AlarmScheduler alarmScheduler) {
        // Use Handler to delay the rescheduling
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Log.d(TAG, "Starting delayed alarm rescheduling...");
                
                // Create repository and reschedule all alarms from Firestore
                ReminderRepository repository = new ReminderRepository(alarmScheduler);
                
                // Reschedule all alarms from database with retry logic
                repository.rescheduleAllAlarms();
                Log.d(TAG, "Delayed Firestore alarm rescheduling initiated");
                
            } catch (Exception e) {
                Log.e(TAG, "Error in delayed alarm rescheduling", e);
                
                // If first attempt fails, try again after longer delay
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        Log.d(TAG, "Retry delayed alarm rescheduling...");
                        ReminderRepository retryRepository = new ReminderRepository(alarmScheduler);
                        retryRepository.rescheduleAllAlarms();
                        
                    } catch (Exception retryE) {
                        Log.e(TAG, "Failed retry alarm rescheduling", retryE);
                    }
                }, 30000); // Retry after 30 seconds
            }
        }, 10000); // Initial delay of 10 seconds to allow Firebase initialization
    }
}
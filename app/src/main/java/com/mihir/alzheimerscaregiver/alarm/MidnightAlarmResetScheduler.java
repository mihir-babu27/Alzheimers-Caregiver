package com.mihir.alzheimerscaregiver.alarm;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler for midnight alarm reset job using WorkManager
 * Ensures alarms are properly maintained and rescheduled daily
 */
public class MidnightAlarmResetScheduler {
    private static final String TAG = "MidnightAlarmResetScheduler";
    private static final String WORK_NAME = "midnight_alarm_reset";
    
    /**
     * Schedule the midnight alarm reset job to run daily at 00:00
     */
    public static void scheduleMidnightReset(Context context) {
        try {
            Log.d(TAG, "Scheduling midnight alarm reset job");
            
            // Calculate initial delay to next midnight
            long initialDelayMillis = getMillisUntilNextMidnight();
            
            // Create constraints - don't require network, allow on battery
            Constraints constraints = new Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build();
            
            // Create periodic work request that runs every 24 hours
            PeriodicWorkRequest midnightResetWork = new PeriodicWorkRequest.Builder(
                    MidnightAlarmResetWorker.class, 
                    24, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                    .build();
            
            // Schedule the work, replacing any existing instance
            WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(
                            WORK_NAME, 
                            ExistingPeriodicWorkPolicy.REPLACE, 
                            midnightResetWork
                    );
            
            Log.d(TAG, "Midnight alarm reset job scheduled successfully. Initial delay: " + 
                  (initialDelayMillis / 1000 / 60) + " minutes");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule midnight alarm reset job", e);
        }
    }
    
    /**
     * Cancel the midnight reset job
     */
    public static void cancelMidnightReset(Context context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
            Log.d(TAG, "Midnight alarm reset job cancelled");
        } catch (Exception e) {
            Log.e(TAG, "Failed to cancel midnight alarm reset job", e);
        }
    }
    
    /**
     * Calculate milliseconds until next midnight (00:00)
     */
    private static long getMillisUntilNextMidnight() {
        Calendar now = Calendar.getInstance();
        Calendar nextMidnight = Calendar.getInstance();
        
        // Set to next midnight
        nextMidnight.add(Calendar.DAY_OF_MONTH, 1);
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.set(Calendar.MILLISECOND, 0);
        
        long delayMillis = nextMidnight.getTimeInMillis() - now.getTimeInMillis();
        
        Log.d(TAG, "Next midnight in " + (delayMillis / 1000 / 60) + " minutes");
        return delayMillis;
    }
}
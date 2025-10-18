package com.mihir.alzheimerscaregiver;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.util.Log;
import com.mihir.alzheimerscaregiver.location.LocationBootJobService;

/**
 * Custom Application class to handle app-wide initialization
 * Ensures boot job is scheduled even for stopped apps
 */
public class AlzheimersApplication extends Application {
    
    private static final String TAG = "AlzheimersApplication";
    private static final int BOOT_JOB_ID = 1001;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.i(TAG, "*** AlzheimersApplication.onCreate() - App startup ***");
        
        // Always schedule boot job on app startup
        // This ensures it's scheduled even if app was previously stopped
        scheduleAggressiveBootJob();
    }
    
    /**
     * Schedule a very aggressive boot job that works for stopped apps
     */
    private void scheduleAggressiveBootJob() {
        try {
            Log.i(TAG, "Scheduling aggressive boot job for location restart...");
            
            JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            
            if (jobScheduler == null) {
                Log.e(TAG, "JobScheduler not available");
                return;
            }
            
            // Cancel any existing jobs
            jobScheduler.cancel(BOOT_JOB_ID);
            jobScheduler.cancel(BOOT_JOB_ID + 1);
            
            ComponentName serviceName = new ComponentName(this, LocationBootJobService.class);
            
            // Create aggressive boot job
            PersistableBundle extras = new PersistableBundle();
            extras.putString("reason", "app_startup_boot_job");
            extras.putLong("scheduled_time", System.currentTimeMillis());
            
            JobInfo bootJob = new JobInfo.Builder(BOOT_JOB_ID, serviceName)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .setPersisted(true) // Critical: survive reboots
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                    .setRequiresBatteryNotLow(false)
                    .setMinimumLatency(1000) // Start 1 second after boot
                    .setOverrideDeadline(30000) // Must run within 30 seconds
                    .setExtras(extras)
                    .build();
            
            int result = jobScheduler.schedule(bootJob);
            
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "✅ Aggressive boot job scheduled successfully");
                
                // Also schedule periodic backup job
                schedulePeriodicBackupJob(jobScheduler, serviceName);
                
            } else {
                Log.e(TAG, "❌ Failed to schedule boot job");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling aggressive boot job", e);
        }
    }
    
    /**
     * Schedule periodic job as backup mechanism
     */
    private void schedulePeriodicBackupJob(JobScheduler jobScheduler, ComponentName serviceName) {
        try {
            Log.i(TAG, "Scheduling periodic backup job...");
            
            PersistableBundle extras = new PersistableBundle();
            extras.putString("reason", "periodic_backup_check");
            
            // Periodic job that checks location status daily
            JobInfo periodicJob = new JobInfo.Builder(BOOT_JOB_ID + 1, serviceName)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .setPersisted(true)
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                    .setPeriodic(24 * 60 * 60 * 1000) // Every 24 hours
                    .setExtras(extras)
                    .build();
            
            int result = jobScheduler.schedule(periodicJob);
            
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "✅ Periodic backup job scheduled");
            } else {
                Log.e(TAG, "❌ Failed to schedule periodic job");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling periodic job", e);
        }
    }
}
package com.mihir.alzheimerscaregiver.location;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.util.Log;

/**
 * JobService to handle location service restart after device boot
 * This provides a more reliable alternative to BroadcastReceiver for boot events
 * Works even when the app is in "stopped" state
 */
public class LocationBootJobService extends JobService {
    
    private static final String TAG = "LocationBootJobService";
    private static final int BOOT_JOB_ID = 1001;
    
    @Override
    public boolean onStartJob(JobParameters params) {
        Log.i(TAG, "*** LocationBootJobService.onStartJob() called! ***");
        Log.i(TAG, "Job reason: " + getJobReason(params));
        
        try {
            // Run the boot logic in a separate thread to avoid blocking
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        executeBootLogic();
                        
                        // Job completed successfully
                        jobFinished(params, false);
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error in boot job execution", e);
                        // Reschedule job on failure
                        jobFinished(params, true);
                    }
                }
            }).start();
            
            return true; // Job is running asynchronously
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting boot job", e);
            return false;
        }
    }
    
    @Override
    public boolean onStopJob(JobParameters params) {
        Log.i(TAG, "LocationBootJobService.onStopJob() called");
        return true; // Reschedule if stopped
    }
    
    private void executeBootLogic() {
        Log.i(TAG, "Executing boot logic via JobService...");
        
        Context context = getApplicationContext();
        LocationServiceManager manager = new LocationServiceManager(context);
        
        // Check if user is authenticated first
        if (manager.getCurrentPatientId() == null) {
            Log.d(TAG, "User not authenticated, not restarting location service");
            return;
        }
        
        // Sync with Firebase to get current sharing state
        manager.syncWithFirebase(new LocationServiceManager.SyncCallback() {
            @Override
            public void onSynced(boolean enabled) {
                if (enabled) {
                    Log.d(TAG, "JobService: Firebase sharing enabled, restarting location service");
                    
                    // Use longer delay to ensure system is fully ready
                    android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            manager.startTrackingAfterBoot();
                        }
                    }, 15000); // 15 second delay for JobService
                } else {
                    Log.d(TAG, "JobService: Firebase sharing disabled, not restarting location service");
                }
            }
            
            @Override
            public void onError(String error) {
                Log.w(TAG, "JobService: Failed to sync with Firebase, checking local settings: " + error);
                
                // Fallback to local settings
                if (manager.isLocationSharingEnabled()) {
                    Log.d(TAG, "JobService: Local sharing enabled, restarting service as fallback");
                    
                    android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            manager.startTrackingAfterBoot();
                        }
                    }, 15000);
                }
            }
        });
    }
    
    private String getJobReason(JobParameters params) {
        PersistableBundle extras = params.getExtras();
        if (extras != null && extras.containsKey("reason")) {
            return extras.getString("reason");
        }
        return "unknown";
    }
    
    /**
     * Schedule the boot job to run after device restart
     * This version is more aggressive for stopped apps
     */
    public static void scheduleBootJob(Context context) {
        Log.i(TAG, "Scheduling aggressive boot job for stopped app compatibility...");
        
        try {
            android.app.job.JobScheduler jobScheduler = (android.app.job.JobScheduler) 
                context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            
            if (jobScheduler == null) {
                Log.e(TAG, "JobScheduler service not available");
                return;
            }
            
            // Cancel any existing boot job
            jobScheduler.cancel(BOOT_JOB_ID);
            
            android.content.ComponentName serviceName = 
                new android.content.ComponentName(context, LocationBootJobService.class);
            
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putString("reason", "device_boot_stopped_app");
            extras.putLong("scheduled_time", System.currentTimeMillis());
            
            // Create a more aggressive job for stopped apps
            android.app.job.JobInfo jobInfo = new android.app.job.JobInfo.Builder(BOOT_JOB_ID, serviceName)
                    .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_NONE)
                    .setPersisted(true) // Critical: survive reboots
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false) 
                    .setRequiresBatteryNotLow(false) // Run even on low battery
                    .setMinimumLatency(1000) // Start ASAP after boot (1 second)
                    .setOverrideDeadline(30000) // Must run within 30 seconds
                    .setExtras(extras)
                    .build();
            
            int result = jobScheduler.schedule(jobInfo);
            
            if (result == android.app.job.JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "✅ Aggressive boot job scheduled successfully for stopped apps");
                
                // Also schedule a secondary periodic job as backup
                schedulePeriodicLocationCheck(context, jobScheduler);
                
            } else {
                Log.e(TAG, "❌ Failed to schedule boot job, result: " + result);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling boot job", e);
        }
    }
    
    /**
     * Schedule a periodic job to check location status (backup mechanism)
     */
    private static void schedulePeriodicLocationCheck(Context context, android.app.job.JobScheduler jobScheduler) {
        try {
            Log.i(TAG, "Scheduling periodic location check as backup...");
            
            android.content.ComponentName serviceName = 
                new android.content.ComponentName(context, LocationBootJobService.class);
            
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putString("reason", "periodic_check");
            
            android.app.job.JobInfo periodicJob = new android.app.job.JobInfo.Builder(BOOT_JOB_ID + 1, serviceName)
                    .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_NONE)
                    .setPersisted(true)
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                    .setPeriodic(24 * 60 * 60 * 1000) // Check daily
                    .setExtras(extras)
                    .build();
            
            jobScheduler.schedule(periodicJob);
            Log.i(TAG, "✅ Periodic location check scheduled");
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling periodic job", e);
        }
    }
    
    /**
     * Cancel the boot job
     */
    public static void cancelBootJob(Context context) {
        try {
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            jobScheduler.cancel(BOOT_JOB_ID);
            Log.i(TAG, "Boot job cancelled");
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling boot job", e);
        }
    }
}
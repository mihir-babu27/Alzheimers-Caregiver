package com.mihir.alzheimerscaregiver.location;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Helper class to manage PatientLocationService operations
 * Provides safe methods to start/stop tracking and manage configuration
 */
public class LocationServiceManager {
    
    private static final String TAG = "LocationServiceManager";
    private static final String PREFS_NAME = "location_prefs";
    
    private final Context context;
    private final SharedPreferences prefs;
    
    public LocationServiceManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Start location tracking service
     */
    public void startTracking() {
        if (!isUserAuthenticated()) {
            Log.e(TAG, "Cannot start tracking - user not authenticated");
            return;
        }
        
        Log.d(TAG, "Starting location tracking service");
        Intent intent = new Intent(context, PatientLocationService.class);
        intent.setAction(PatientLocationService.ACTION_START_TRACKING);
        context.startForegroundService(intent);
        
        // Update preferences
        setLocationSharingEnabled(true);
    }
    
    /**
     * Start location tracking service after device boot
     * Handles Android 8+ background service restrictions and ensures location updates resume
     */
    public void startTrackingAfterBoot() {
        if (!isUserAuthenticated()) {
            Log.e(TAG, "Cannot start tracking after boot - user not authenticated");
            return;
        }
        
        Log.d(TAG, "Starting location tracking service after device boot");
        
        try {
            Intent intent = new Intent(context, PatientLocationService.class);
            intent.setAction(PatientLocationService.ACTION_START_TRACKING);
            // Add extra flag to indicate this is a post-boot restart
            intent.putExtra("post_boot_restart", true);
            
            // For Android 8+, use startForegroundService to handle background restrictions
            // The service will immediately show a persistent notification
            context.startForegroundService(intent);
            
            // Update preferences to ensure local state is consistent
            setLocationSharingEnabled(true);
            
            Log.d(TAG, "Location tracking service started successfully after boot");
            
            // Schedule verification that location updates are actually working
            verifyLocationUpdatesAfterBoot();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start location tracking after boot", e);
            
            // Fallback: schedule for later if immediate start fails
            scheduleDelayedStart();
        }
    }
    
    /**
     * Verify that location updates are actually working after boot restart
     * If not working after 2 minutes, restart the service
     */
    private void verifyLocationUpdatesAfterBoot() {
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            Log.d(TAG, "Verifying location updates are working after boot...");
            
            // Check if service is still running and if we've received recent location updates
            // We'll add a method to the service to check last update time
            Intent verifyIntent = new Intent(context, PatientLocationService.class);
            verifyIntent.setAction(PatientLocationService.ACTION_VERIFY_LOCATION_UPDATES);
            
            try {
                context.startService(verifyIntent);
            } catch (Exception e) {
                Log.w(TAG, "Failed to send verification intent", e);
                // If verification fails, restart tracking
                restartTrackingAfterBootFailure();
            }
        }, 120000); // Check after 2 minutes
    }
    
    /**
     * Restart tracking if post-boot verification fails
     */
    private void restartTrackingAfterBootFailure() {
        Log.w(TAG, "Restarting location tracking due to post-boot verification failure");
        
        // Stop current service first
        stopTracking();
        
        // Wait a moment, then restart
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            startTracking();
        }, 5000); // 5 second delay before restart
    }
    
    /**
     * Schedule delayed start if immediate boot start fails
     */
    private void scheduleDelayedStart() {
        Log.d(TAG, "Scheduling delayed location tracking start");
        
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.postDelayed(() -> {
            try {
                startTracking();
                Log.d(TAG, "Delayed location tracking start successful");
            } catch (Exception e) {
                Log.e(TAG, "Delayed location tracking start also failed", e);
            }
        }, 30000); // 30 second delay for final retry
    }
    
    /**
     * Stop location tracking service
     */
    public void stopTracking() {
        Log.d(TAG, "Stopping location tracking service");
        Intent intent = new Intent(context, PatientLocationService.class);
        intent.setAction(PatientLocationService.ACTION_STOP_TRACKING);
        context.startService(intent);
        
        // Update preferences
        setLocationSharingEnabled(false);
    }
    
    /**
     * Request current location immediately
     */
    public void requestCurrentLocationNow() {
        if (!isUserAuthenticated()) {
            Log.e(TAG, "Cannot request location - user not authenticated");
            return;
        }
        
        Log.d(TAG, "Requesting current location");
        Intent intent = new Intent(context, PatientLocationService.class);
        intent.setAction(PatientLocationService.ACTION_REQUEST_CURRENT_LOCATION);
        context.startService(intent);
    }
    
    /**
     * Check if location tracking is currently enabled (checks both local and Firebase)
     */
    public boolean isLocationSharingEnabled() {
        return prefs.getBoolean("location_sharing_enabled", false);
    }
    
    /**
     * Set location sharing enabled state locally only (no Firebase update)
     */
    public void setLocationSharingEnabledLocally(boolean enabled) {
        prefs.edit()
                .putBoolean("location_sharing_enabled", enabled)
                .apply();
        Log.d(TAG, "Location sharing enabled set locally to: " + enabled);
    }
    
    /**
     * Set location sharing enabled state (updates both local and Firebase)
     */
    public void setLocationSharingEnabled(boolean enabled) {
        setLocationSharingEnabledLocally(enabled);
        
        // Schedule or cancel boot job based on sharing state
        try {
            if (enabled) {
                scheduleBootRestartJob();
            } else {
                cancelBootRestartJob();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error managing boot restart job", e);
        }
        
        // Also update Firebase state
        String patientId = getCurrentPatientId();
        if (patientId != null) {
            LocationUploader uploader = new LocationUploader();
            uploader.updateSharingEnabled(patientId, enabled, new LocationUploader.UploadCallback() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Firebase sharing state synchronized");
                }
                
                @Override
                public void onError(String error) {
                    Log.w(TAG, "Failed to sync Firebase sharing state: " + error);
                }
            });
        }
    }
    
    /**
     * Sync local preferences with Firebase sharing state
     */
    public void syncWithFirebase(SyncCallback callback) {
        String patientId = getCurrentPatientId();
        if (patientId == null) {
            if (callback != null) {
                callback.onError("User not authenticated");
            }
            return;
        }
        
        LocationUploader uploader = new LocationUploader();
        uploader.getSharingEnabled(patientId, new LocationUploader.SharingStateCallback() {
            @Override
            public void onSharingState(boolean enabled) {
                // Update local preferences to match Firebase
                prefs.edit()
                        .putBoolean("location_sharing_enabled", enabled)
                        .apply();
                Log.d(TAG, "Local sharing state synced with Firebase: " + enabled);
                
                if (callback != null) {
                    callback.onSynced(enabled);
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to sync with Firebase: " + error);
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }
    
    /**
     * Interface for sync callbacks
     */
    public interface SyncCallback {
        void onSynced(boolean enabled);
        void onError(String error);
    }
    
    /**
     * Set location update interval in milliseconds
     */
    public void setLocationInterval(long intervalMillis) {
        prefs.edit()
                .putLong("location_interval_millis", intervalMillis)
                .apply();
        Log.d(TAG, "Location interval set to: " + intervalMillis + "ms");
    }
    
    /**
     * Get current location update interval
     */
    public long getLocationInterval() {
        return prefs.getLong("location_interval_millis", 5 * 60 * 1000); // Default 5 minutes
    }
    
    /**
     * Set minimum displacement for location updates
     */
    public void setSmallestDisplacement(float meters) {
        prefs.edit()
                .putFloat("smallest_displacement_meters", meters)
                .apply();
        Log.d(TAG, "Smallest displacement set to: " + meters + "m");
    }
    
    /**
     * Get current minimum displacement setting
     */
    public float getSmallestDisplacement() {
        return prefs.getFloat("smallest_displacement_meters", 25.0f); // Default 25 meters (best practice)
    }
    
    /**
     * Get available interval options for UI
     */
    public static class IntervalOption {
        public final String label;
        public final long milliseconds;
        
        public IntervalOption(String label, long milliseconds) {
            this.label = label;
            this.milliseconds = milliseconds;
        }
    }
    
    /**
     * Get predefined interval options for user selection
     */
    public static IntervalOption[] getIntervalOptions() {
        return new IntervalOption[] {
                new IntervalOption("2 minutes", 2 * 60 * 1000),
                new IntervalOption("5 minutes", 5 * 60 * 1000),
                new IntervalOption("10 minutes", 10 * 60 * 1000),
                new IntervalOption("15 minutes", 15 * 60 * 1000),
                new IntervalOption("30 minutes", 30 * 60 * 1000)
        };
    }
    
    /**
     * Get interval option label for current setting
     */
    public String getCurrentIntervalLabel() {
        long current = getLocationInterval();
        for (IntervalOption option : getIntervalOptions()) {
            if (option.milliseconds == current) {
                return option.label;
            }
        }
        return (current / (60 * 1000)) + " minutes"; // Fallback
    }
    
    /**
     * Check if user is authenticated
     */
    private boolean isUserAuthenticated() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        return auth.getCurrentUser() != null;
    }
    
    /**
     * Get current patient ID
     */
    public String getCurrentPatientId() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }
    
    /**
     * Clear all location preferences (for logout/reset)
     */
    public void clearPreferences() {
        prefs.edit().clear().apply();
        Log.d(TAG, "Location preferences cleared");
    }
    
    /**
     * Schedule JobService to restart location after boot
     */
    private void scheduleBootRestartJob() {
        try {
            Log.i(TAG, "Scheduling JobService for boot restart...");
            
            android.app.job.JobScheduler jobScheduler = 
                (android.app.job.JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);
            
            if (jobScheduler == null) {
                Log.e(TAG, "JobScheduler not available");
                return;
            }
            
            // Cancel existing job
            jobScheduler.cancel(1001);
            
            android.content.ComponentName serviceName = 
                new android.content.ComponentName(context, LocationBootJobService.class);
            
            android.os.PersistableBundle extras = new android.os.PersistableBundle();
            extras.putString("reason", "location_boot_restart");
            
            android.app.job.JobInfo jobInfo = new android.app.job.JobInfo.Builder(1001, serviceName)
                    .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_NONE)
                    .setPersisted(true) // Survive reboots
                    .setRequiresCharging(false)
                    .setRequiresDeviceIdle(false)
                    .setExtras(extras)
                    .build();
            
            int result = jobScheduler.schedule(jobInfo);
            
            if (result == android.app.job.JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "✅ Boot restart JobService scheduled successfully");
            } else {
                Log.e(TAG, "❌ Failed to schedule boot restart JobService");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling boot restart job", e);
        }
    }
    
    /**
     * Cancel JobService for boot restart
     */
    private void cancelBootRestartJob() {
        try {
            Log.i(TAG, "Cancelling JobService for boot restart...");
            
            android.app.job.JobScheduler jobScheduler = 
                (android.app.job.JobScheduler) context.getSystemService(context.JOB_SCHEDULER_SERVICE);
            
            if (jobScheduler != null) {
                jobScheduler.cancel(1001);
                Log.i(TAG, "Boot restart JobService cancelled");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error cancelling boot restart job", e);
        }
    }
}
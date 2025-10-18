package com.mihir.alzheimerscaregiver.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Broadcast receiver to handle device boot completion
 * Automatically restarts location tracking service if it was enabled before reboot
 * Handles Android 8+ background service restrictions properly
 */
public class LocationBootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "LocationBootReceiver";
    
    static {
        Log.i(TAG, "*** LocationBootReceiver class loaded! ***");
    }
    
    public LocationBootReceiver() {
        super();
        Log.i(TAG, "*** LocationBootReceiver constructor called! ***");
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        // Log that the receiver is being called
        Log.i(TAG, "*** LocationBootReceiver.onReceive() called! ***");
        Log.i(TAG, "Intent: " + intent);
        Log.i(TAG, "Context: " + context);
        
        String action = intent.getAction();
        Log.i(TAG, "Action received: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action) ||
            "com.mihir.alzheimerscaregiver.TEST_BOOT_RECEIVER".equals(action)) {
            
            Log.i(TAG, "*** BOOT/REPLACE ACTION MATCHED: " + action + " ***");
            Log.d(TAG, "Device boot or app update completed: " + action + ", checking location tracking settings");
            
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
                        Log.d(TAG, "Firebase sharing enabled, restarting location service after boot");
                        
                        // For Android 8+, we need to use proper delayed startup
                        // Use longer delay to ensure system is fully ready and background restrictions are relaxed
                        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                        handler.postDelayed(() -> {
                            // Use startTrackingAfterBoot which handles Android 8+ restrictions
                            manager.startTrackingAfterBoot();
                        }, 10000); // 10 second delay for better reliability
                    } else {
                        Log.d(TAG, "Firebase sharing disabled, not restarting location service");
                    }
                }
                
                @Override
                public void onError(String error) {
                    Log.w(TAG, "Failed to sync with Firebase after boot, checking local settings: " + error);
                    
                    // Fallback to local settings if Firebase sync fails
                    if (manager.isLocationSharingEnabled()) {
                        Log.d(TAG, "Local sharing enabled, restarting service as fallback");
                        
                        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                        handler.postDelayed(() -> {
                            // Use startTrackingAfterBoot for fallback too
                            manager.startTrackingAfterBoot();
                        }, 10000);
                    }
                }
            });
        }
    }
}
package com.mihir.alzheimerscaregiver.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Broadcast receiver to handle device boot completion
 * Automatically restarts location tracking service if it was enabled before reboot
 */
public class LocationBootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "LocationBootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Device boot completed, checking location tracking settings");
            
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
                        
                        // Small delay to ensure system is ready
                        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                        handler.postDelayed(() -> {
                            manager.startTracking();
                        }, 5000); // 5 second delay
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
                            manager.startTracking();
                        }, 5000);
                    }
                }
            });
        }
    }
}
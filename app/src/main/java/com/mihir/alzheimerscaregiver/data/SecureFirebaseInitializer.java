package com.mihir.alzheimerscaregiver.data;

import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.mihir.alzheimerscaregiver.BuildConfig;
import com.mihir.alzheimerscaregiver.utils.SecureKeys;

/**
 * Utility class for secure Firebase initialization with API keys from BuildConfig
 * instead of google-services.json
 */
public class SecureFirebaseInitializer {
    private static final String TAG = "SecureFirebaseInit";
    
    /**
     * Initialize Firebase with secure API key
     * @param context Application context
     */
    public static void initialize(Context context) {
        try {
            // Check if Firebase is already initialized
            FirebaseApp.getInstance();
            Log.d(TAG, "Firebase already initialized");
        } catch (IllegalStateException e) {
            // Firebase not initialized yet, initialize it manually with secure API key
            try {
                // Create FirebaseOptions programmatically using the secure API key
                FirebaseOptions options = new FirebaseOptions.Builder()
                        .setApiKey(SecureKeys.getFirebaseApiKey())
                        .setApplicationId("1:1092854188528:android:6581dd1fd9af8deee27535")
                        .setProjectId("recallar-12588")
                        .setStorageBucket("recallar-12588.firebasestorage.app")
                        .build();

                // Initialize with the created options
                FirebaseApp.initializeApp(context, options);
                Log.d(TAG, "Firebase initialized successfully with secure API key");
            } catch (Exception ex) {
                Log.e(TAG, "Failed to initialize Firebase with secure API key", ex);
                
                // Fall back to default initialization
                Log.w(TAG, "Falling back to default Firebase initialization");
                try {
                    FirebaseApp.initializeApp(context);
                    Log.d(TAG, "Firebase initialized with default configuration");
                } catch (Exception fallbackEx) {
                    Log.e(TAG, "Failed to initialize Firebase with default configuration", fallbackEx);
                }
            }
        }
    }
}
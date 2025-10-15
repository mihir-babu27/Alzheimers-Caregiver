package com.mihir.alzheimerscaregiver.debug;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.mihir.alzheimerscaregiver.location.LocationUploader;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase Debug Helper - Test Firebase connectivity and location uploads
 * Use this to debug why location data might not be uploading to Firebase
 */
public class FirebaseDebugHelper {
    
    private static final String TAG = "FirebaseDebugHelper";
    
    /**
     * Test Firebase Realtime Database connectivity
     */
    public static void testFirebaseConnectivity(Context context) {
        Log.d(TAG, "=== FIREBASE CONNECTIVITY TEST ===");
        
        // 1. Check Firebase Auth
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            Log.d(TAG, "✅ Firebase Auth: User logged in - " + auth.getCurrentUser().getUid());
        } else {
            Log.e(TAG, "❌ Firebase Auth: No user logged in!");
            return;
        }
        
        // 2. Test Realtime Database connection
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference testRef = database.getReference("debug_test");
        
        Map<String, Object> testData = new HashMap<>();
        testData.put("timestamp", System.currentTimeMillis());
        testData.put("message", "Firebase connectivity test");
        testData.put("userId", auth.getCurrentUser().getUid());
        
        testRef.setValue(testData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "✅ Firebase Realtime DB: Connection successful!");
                    
                    // Test location upload
                    testLocationUpload();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Firebase Realtime DB: Connection failed - " + e.getMessage());
                });
    }
    
    /**
     * Test LocationUploader functionality
     */
    private static void testLocationUpload() {
        Log.d(TAG, "=== LOCATION UPLOAD TEST ===");
        
        FirebaseAuth auth = FirebaseAuth.getInstance();
        String patientId = auth.getCurrentUser().getUid();
        
        // Create test location
        Location testLocation = new Location("debug");
        testLocation.setLatitude(37.7749); // San Francisco
        testLocation.setLongitude(-122.4194);
        testLocation.setAccuracy(10.0f);
        testLocation.setTime(System.currentTimeMillis());
        
        LocationUploader uploader = new LocationUploader();
        uploader.uploadCurrentLocation(patientId, testLocation, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "✅ LocationUploader: Test upload successful!");
                
                // Test sharing state
                testSharingState(patientId);
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ LocationUploader: Test upload failed - " + error);
            }
        });
    }
    
    /**
     * Test sharing state functionality
     */
    private static void testSharingState(String patientId) {
        Log.d(TAG, "=== SHARING STATE TEST ===");
        
        LocationUploader uploader = new LocationUploader();
        
        // First set sharing enabled
        uploader.updateSharingEnabled(patientId, true, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "✅ Sharing State: Update successful!");
                
                // Now read it back
                uploader.getSharingEnabled(patientId, new LocationUploader.SharingStateCallback() {
                    @Override
                    public void onSharingState(boolean enabled) {
                        Log.d(TAG, "✅ Sharing State: Read successful - enabled: " + enabled);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "❌ Sharing State: Read failed - " + error);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ Sharing State: Update failed - " + error);
            }
        });
    }
    
    /**
     * Check Firebase Database URL and configuration
     */
    public static void checkFirebaseConfig() {
        Log.d(TAG, "=== FIREBASE CONFIG CHECK ===");
        
        try {
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            String url = database.getReference().toString();
            Log.d(TAG, "Firebase Database URL: " + url);
            
            if (url.contains("firebaseio.com")) {
                Log.d(TAG, "✅ Database URL looks correct");
            } else {
                Log.w(TAG, "⚠️ Database URL might be incorrect");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Firebase config error: " + e.getMessage());
        }
    }
    
    /**
     * Run all debug tests
     */
    public static void runAllTests(Context context) {
        Log.d(TAG, "Starting Firebase debug tests...");
        checkFirebaseConfig();
        testFirebaseConnectivity(context);
    }
}
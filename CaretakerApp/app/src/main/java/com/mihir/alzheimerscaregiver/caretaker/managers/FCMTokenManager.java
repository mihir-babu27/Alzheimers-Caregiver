package com.mihir.alzheimerscaregiver.caretaker.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

/**
 * Manager class for handling FCM tokens and caretaker-patient associations
 */
public class FCMTokenManager {
    
    private static final String TAG = "FCMTokenManager";
    private static final String PREF_NAME = "fcm_token_prefs";
    private static final String KEY_FCM_TOKEN = "fcm_token";
    private static final String KEY_CARETAKER_ID = "caretaker_id";
    
    private Context context;
    private DatabaseReference databaseReference;
    private SharedPreferences sharedPreferences;
    
    public FCMTokenManager(Context context) {
        this.context = context;
        this.databaseReference = FirebaseDatabase.getInstance().getReference();
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * Initialize FCM token and register with Firebase
     */
    public void initializeFCMToken(String caretakerId) {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                        return;
                    }
                    
                    // Get new FCM registration token
                    String token = task.getResult();
                    Log.d(TAG, "FCM Token: " + token);
                    
                    // Store token and caretaker ID locally
                    storeTokenLocally(token, caretakerId);
                    
                    // NOTE: We don't call registerTokenWithFirebase() here anymore
                    // The token will be stored in patient_caretaker_tokens path via associateWithPatient()
                    Log.d(TAG, "✅ FCM Token generated and stored locally - ready for patient association");
                });
    }
    
    /**
     * Register FCM token with Firebase for this caretaker
     */
    public void registerTokenWithFirebase(String token, String caretakerId) {
        if (token == null || caretakerId == null) {
            Log.w(TAG, "Cannot register token: token or caretaker ID is null");
            return;
        }
        
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("token", token);
        tokenData.put("caretakerId", caretakerId);
        tokenData.put("deviceType", "android");
        tokenData.put("appType", "caretaker");
        tokenData.put("lastUpdated", System.currentTimeMillis());
        tokenData.put("active", true);
        
        // Store in Firebase under /fcm_tokens/{caretakerId}
        databaseReference.child("fcm_tokens")
                .child("caretakers")
                .child(caretakerId)
                .setValue(tokenData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "FCM token registered successfully for caretaker: " + caretakerId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to register FCM token", e);
                });
    }
    
    /**
     * Associate caretaker FCM token with a patient for notifications
     * CRITICAL: This stores the token in the exact path the Patient app expects!
     */
    public void associateWithPatient(String caretakerId, String patientId) {
        String token = getStoredToken();
        if (token == null) {
            Log.w(TAG, "No FCM token available for association");
            return;
        }
        
        // IMPORTANT: Store in the exact format the Patient app FCMNotificationSender expects
        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("token", token);  // Patient app looks for "token" field
        tokenData.put("active", true);  // Patient app checks "active" field
        tokenData.put("deviceInfo", "CaretakerApp Android");
        tokenData.put("caretakerId", caretakerId);
        tokenData.put("patientId", patientId);
        tokenData.put("registeredAt", System.currentTimeMillis());
        
        // Store in the EXACT path the Patient app FCMNotificationSender queries:
        // patient_caretaker_tokens/{patientId}/{caretakerId}
        databaseReference.child("patient_caretaker_tokens")
                .child(patientId)
                .child(caretakerId)
                .setValue(tokenData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "🎯 FCM TOKEN REGISTERED FOR MISSED MEDICATION ALERTS!");
                    Log.d(TAG, "✅ Patient App can now send notifications to CaretakerApp");
                    Log.d(TAG, "📋 Path: patient_caretaker_tokens/" + patientId + "/" + caretakerId);
                    Log.d(TAG, "🔑 Token: " + token.substring(0, 20) + "...");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Failed to register FCM token for missed medication alerts", e);
                });
    }
    
    /**
     * Remove association between caretaker and patient
     */
    public void removePatientAssociation(String caretakerId, String patientId) {
        databaseReference.child("patient_caretaker_tokens")
                .child(patientId)
                .child(caretakerId)
                .removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Caretaker association removed: " + caretakerId + " -> " + patientId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to remove caretaker association", e);
                });
    }
    
    /**
     * Update FCM token when it refreshes
     */
    public void updateToken(String newToken) {
        String caretakerId = getStoredCaretakerId();
        if (caretakerId != null) {
            storeTokenLocally(newToken, caretakerId);
            registerTokenWithFirebase(newToken, caretakerId);
        } else {
            Log.w(TAG, "Cannot update token: no caretaker ID stored");
        }
    }
    
    /**
     * Store token and caretaker ID locally
     */
    public void storeTokenLocally(String token, String caretakerId) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_FCM_TOKEN, token);
        editor.putString(KEY_CARETAKER_ID, caretakerId);
        editor.apply();
        
        Log.d(TAG, "FCM token stored locally for caretaker: " + caretakerId);
    }
    
    /**
     * Get stored FCM token
     */
    public String getStoredToken() {
        return sharedPreferences.getString(KEY_FCM_TOKEN, null);
    }
    
    /**
     * Get stored caretaker ID
     */
    public String getStoredCaretakerId() {
        return sharedPreferences.getString(KEY_CARETAKER_ID, null);
    }
    
    /**
     * Check if FCM token is registered
     */
    public boolean isTokenRegistered() {
        return getStoredToken() != null && getStoredCaretakerId() != null;
    }
    
    /**
     * Clear stored token data (for logout)
     */
    public void clearTokenData() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY_FCM_TOKEN);
        editor.remove(KEY_CARETAKER_ID);
        editor.apply();
        
        Log.d(TAG, "FCM token data cleared");
    }
}
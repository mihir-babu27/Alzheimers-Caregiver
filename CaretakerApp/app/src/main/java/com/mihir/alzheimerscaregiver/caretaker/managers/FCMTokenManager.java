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
                    
                    // Send token to Firebase
                    registerTokenWithFirebase(token, caretakerId);
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
     */
    public void associateWithPatient(String caretakerId, String patientId) {
        String token = getStoredToken();
        if (token == null) {
            Log.w(TAG, "No FCM token available for association");
            return;
        }
        
        Map<String, Object> associationData = new HashMap<>();
        associationData.put("caretakerId", caretakerId);
        associationData.put("patientId", patientId);
        associationData.put("token", token);
        associationData.put("createdAt", System.currentTimeMillis());
        associationData.put("active", true);
        
        // Store association in Firebase
        databaseReference.child("patient_caretaker_tokens")
                .child(patientId)
                .child(caretakerId)
                .setValue(associationData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Caretaker associated with patient successfully: " + 
                           caretakerId + " -> " + patientId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to associate caretaker with patient", e);
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
    private void storeTokenLocally(String token, String caretakerId) {
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
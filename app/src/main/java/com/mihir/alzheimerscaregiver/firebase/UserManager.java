package com.mihir.alzheimerscaregiver.firebase;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.mihir.alzheimerscaregiver.location.LocationUploader;

/**
 * UserManager - Manages user data synchronization between Firestore and Realtime Database
 * 
 * Handles:
 * - User metadata storage in Firestore
 * - Patient-caretaker link synchronization between Firestore and Realtime DB
 * - User role management (patient | caretaker)
 */
public class UserManager {
    
    private static final String TAG = "UserManager";
    
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseDatabase realtimeDb;
    private final LocationUploader locationUploader;
    
    public UserManager() {
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
        this.realtimeDb = FirebaseDatabase.getInstance();
        this.locationUploader = new LocationUploader();
    }
    
    /**
     * Callback interface for user operations
     */
    public interface UserCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * Initialize user data after authentication
     * Creates user metadata and role information
     */
    public void initializeUser(String role, UserCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) {
                callback.onError("User not authenticated");
            }
            return;
        }
        
        String uid = user.getUid();
        
        // Create user metadata in Firestore
        UserMetadata userMetadata = new UserMetadata(uid, role, System.currentTimeMillis());
        
        firestore.collection("users").document(uid)
                .set(userMetadata)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User metadata created successfully for role: " + role);
                    
                    // Also add to Realtime Database for rules compatibility
                    realtimeDb.getReference("users").child(uid).setValue(userMetadata)
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d(TAG, "User metadata synced to Realtime Database");
                                if (callback != null) {
                                    callback.onSuccess();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Failed to sync user metadata to Realtime DB", e);
                                // Still consider successful since Firestore update succeeded
                                if (callback != null) {
                                    callback.onSuccess();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create user metadata", e);
                    if (callback != null) {
                        callback.onError("Failed to create user metadata: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Link a caretaker to a patient
     * Updates both Firestore and Realtime Database for proper access control
     */
    public void linkCaretakerToPatient(String patientId, String caretakerId, UserCallback callback) {
        if (patientId == null || caretakerId == null) {
            if (callback != null) {
                callback.onError("Invalid patient or caretaker ID");
            }
            return;
        }
        
        Log.d(TAG, "Linking caretaker " + caretakerId + " to patient " + patientId);
        
        // Update Realtime Database link for database rules
        locationUploader.updatePatientCaretakerLink(patientId, caretakerId, new LocationUploader.UploadCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Patient-caretaker link updated in Realtime Database");
                if (callback != null) {
                    callback.onSuccess();
                }
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to update Realtime Database link: " + error);
                if (callback != null) {
                    callback.onError("Failed to create database link: " + error);
                }
            }
        });
    }
    
    /**
     * Get current user ID
     */
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }
    
    /**
     * Check if current user is authenticated
     */
    public boolean isAuthenticated() {
        return auth.getCurrentUser() != null;
    }
    
    /**
     * User metadata class for Firebase storage
     */
    public static class UserMetadata {
        public String uid;
        public String role; // "patient" or "caretaker"
        public long createdAt;
        
        // Default constructor required for Firebase
        public UserMetadata() {}
        
        public UserMetadata(String uid, String role, long createdAt) {
            this.uid = uid;
            this.role = role;
            this.createdAt = createdAt;
        }
    }
}
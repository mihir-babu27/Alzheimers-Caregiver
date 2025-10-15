package com.mihir.alzheimerscaregiver.firebase;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * UserManager - Manages user data for CaretakerApp
 * 
 * Handles:
 * - User metadata storage in Firestore
 * - Caretaker role management
 * - Patient linking operations
 */
public class UserManager {
    
    private static final String TAG = "CaretakerUserManager";
    
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseDatabase realtimeDb;
    
    public UserManager() {
        this.auth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();
        this.realtimeDb = FirebaseDatabase.getInstance();
    }
    
    /**
     * Callback interface for user operations
     */
    public interface UserCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * Initialize caretaker user data after authentication
     */
    public void initializeCaretaker(UserCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) {
                callback.onError("User not authenticated");
            }
            return;
        }
        
        String uid = user.getUid();
        
        // Create caretaker metadata in Firestore
        UserMetadata userMetadata = new UserMetadata(uid, "caretaker", System.currentTimeMillis());
        
        firestore.collection("users").document(uid)
                .set(userMetadata)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Caretaker metadata created successfully");
                    
                    // Also add to Realtime Database for rules compatibility
                    realtimeDb.getReference("users").child(uid).setValue(userMetadata)
                            .addOnSuccessListener(aVoid2 -> {
                                Log.d(TAG, "Caretaker metadata synced to Realtime Database");
                                if (callback != null) {
                                    callback.onSuccess();
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Failed to sync caretaker metadata to Realtime DB", e);
                                // Still consider successful since Firestore update succeeded
                                if (callback != null) {
                                    callback.onSuccess();
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create caretaker metadata", e);
                    if (callback != null) {
                        callback.onError("Failed to create caretaker metadata: " + e.getMessage());
                    }
                });
    }
    
    /**
     * Get current caretaker ID
     */
    public String getCurrentCaretakerId() {
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
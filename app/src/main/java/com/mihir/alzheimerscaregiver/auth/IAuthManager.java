package com.mihir.alzheimerscaregiver.auth;

import com.google.firebase.auth.FirebaseUser;

/**
 * Interface for authentication management to abstract Firebase Auth implementation details.
 */
public interface IAuthManager {
    
    /**
     * Checks if a patient is currently signed in
     * @return true if signed in, false otherwise
     */
    boolean isPatientSignedIn();
    
    /**
     * Gets the current patient ID if signed in
     * @return patient ID or null if not signed in
     */
    String getCurrentPatientId();
    
    /**
     * Gets the current Firebase user
     * @return FirebaseUser object or null if not signed in
     */
    FirebaseUser getCurrentUser();
    
    /**
     * Sign out the current user
     */
    void signOut();
    
    /**
     * Reload the current user's authentication state
     * @param callback Callback to execute after reload completes
     */
    void reloadCurrentUser(OnReloadCompleteCallback callback);
    
    /**
     * Update email in Firestore to match Firebase Auth
     * @param callback Callback to execute after update completes
     */
    void syncEmailWithFirestore(OnFirestoreSyncCallback callback);
    
    /**
     * Interface for reload completion callback
     */
    interface OnReloadCompleteCallback {
        void onComplete(boolean success);
    }
    
    /**
     * Interface for Firestore sync callback
     */
    interface OnFirestoreSyncCallback {
        void onComplete(boolean success);
    }
}
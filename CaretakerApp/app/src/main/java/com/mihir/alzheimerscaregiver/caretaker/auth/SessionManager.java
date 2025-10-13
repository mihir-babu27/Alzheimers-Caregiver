package com.mihir.alzheimerscaregiver.caretaker.auth;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * SessionManager handles user authentication state and session persistence
 * using Firebase Authentication's built-in persistence mechanisms.
 * 
 * This class follows Android and Firebase best practices:
 * - Uses FirebaseAuth's automatic session persistence
 * - No manual token storage in SharedPreferences
 * - Handles authentication state changes gracefully
 * - Provides clean session management APIs
 */
public class SessionManager {
    
    private static final String TAG = "SessionManager";
    
    private final FirebaseAuth firebaseAuth;
    private final Context context;
    
    public SessionManager(Context context) {
        this.context = context.getApplicationContext();
        this.firebaseAuth = FirebaseAuth.getInstance();
    }
    
    /**
     * Check if user is currently authenticated with a valid session
     * @return true if user is authenticated, false otherwise
     */
    public boolean isUserAuthenticated() {
        FirebaseUser currentUser = firebaseAuth.getCurrentUser();
        boolean isAuthenticated = currentUser != null;
        
        Log.d(TAG, "Checking authentication state: " + (isAuthenticated ? "authenticated" : "not authenticated"));
        
        if (isAuthenticated) {
            Log.d(TAG, "Current user: " + currentUser.getEmail() + " (UID: " + currentUser.getUid() + ")");
        }
        
        return isAuthenticated;
    }
    
    /**
     * Get the current authenticated user
     * @return FirebaseUser if authenticated, null otherwise
     */
    @Nullable
    public FirebaseUser getCurrentUser() {
        return firebaseAuth.getCurrentUser();
    }
    
    /**
     * Get the current user's UID
     * @return User UID if authenticated, null otherwise
     */
    @Nullable
    public String getCurrentUserId() {
        FirebaseUser currentUser = getCurrentUser();
        return currentUser != null ? currentUser.getUid() : null;
    }
    
    /**
     * Get the current user's email
     * @return User email if authenticated, null otherwise
     */
    @Nullable
    public String getCurrentUserEmail() {
        FirebaseUser currentUser = getCurrentUser();
        return currentUser != null ? currentUser.getEmail() : null;
    }
    
    /**
     * Sign out the current user and clear all session data
     * This will:
     * 1. Sign out from Firebase (clears Firebase session)
     * 2. Clear any cached user data if needed
     */
    public void signOut() {
        Log.d(TAG, "Signing out user");
        
        FirebaseUser currentUser = getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "Signing out user: " + currentUser.getEmail());
        }
        
        // Sign out from Firebase (this clears the persisted session)
        firebaseAuth.signOut();
        
        // Clear any additional cached data if needed
        clearCachedData();
        
        Log.d(TAG, "User signed out successfully");
    }
    
    /**
     * Validate that the current session is still valid
     * This can be used to check if the Firebase token is still valid
     * @param callback Callback to handle validation result
     */
    public void validateSession(SessionValidationCallback callback) {
        FirebaseUser currentUser = getCurrentUser();
        
        if (currentUser == null) {
            Log.d(TAG, "No current user, session invalid");
            callback.onSessionInvalid();
            return;
        }
        
        // Reload the user to check if the token is still valid
        currentUser.reload()
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Token is still valid
                    Log.d(TAG, "Session validated successfully");
                    callback.onSessionValid(currentUser);
                } else {
                    // Token is invalid or expired
                    Log.w(TAG, "Session validation failed", task.getException());
                    callback.onSessionInvalid();
                }
            });
    }
    
    /**
     * Add an authentication state listener
     * @param listener Listener to be notified of auth state changes
     */
    public void addAuthStateListener(@NonNull FirebaseAuth.AuthStateListener listener) {
        firebaseAuth.addAuthStateListener(listener);
    }
    
    /**
     * Remove an authentication state listener
     * @param listener Listener to be removed
     */
    public void removeAuthStateListener(@NonNull FirebaseAuth.AuthStateListener listener) {
        firebaseAuth.removeAuthStateListener(listener);
    }
    
    /**
     * Clear any cached user data (override if needed for specific app requirements)
     * This method can be extended to clear any app-specific cached data
     */
    protected void clearCachedData() {
        // Clear app-specific cached data here if needed
        // For example, clear SharedPreferences entries that are not authentication tokens
        
        Log.d(TAG, "Cleared cached data");
    }
    
    /**
     * Callback interface for session validation results
     */
    public interface SessionValidationCallback {
        /**
         * Called when the session is valid
         * @param user The current authenticated user
         */
        void onSessionValid(@NonNull FirebaseUser user);
        
        /**
         * Called when the session is invalid or expired
         */
        void onSessionInvalid();
    }
}
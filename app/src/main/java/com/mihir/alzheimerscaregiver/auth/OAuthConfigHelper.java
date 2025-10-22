package com.mihir.alzheimerscaregiver.auth;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

/**
 * Helper class for OAuth configuration and credential management
 * Provides centralized OAuth provider configuration for consistent authentication flows
 */
public class OAuthConfigHelper {
    
    private static final String TAG = "OAuthConfigHelper";
    
    /**
     * Create Google Sign-In options for patient authentication
     * Uses the default web client ID from google-services.json for secure configuration
     */
    public static GoogleSignInOptions createGoogleSignInOptions(Context context) {
        try {
            // The web client ID is automatically generated from google-services.json
            // This approach is more secure than hardcoding client IDs
            String webClientId = context.getString(
                context.getResources().getIdentifier("default_web_client_id", "string", context.getPackageName())
            );
            
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(webClientId)
                    .requestEmail()
                    .build();
                    
            Log.d(TAG, "Google Sign-In options created successfully");
            return gso;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create Google Sign-In options: " + e.getMessage());
            throw new RuntimeException("Failed to configure Google Sign-In", e);
        }
    }
    
    /**
     * Create Firebase Auth credential from Google Sign-In account
     */
    public static AuthCredential createGoogleCredential(String idToken) {
        if (idToken == null || idToken.trim().isEmpty()) {
            throw new IllegalArgumentException("ID token cannot be null or empty");
        }
        
        return GoogleAuthProvider.getCredential(idToken, null);
    }
    
    /**
     * Validate OAuth provider configuration
     */
    public static boolean isOAuthConfigured(Context context) {
        try {
            createGoogleSignInOptions(context);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "OAuth configuration validation failed: " + e.getMessage());
            return false;
        }
    }
}
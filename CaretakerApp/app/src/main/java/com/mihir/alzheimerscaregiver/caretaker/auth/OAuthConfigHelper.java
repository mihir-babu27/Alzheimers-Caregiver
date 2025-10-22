package com.mihir.alzheimerscaregiver.caretaker.auth;

import android.content.Context;

import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

/**
 * OAuthConfigHelper - Configuration helper for OAuth providers
 * 
 * Manages OAuth provider configurations and settings
 */
public class OAuthConfigHelper {
    
    private static final String TAG = "OAuthConfigHelper";
    
    private final Context context;
    
    public OAuthConfigHelper(Context context) {
        this.context = context;
    }
    
    /**
     * Get Google Sign-In configuration
     */
    public GoogleSignInOptions getGoogleSignInOptions() {
        return new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getGoogleWebClientId())
                .requestEmail()
                .build();
    }
    
    /**
     * Get Google Web Client ID from auto-generated resources
     * The Google Services plugin automatically generates this from google-services.json
     */
    private String getGoogleWebClientId() {
        try {
            return context.getString(com.mihir.alzheimerscaregiver.caretaker.R.string.default_web_client_id);
        } catch (Exception e) {
            // This means google-services.json is not properly configured
            return null;
        }
    }
    
    /**
     * Check if Google Sign-In is properly configured
     */
    public boolean isGoogleSignInConfigured() {
        String clientId = getGoogleWebClientId();
        return clientId != null && 
               clientId.endsWith(".apps.googleusercontent.com") &&
               !clientId.contains("example");
    }
    
    /**
     * Get supported OAuth providers
     */
    public String[] getSupportedProviders() {
        return new String[]{"google.com", "password"};
    }
    
    /**
     * Get display name for OAuth provider
     */
    public String getProviderDisplayName(String providerId) {
        switch (providerId) {
            case "google.com":
                return "Google";
            case "password":
                return "Email/Password";
            case "facebook.com":
                return "Facebook";
            case "apple.com":
                return "Apple";
            default:
                return "Unknown Provider";
        }
    }
    
    /**
     * Check if provider requires additional setup
     */
    public boolean requiresSetup(String providerId) {
        switch (providerId) {
            case "google.com":
                return !isGoogleSignInConfigured();
            case "password":
                return false; // Always available
            case "facebook.com":
            case "apple.com":
                return true; // Not implemented yet
            default:
                return true;
        }
    }
}
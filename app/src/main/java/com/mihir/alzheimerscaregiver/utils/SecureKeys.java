package com.mihir.alzheimerscaregiver.utils;

import com.mihir.alzheimerscaregiver.BuildConfig;

/**
 * Helper class to securely access API keys
 * These keys are loaded from secure-keys/api-keys.properties during the build process
 */
public class SecureKeys {
    
    // Private constructor to prevent instantiation
    private SecureKeys() {}
    
    /**
     * Get the Firebase API key
     * @return The Firebase API key
     */
    public static String getFirebaseApiKey() {
        return BuildConfig.GOOGLE_API_KEY;
    }
    
    /**
     * Get the Firebase Project ID
     * @return The Firebase Project ID
     */
    public static String getFirebaseProjectId() {
        return BuildConfig.FIREBASE_PROJECT_ID;
    }
}
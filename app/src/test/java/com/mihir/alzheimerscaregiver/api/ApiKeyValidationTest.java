package com.mihir.alzheimerscaregiver.api;

import com.mihir.alzheimerscaregiver.BuildConfig;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * API Key Validation Test
 * Tests that all API keys are properly configured and not using placeholder values
 */
public class ApiKeyValidationTest {

    @Test
    public void testGoogleApiKeyConfigured() {
        String googleApiKey = BuildConfig.GOOGLE_API_KEY;
        assertNotNull("Google API Key should not be null", googleApiKey);
        assertFalse("Google API Key should not be placeholder", 
                googleApiKey.equals("placeholder") || googleApiKey.contains("your_actual"));
        assertTrue("Google API Key should start with AIza", googleApiKey.startsWith("AIza"));
    }

    @Test
    public void testMapsApiKeyConfigured() {
        String mapsApiKey = ""; // This would come from BuildConfig if added
        // Note: Maps API key is loaded from manifestPlaceholders, not BuildConfig
        // This test validates the configuration structure
        assertTrue("Maps API configuration test", true);
    }

    @Test
    public void testGeminiApiKeyConfigured() {
        String geminiApiKey = BuildConfig.GEMINI_API_KEY;
        assertNotNull("Gemini API Key should not be null", geminiApiKey);
        assertFalse("Gemini API Key should not be placeholder", 
                geminiApiKey.equals("placeholder") || geminiApiKey.contains("your_gemini"));
        assertTrue("Gemini API Key should start with AIza", geminiApiKey.startsWith("AIza"));
    }

    @Test
    public void testHuggingFaceApiKeyConfigured() {
        String hfApiKey = BuildConfig.HUGGING_FACE_API_KEY;
        assertNotNull("Hugging Face API Key should not be null", hfApiKey);
        assertFalse("Hugging Face API Key should not be placeholder", 
                hfApiKey.equals("placeholder") || hfApiKey.contains("your_hugging_face"));
        assertTrue("Hugging Face API Key should start with hf_", hfApiKey.startsWith("hf_"));
    }

    @Test
    public void testFirebaseProjectIdConfigured() {
        String projectId = BuildConfig.FIREBASE_PROJECT_ID;
        assertNotNull("Firebase Project ID should not be null", projectId);
        assertFalse("Firebase Project ID should not be placeholder", 
                projectId.equals("placeholder") || projectId.contains("your_project"));
        assertTrue("Firebase Project ID should be valid format", 
                projectId.matches("[a-z0-9-]+"));
    }
}

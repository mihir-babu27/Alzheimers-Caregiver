package com.mihir.alzheimerscaregiver.utils;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class to manage Text-to-Speech functionality
 * Supports multiple languages and provides callbacks for TTS events
 */
public class TextToSpeechManager implements TextToSpeech.OnInitListener {
    private static final String TAG = "TextToSpeechManager";
    
    private TextToSpeech textToSpeech;
    private Context context;
    private boolean isInitialized = false;
    private TTSCallback callback;
    private String pendingText = null;
    private String pendingLanguageCode = null;
    
    // Language code mapping for supported languages
    private static final Map<String, Locale> LANGUAGE_LOCALES = new HashMap<>();
    static {
        LANGUAGE_LOCALES.put(LanguagePreferenceManager.LANGUAGE_ENGLISH, Locale.ENGLISH);
        LANGUAGE_LOCALES.put(LanguagePreferenceManager.LANGUAGE_HINDI, new Locale("hi", "IN"));
        LANGUAGE_LOCALES.put(LanguagePreferenceManager.LANGUAGE_KANNADA, new Locale("kn", "IN"));
        LANGUAGE_LOCALES.put(LanguagePreferenceManager.LANGUAGE_TAMIL, new Locale("ta", "IN"));
        LANGUAGE_LOCALES.put(LanguagePreferenceManager.LANGUAGE_TELUGU, new Locale("te", "IN"));
        LANGUAGE_LOCALES.put(LanguagePreferenceManager.LANGUAGE_MALAYALAM, new Locale("ml", "IN"));
    }
    
    public interface TTSCallback {
        void onTTSInitialized();
        void onTTSError(String error);
        void onSpeechStart();
        void onSpeechDone();
    }
    
    public TextToSpeechManager(Context context, TTSCallback callback) {
        this.context = context;
        this.callback = callback;
        initializeTTS();
    }
    
    private void initializeTTS() {
        try {
            textToSpeech = new TextToSpeech(context, this);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TTS", e);
            if (callback != null) {
                callback.onTTSError("Failed to initialize Text-to-Speech: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true;
            
            // Set up utterance progress listener
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "TTS started for utterance: " + utteranceId);
                    if (callback != null) {
                        callback.onSpeechStart();
                    }
                }
                
                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "TTS completed for utterance: " + utteranceId);
                    if (callback != null) {
                        callback.onSpeechDone();
                    }
                }
                
                @Override
                public void onError(String utteranceId) {
                    Log.e(TAG, "TTS error for utterance: " + utteranceId);
                    if (callback != null) {
                        callback.onTTSError("Speech synthesis error");
                    }
                }
            });
            
            Log.d(TAG, "TextToSpeech initialized successfully");
            if (callback != null) {
                callback.onTTSInitialized();
            }
            
            // If there was pending text to speak, speak it now
            if (pendingText != null && pendingLanguageCode != null) {
                speak(pendingText, pendingLanguageCode);
                pendingText = null;
                pendingLanguageCode = null;
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status: " + status);
            if (callback != null) {
                callback.onTTSError("Text-to-Speech initialization failed");
            }
        }
    }
    
    /**
     * Speak the given text in the specified language
     * @param text The text to speak
     * @param languageCode The language code (from LanguagePreferenceManager)
     */
    public void speak(String text, String languageCode) {
        if (!isInitialized) {
            // Store text to speak once initialized
            pendingText = text;
            pendingLanguageCode = languageCode;
            Log.d(TAG, "TTS not initialized yet, storing text for later");
            return;
        }
        
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "Cannot speak empty text");
            return;
        }
        
        try {
            // Set language based on the language code
            Locale locale = LANGUAGE_LOCALES.get(languageCode);
            if (locale == null) {
                locale = Locale.ENGLISH; // Fallback to English
                Log.w(TAG, "Unsupported language code: " + languageCode + ", using English");
            }
            
            int languageResult = textToSpeech.setLanguage(locale);
            if (languageResult == TextToSpeech.LANG_MISSING_DATA || 
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language not supported: " + locale + ", trying English");
                textToSpeech.setLanguage(Locale.ENGLISH);
            }
            
            // Configure speech parameters for better readability
            textToSpeech.setSpeechRate(0.8f); // Slightly slower for clarity
            textToSpeech.setPitch(1.0f); // Normal pitch
            
            // Create utterance ID for tracking
            String utteranceId = "story_speech_" + System.currentTimeMillis();
            
            // Speak the text
            int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            
            if (result != TextToSpeech.SUCCESS) {
                Log.e(TAG, "Failed to start speech synthesis");
                if (callback != null) {
                    callback.onTTSError("Failed to start speech");
                }
            } else {
                Log.d(TAG, "Started speaking text in language: " + locale);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error during speech synthesis", e);
            if (callback != null) {
                callback.onTTSError("Speech error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Stop current speech
     */
    public void stop() {
        if (isInitialized && textToSpeech != null) {
            textToSpeech.stop();
            Log.d(TAG, "Speech stopped");
        }
    }
    
    /**
     * Check if TTS is currently speaking
     */
    public boolean isSpeaking() {
        return isInitialized && textToSpeech != null && textToSpeech.isSpeaking();
    }
    
    /**
     * Release TTS resources - call this in onDestroy()
     */
    public void shutdown() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
            isInitialized = false;
            Log.d(TAG, "TextToSpeech resources released");
        }
    }
    
    /**
     * Check if a language is supported by TTS
     */
    public boolean isLanguageSupported(String languageCode) {
        if (!isInitialized || textToSpeech == null) {
            return false;
        }
        
        Locale locale = LANGUAGE_LOCALES.get(languageCode);
        if (locale == null) {
            return false;
        }
        
        int result = textToSpeech.isLanguageAvailable(locale);
        return result == TextToSpeech.LANG_AVAILABLE || 
               result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
               result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
    }
    
    /**
     * Get available TTS engines info for debugging
     */
    public String getTTSEngineInfo() {
        if (!isInitialized || textToSpeech == null) {
            return "TTS not initialized";
        }
        
        return "TTS Default Engine: " + textToSpeech.getDefaultEngine();
    }
}
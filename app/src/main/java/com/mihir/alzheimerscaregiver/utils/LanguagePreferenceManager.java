package com.mihir.alzheimerscaregiver.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Utility class for managing user language preferences for story generation
 */
public class LanguagePreferenceManager {
    
    private static final String PREF_NAME = "user_prefs";
    private static final String KEY_PREFERRED_LANGUAGE = "preferred_story_language";
    
    // Default language
    public static final String DEFAULT_LANGUAGE = "English";
    
    // Supported languages
    public static final String LANGUAGE_ENGLISH = "English";
    public static final String LANGUAGE_KANNADA = "Kannada";
    public static final String LANGUAGE_HINDI = "Hindi";
    public static final String LANGUAGE_TAMIL = "Tamil";
    public static final String LANGUAGE_TELUGU = "Telugu";
    public static final String LANGUAGE_MALAYALAM = "Malayalam";
    
    // Language arrays for easy UI binding
    public static final String[] SUPPORTED_LANGUAGES = {
        LANGUAGE_ENGLISH,
        LANGUAGE_KANNADA,
        LANGUAGE_HINDI,
        LANGUAGE_TAMIL,
        LANGUAGE_TELUGU,
        LANGUAGE_MALAYALAM
    };
    
    /**
     * Get the user's preferred story language
     * @param context Application context
     * @return Preferred language string (defaults to English if not set)
     */
    public static String getPreferredLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PREFERRED_LANGUAGE, DEFAULT_LANGUAGE);
    }
    
    /**
     * Set the user's preferred story language
     * @param context Application context
     * @param language Language to set as preferred
     */
    public static void setPreferredLanguage(Context context, String language) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PREFERRED_LANGUAGE, language).apply();
    }
    
    /**
     * Get the index of a language in the supported languages array
     * @param language Language to find index for
     * @return Index of the language, or 0 (English) if not found
     */
    public static int getLanguageIndex(String language) {
        for (int i = 0; i < SUPPORTED_LANGUAGES.length; i++) {
            if (SUPPORTED_LANGUAGES[i].equals(language)) {
                return i;
            }
        }
        return 0; // Default to English
    }
    
    /**
     * Check if a language is supported
     * @param language Language to check
     * @return true if language is supported, false otherwise
     */
    public static boolean isLanguageSupported(String language) {
        for (String supportedLang : SUPPORTED_LANGUAGES) {
            if (supportedLang.equals(language)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get culturally appropriate instructions for the specified language
     * @param language Target language
     * @return Cultural context instructions for story generation
     */
    public static String getCulturalContext(String language) {
        switch (language) {
            case LANGUAGE_KANNADA:
                return "Include references to Karnataka culture, local festivals like Dasara, Ganesha Chaturthi, traditional foods like Bisi Bele Bath, Mysore Pak, and use warm, respectful Kannada cultural expressions naturally.";
            
            case LANGUAGE_HINDI:
                return "Include references to North Indian culture, festivals like Diwali, Holi, Karva Chauth, traditional foods like Rajma-Chawal, Aloo Paratha, and use respectful Hindi cultural expressions naturally.";
            
            case LANGUAGE_TAMIL:
                return "Include references to Tamil culture, festivals like Pongal, Deepavali, traditional foods like Sambar, Rasam, Idli-Dosa, and use warm Tamil cultural expressions naturally.";
            
            case LANGUAGE_TELUGU:
                return "Include references to Telugu culture, festivals like Ugadi, Sankranti, traditional foods like Pulihora, Pesarattu, and use respectful Telugu cultural expressions naturally.";
            
            case LANGUAGE_MALAYALAM:
                return "Include references to Kerala culture, festivals like Onam, Vishu, traditional foods like Sadhya, Appam-Stew, and use warm Malayalam cultural expressions naturally.";
            
            case LANGUAGE_ENGLISH:
            default:
                return "Use standard English with universal cultural references that are warm and familiar.";
        }
    }
    
    /**
     * Get language-specific greeting or closing phrases for native language stories
     * @param language Target language
     * @return Common phrases and emotional expressions in the target language
     */
    public static String getLanguageSpecificPhrases(String language) {
        switch (language) {
            case LANGUAGE_KANNADA:
                return "Use natural Kannada expressions like 'ನಮಸ್ಕಾರ' (Namaskara) for greetings, 'ಸಂತೋಷದಿಂದ' (Santoshadinda) for happiness, 'ನಮ್ಮ ಊರು' (namma ooru) for our town, and other warm Kannada phrases.";
            
            case LANGUAGE_HINDI:
                return "Use natural Hindi expressions like 'नमस्ते' (Namaste) for greetings, 'खुशी से' (Khushi se) for happiness, 'हमारा गाँव' (hamara gaon) for our village, and other warm Hindi phrases.";
            
            case LANGUAGE_TAMIL:
                return "Use natural Tamil expressions like 'வணக்கம்' (Vanakkam) for greetings, 'மகிழ்ச்சியாக' (Magizhchiyaga) for happiness, 'நம்ம ஊர்' (namma oor) for our place, and other warm Tamil phrases.";
            
            case LANGUAGE_TELUGU:
                return "Use natural Telugu expressions like 'నమస్తే' (Namaste) for greetings, 'సంతోషంగా' (Santoshanga) for happiness, 'మన ఊరు' (mana ooru) for our town, and other warm Telugu phrases.";
            
            case LANGUAGE_MALAYALAM:
                return "Use natural Malayalam expressions like 'നമസ്കാരം' (Namaskaram) for greetings, 'സന്തോഷത്തോടെ' (Santoshatthode) for happiness, 'നമ്മുടെ നാട്' (nammude naad) for our land, and other warm Malayalam phrases.";
            
            case LANGUAGE_ENGLISH:
            default:
                return "Use warm English greetings and expressions that feel familiar and comforting.";
        }
    }
}
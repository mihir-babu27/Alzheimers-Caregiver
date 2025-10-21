package com.mihir.alzheimerscaregiver.reminiscence;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mihir.alzheimerscaregiver.BuildConfig;
import com.mihir.alzheimerscaregiver.utils.LanguagePreferenceManager;


import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Modular class for generating reminiscence stories using Google Gemini API
 * This class handles all Gemini API interactions and can be extended for future features
 * like historical events and location images.
 */
public class GeminiStoryGenerator {
    
    private static final String TAG = "GeminiStoryGenerator";
    // API Configuration - Updated with newer available models
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String[] MODEL_NAMES = {
        "gemini-2.0-flash",      // Latest 2.0 Flash model
        "gemini-2.5-flash",      // 2.5 Flash for better performance
        "gemini-1.5-flash",      // Fallback to 1.5 Flash
        "gemini-pro"             // Original model as final fallback
    };
    private static final String GENERATE_ENDPOINT = ":generateContent?key=";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;
    
    // Track current model index for fallback
    private int currentModelIndex = 0;
    
    public GeminiStoryGenerator() {
        httpClient = new OkHttpClient.Builder()
                .build();
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }
    
    /**
     * Patient details container for story generation
     */
    public static class PatientDetails {
        public final String name;
        public final String birthYear;
        public final String birthplace;
        public final String profession;
        public final String otherDetails;
        
        public PatientDetails(String name, String birthYear, String birthplace, 
                            String profession, String otherDetails) {
            this.name = name;
            this.birthYear = birthYear;
            this.birthplace = birthplace;
            this.profession = profession;
            this.otherDetails = otherDetails;
        }
        
        /**
         * Validates that essential details are present for story generation
         */
        public boolean isValid() {
            return name != null && !name.trim().isEmpty() &&
                   birthYear != null && !birthYear.trim().isEmpty() &&
                   birthplace != null && !birthplace.trim().isEmpty() &&
                   profession != null && !profession.trim().isEmpty();
        }
        
        /**
         * Gets validation error message if details are invalid
         */
        public String getValidationError() {
            if (name == null || name.trim().isEmpty()) {
                return "Patient name is missing";
            }
            if (birthYear == null || birthYear.trim().isEmpty()) {
                return "Birth year is missing";
            }
            if (birthplace == null || birthplace.trim().isEmpty()) {
                return "Birthplace is missing";
            }
            if (profession == null || profession.trim().isEmpty()) {
                return "Profession is missing";
            }
            return null;
        }
    }
    
    /**
     * Callback interface for story generation results
     */
    public interface StoryGenerationCallback {
        void onSuccess(String story);
        void onError(String errorMessage);
    }
    
    /**
     * Generates a reminiscence story based on patient details
     */
    public void generateReminiscenceStory(PatientDetails patientDetails, StoryGenerationCallback callback) {
        generateReminiscenceStory(patientDetails, null, callback);
    }
    
    /**
     * Generates a reminiscence story based on patient details with context
     */
    public void generateReminiscenceStory(PatientDetails patientDetails, android.content.Context context, StoryGenerationCallback callback) {
        // First, populate the memory cache from Firebase before generating the story
        if (context != null) {
            populateMemoryCache(context, () -> {
                // Once memories are cached, proceed with story generation
                generateStoryWithCachedMemories(patientDetails, context, callback);
            });
        } else {
            // Proceed without memory context if no context available
            generateStoryWithCachedMemories(patientDetails, context, callback);
        }
    }
    
    /**
     * Populate memory cache from Firebase conversations
     */
    private void populateMemoryCache(android.content.Context context, Runnable onComplete) {
        try {
            // Get current patient ID from Firebase Auth
            com.google.firebase.auth.FirebaseAuth auth = com.google.firebase.auth.FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.d(TAG, "No authenticated user, proceeding without memory cache");
                onComplete.run();
                return;
            }
            
            String patientId = auth.getCurrentUser().getUid();
            Log.d(TAG, "Populating memory cache for patient: " + patientId);
            
            // Get Firebase Firestore instance
            com.google.firebase.firestore.FirebaseFirestore db = com.google.firebase.firestore.FirebaseFirestore.getInstance();
            
            // Query recent conversations and filter for memories in code to avoid index requirement
            db.collection("patients")
                .document(patientId)
                .collection("conversations")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10) // Get last 10 conversations and filter for memories in code
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        StringBuilder memoriesContext = new StringBuilder();
                        int memoryCount = 0;
                        
                        for (com.google.firebase.firestore.QueryDocumentSnapshot document : task.getResult()) {
                            try {
                                @SuppressWarnings("unchecked")
                                java.util.List<String> detectedMemories = (java.util.List<String>) document.get("detectedMemories");
                                
                                // Only process conversations that have detected memories
                                if (detectedMemories != null && !detectedMemories.isEmpty()) {
                                    for (String memoryText : detectedMemories) {
                                        if (memoryCount >= 5) break; // Limit to 5 memories
                                        
                                        memoriesContext.append("• ").append(memoryText).append("\n");
                                        memoryCount++;
                                    }
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error processing conversation document: " + document.getId(), e);
                            }
                            
                            if (memoryCount >= 5) break;
                        }
                        
                        Log.d(TAG, "Memory cache populated with " + memoryCount + " memories from recent conversations");
                        
                        // Update the cache
                        synchronized (GeminiStoryGenerator.class) {
                            cachedMemoriesContext = memoriesContext.toString();
                        }
                        
                        onComplete.run();
                        
                    } else {
                        Log.w(TAG, "Failed to retrieve memories from Firebase", task.getException());
                        onComplete.run();
                    }
                });
                
        } catch (Exception e) {
            Log.e(TAG, "Error populating memory cache", e);
            onComplete.run();
        }
    }
    
    /**
     * Generate story with cached memories (internal method)
     */
    private void generateStoryWithCachedMemories(PatientDetails patientDetails, android.content.Context context, StoryGenerationCallback callback) {
        // REAL GEMINI API CODE:
        // Validate input
        if (patientDetails == null) {
            callback.onError("Patient details are missing");
            return;
        }
        
        if (!patientDetails.isValid()) {
            callback.onError(patientDetails.getValidationError());
            return;
        }
        
        // Build the prompt with language preference
        String prompt = buildStoryPrompt(patientDetails, context);
        Log.d(TAG, "Generated prompt: " + prompt);
        
        // Execute API call in background thread
        executor.execute(() -> {
            try {
                // Create JSON request body
                JSONObject requestBody = createRequestBody(prompt);
                
                // Create the API request with the latest model
                String url = BASE_URL + MODEL_NAMES[0] + GENERATE_ENDPOINT + BuildConfig.GOOGLE_API_KEY;
                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(requestBody.toString(), JSON))
                        .addHeader("Content-Type", "application/json")
                        .build();
                
                // Make the API call
                httpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "Failed to generate story with " + MODEL_NAMES[0] + ", trying next model", e);
                        // Try next model
                        tryNextModelOrFallback(patientDetails, callback, 1, buildStoryPrompt(patientDetails, context), context);
                    }
                    
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try (Response r = response) {
                            if (!r.isSuccessful()) {
                                String responseBody = r.body() != null ? r.body().string() : "No response body";
                                Log.e(TAG, "API call unsuccessful for " + MODEL_NAMES[0] + ": " + r.code() + " - " + r.message());
                                Log.e(TAG, "Response body: " + responseBody);
                                
                                // Try next model
                                mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, 1, buildStoryPrompt(patientDetails, context), context));
                                return;
                            }
                            
                            String responseBody = r.body().string();
                            String story = parseStoryFromResponse(responseBody);
                            
                            if (story != null && !story.trim().isEmpty()) {
                                Log.d(TAG, "Story generated successfully");
                                mainHandler.post(() -> callback.onSuccess(story.trim()));
                            } else {
                                Log.w(TAG, "Generated story is empty");
                                mainHandler.post(() -> callback.onError("Story generation returned empty content. Please try again in a moment."));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing response", e);
                            mainHandler.post(() -> callback.onError("There was an issue processing the story. Please try again."));
                        }
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating request", e);
                mainHandler.post(() -> callback.onError("Unable to prepare story request. Please check patient information and try again."));
            }
        });
        // END OF REAL API CODE */
    }

    /**
     * Tries the next available model or shows appropriate error if all models fail
     */
    private void tryNextModelOrFallback(PatientDetails patientDetails, StoryGenerationCallback callback, 
                                      int modelIndex, String prompt, android.content.Context context) {
        if (modelIndex >= MODEL_NAMES.length) {
            Log.e(TAG, "All API models failed");
            mainHandler.post(() -> callback.onError("Story generation is temporarily unavailable. Please check your internet connection and try again in a few minutes."));
            return;
        }

        String currentModel = MODEL_NAMES[modelIndex];
        Log.d(TAG, "Trying model: " + currentModel + " (attempt " + (modelIndex + 1) + "/" + MODEL_NAMES.length + ")");

        try {
            JSONObject requestBody = createRequestBody(prompt);
            String url = BASE_URL + currentModel + GENERATE_ENDPOINT + BuildConfig.GOOGLE_API_KEY;
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .addHeader("Content-Type", "application/json")
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Failed to generate story with " + currentModel, e);
                    mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, modelIndex + 1, prompt, context));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (Response r = response) {
                        if (!r.isSuccessful()) {
                            String responseBody = r.body() != null ? r.body().string() : "No response body";
                            Log.e(TAG, "API call unsuccessful for " + currentModel + ": " + r.code() + " - " + r.message());
                            
                            // Try next model
                            mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, modelIndex + 1, prompt, context));
                            return;
                        }

                        String responseBody = r.body().string();
                        String story = parseStoryFromResponse(responseBody);
                        
                        if (story == null || story.trim().isEmpty()) {
                            Log.w(TAG, "Generated story is empty for " + currentModel);
                            mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, modelIndex + 1, prompt, context));
                            return;
                        }
                        
                        Log.d(TAG, "Successfully generated story with model: " + currentModel);
                        mainHandler.post(() -> callback.onSuccess(story));
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing response for " + currentModel, e);
                        mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, modelIndex + 1, prompt, context));
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating request for " + currentModel, e);
            mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, modelIndex + 1, prompt, context));
        }
    }

    
    /**
     * Creates the JSON request body for the Gemini API
     */
    private JSONObject createRequestBody(String prompt) throws JSONException {
        JSONObject requestBody = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();
        
        part.put("text", prompt);
        parts.put(part);
        content.put("parts", parts);
        contents.put(content);
        requestBody.put("contents", contents);
        
        return requestBody;
    }
    
    /**
     * Parses the story from the Gemini API response
     */
    private String parseStoryFromResponse(String responseBody) {
        try {
            JSONObject response = new JSONObject(responseBody);
            JSONArray candidates = response.getJSONArray("candidates");
            
            if (candidates.length() > 0) {
                JSONObject candidate = candidates.getJSONObject(0);
                JSONObject content = candidate.getJSONObject("content");
                JSONArray parts = content.getJSONArray("parts");
                
                if (parts.length() > 0) {
                    JSONObject part = parts.getJSONObject(0);
                    return part.getString("text");
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing response", e);
        }
        return null;
    }
    
    /**
     * Builds enhanced, detailed prompts for safe therapeutic reminiscence story generation
     * Provides specific instructions for creating therapeutic, personalized stories inspired by life details
     * @param details Patient details for story context
     * @param context Android context to access language preferences (can be null for backward compatibility)
     */
    private String buildStoryPrompt(PatientDetails details, android.content.Context context) {
        // Get user's preferred language for story generation
        String preferredLanguage = com.mihir.alzheimerscaregiver.utils.LanguagePreferenceManager.DEFAULT_LANGUAGE;
        if (context != null) {
            preferredLanguage = com.mihir.alzheimerscaregiver.utils.LanguagePreferenceManager.getPreferredLanguage(context);
        }
        
        // Get extracted memories for personalization
        String extractedMemoriesContext = "";
        if (context != null) {
            extractedMemoriesContext = getExtractedMemoriesForStory(context);
        }
        // Therapeutically safe story themes focused on sensory memories and emotional comfort
        String[] storyThemes = {
            // Theme 1: Community and relationships
            "Write a gentle reminiscence story inspired by life in %s as a %s. " +
            "The story should feel comforting, familiar, and emotionally supportive, but should not claim to be a factual record or biography. " +
            "Focus on evoking warm memories of community connections - the sounds of friendly conversations with neighbors, " +
            "the welcoming smells of local shops, the comfortable feelings of belonging at community gatherings. " +
            "Emphasize sensory details like warm greetings, familiar faces, and the gentle rhythm of community life in %s.",
            
            // Theme 2: Daily life and cherished routines
            "Write a gentle reminiscence story inspired by the daily rhythms that someone might have experienced in %s while working as a %s. " +
            "The story should feel comforting and familiar but not claim to be factual. Focus on evoking warm, sensory-rich memories: " +
            "the aroma of morning coffee, the gentle sounds of a neighborhood awakening, the satisfaction of familiar routines. " +
            "Paint pictures of cozy morning rituals, peaceful walks through %s, and the simple pleasures that bring comfort and stability.",
            
            // Theme 3: Seasonal memories and natural beauty  
            "Craft a peaceful reminiscence story inspired by the natural beauty someone in %s might have experienced while working as a %s. " +
            "The story should evoke warm, sensory memories but not claim to be biographical. Focus on gentle seasonal moments: " +
            "the soft colors of spring flowers in local parks, the warm breeze of summer evenings, the golden hues of autumn streets, " +
            "the cozy comfort of winter gatherings. Emphasize how nature's beauty in %s could provide comfort, peace, and moments of quiet joy.",
            
            // Theme 4: Professional pride and meaningful work
            "Write a gentle reminiscence story inspired by the satisfaction someone might have found working as a %s in %s. " +
            "The story should feel emotionally supportive but not claim to be factual. Focus on evoking the warm feelings of meaningful work: " +
            "the satisfaction of helping others, the comfort of workplace friendships, the pride in developing skills over time. " +
            "Emphasize sensory memories like the sounds of a busy workplace, the feeling of accomplishment, and the respect earned through dedication.",
            
            // Theme 5: Cultural celebrations and traditions
            "Create a gentle reminiscence story inspired by the cultural richness someone in %s might have experienced while working as a %s. " +
            "The story should evoke warm, nostalgic feelings but not claim to be biographical. Focus on sensory memories of celebrations: " +
            "the aromas of traditional foods, the sounds of music and laughter, the colorful sights of local festivals. " +
            "Paint pictures of community gatherings, seasonal traditions, and the comfort found in shared cultural experiences in %s.",
            
            // Theme 6: Generational connections and wisdom sharing
            "Write a gentle reminiscence story inspired by the wisdom and connections someone might have shared in %s as a %s. " +
            "The story should feel emotionally supportive but not claim to be factual. Focus on evoking warm memories of mentorship: " +
            "the satisfaction of teaching others, the comfort of sharing life lessons, the joy of watching others grow. " +
            "Emphasize the gentle moments of guidance, the warm feelings of being valued for wisdom, and the connections across generations.",
            
            // Theme 7: Family connections and home life
            "Create a gentle reminiscence story inspired by the warmth of home that someone might have experienced in %s while working as a %s. " +
            "The story should feel comforting and familiar but not claim to be biographical. Focus on sensory memories of home: " +
            "the aromas of favorite meals, the sounds of family conversations, the comfort of familiar spaces. " +
            "Paint pictures of cozy gatherings, the warmth of shared traditions, and the security found in a loving home environment.",
            
            // Theme 8: Personal growth and life journey
            "Write a gentle reminiscence story inspired by the personal growth someone might have experienced while living in %s and working as a %s. " +
            "The story should feel emotionally supportive but not claim to be factual. Focus on evoking warm feelings of personal development: " +
            "the satisfaction of learning new skills, the comfort of overcoming gentle challenges, the pride in personal achievements. " +
            "Emphasize the positive emotions of resilience, the warmth of self-discovery, and the peaceful wisdom gained through life's experiences."
        };
        
        // Randomly select a story theme for variety
        java.util.Random random = new java.util.Random();
        int themeIndex = random.nextInt(storyThemes.length);
        String selectedTheme = storyThemes[themeIndex];
        
        // Build the comprehensive prompt
        StringBuilder prompt = new StringBuilder();
        
        // Add therapeutic safety context and instructions
        prompt.append("CRITICAL: This is for dementia/Alzheimer's reminiscence therapy. You must create a story about a FICTIONAL CHARACTER in THIRD PERSON that is INSPIRED BY life details, not a factual biography about the patient. ");
        prompt.append("The story should feel comforting, familiar, and emotionally supportive while being therapeutically safe. Create a relatable fictional character whose experiences mirror the provided life details. ");
        
        // Add the selected theme with patient details (excluding name for privacy)
        prompt.append(String.format(selectedTheme, 
            details.birthplace, details.profession,
            details.birthplace));
        
        // Add additional context if provided - emphasize personal details
        if (details.otherDetails != null && !details.otherDetails.trim().isEmpty()) {
            prompt.append("\n\nPERSONAL CONTEXT (Very Important - weave these details throughout the story): ")
                  .append(details.otherDetails)
                  .append(" - These personal details should be central to creating authentic, meaningful memories in the story.");
        }
        
        // Add detailed therapeutic writing guidelines
        prompt.append("\n\nTHERAPEUTIC WRITING GUIDELINES:\n");
        prompt.append("• NARRATIVE STYLE: Write in THIRD PERSON about a FICTIONAL CHARACTER (use 'he', 'she', or character name)\n");
        prompt.append("• CHARACTER: Create a relatable fictional person whose life experiences are INSPIRED BY the provided details\n");
        prompt.append("• SAFETY: This is NOT a factual record or biography about the patient - it's fiction inspired by life details\n");
        prompt.append("• Length: 4-5 sentences (approximately 80-120 words)\n");
        
        // Add language-specific instructions
        if (preferredLanguage.equals(LanguagePreferenceManager.LANGUAGE_ENGLISH)) {
            prompt.append("• Language: Simple, clear English suitable for elderly patients\n");
        } else {
            prompt.append("• Language: Write the ENTIRE story in ").append(preferredLanguage).append(" using simple, clear language suitable for elderly patients\n");
            prompt.append("• Script: Use the native script of ").append(preferredLanguage).append(" (e.g., Devanagari for Hindi, Kannada script for Kannada, Tamil script for Tamil, etc.)\n");
            prompt.append("• Cultural Context: ").append(LanguagePreferenceManager.getCulturalContext(preferredLanguage)).append("\n");
            prompt.append("• Natural Expressions: ").append(LanguagePreferenceManager.getLanguageSpecificPhrases(preferredLanguage)).append("\n");
            prompt.append("• IMPORTANT: The story must be written completely in ").append(preferredLanguage).append(", not in English with ").append(preferredLanguage).append(" words mixed in\n");
        }
        
        prompt.append("• Tone: Warm, gentle, positive, and reassuring - avoid any dramatic or stressful content\n");
        prompt.append("• Focus: Emphasize sensory details (sounds, smells, colors, feelings), nostalgia, and emotional comfort\n");
        prompt.append("• Avoid: Factual claims, specific events, sad topics, loss, conflict, or anything distressing\n");
        prompt.append("• Include: Gentle sensory memories, warm emotions, peaceful scenarios, and comforting imagery\n");
        prompt.append("• Emotion: Create feelings of warmth, belonging, pride, and comfort through gentle reminiscence\n");
        prompt.append("• Pacing: Use gentle, non-judgmental phrasing with supportive and calming language\n");
        
        // Add extracted memories for personalization
        if (!extractedMemoriesContext.isEmpty()) {
            prompt.append("\n🧠 PERSONALIZATION FROM RECENT CONVERSATIONS:\n");
            prompt.append("The following memories were mentioned during recent chats. You may gently weave SIMILAR themes, people, places, or experiences into the story to make it more personally meaningful, but do NOT use these as direct facts:\n");
            prompt.append(extractedMemoriesContext);
            prompt.append("\nIMPORTANT: Use these as INSPIRATION only - create fictional scenarios that echo these themes while maintaining therapeutic safety.\n");
        }
        
        // Add historical context if birth year is available
        if (details.birthYear != null && !details.birthYear.trim().isEmpty()) {
            int currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            int birthYear = Integer.parseInt(details.birthYear);
            // int currentAge = currentYear - birthYear; // Future use for age-appropriate context
            
            prompt.append("\nHISTORICAL CONTEXT: ");
            prompt.append("This person was born in ").append(details.birthYear);
            if (birthYear < 1950) {
                prompt.append(", so include references to mid-20th century life, traditional values, and simpler times.");
            } else if (birthYear < 1970) {
                prompt.append(", so include references to post-war prosperity, community growth, and social connections.");
            }
        }
        
        // Final therapeutic safety instructions with language emphasis
        prompt.append("\n\nFINAL INSTRUCTIONS: ");
        if (!preferredLanguage.equals(LanguagePreferenceManager.LANGUAGE_ENGLISH)) {
            prompt.append("Write the COMPLETE story in ").append(preferredLanguage).append(" language using native script. ");
        }
        prompt.append("Write a gentle reminiscence story INSPIRED BY life details from ");
        prompt.append(details.birthplace).append(", but do NOT claim it as factual biography. ");
        prompt.append("The story should evoke warm, familiar feelings and sensory memories that feel emotionally supportive. ");
        prompt.append("Focus on creating therapeutic comfort through gentle nostalgia, peaceful imagery, and positive emotions. ");
        prompt.append("This is therapeutic reminiscence, not historical documentation - prioritize emotional safety and comfort above all else.");
        
        return prompt.toString();
    }
    
    /**
     * Backward compatibility method for existing code
     */
    private String buildStoryPrompt(PatientDetails details) {
        return buildStoryPrompt(details, null);
    }
    
    /**
     * Future extension point: Generate stories with historical events
     * This method is prepared for future enhancement
     */
    public void generateStoryWithHistoricalEvents(PatientDetails patientDetails, 
                                                 String historicalEvents, 
                                                 StoryGenerationCallback callback) {
        // TODO: Implement in future version
        // This will incorporate historical events from the patient's era
        Log.d(TAG, "Historical events feature not yet implemented");
        generateReminiscenceStory(patientDetails, callback);
    }
    
    /**
     * Future extension point: Generate stories with location images
     * This method is prepared for future enhancement
     */
    public void generateStoryWithLocationContext(PatientDetails patientDetails, 
                                               StoryGenerationCallback callback) {
        // TODO: Implement in future version
        // This will incorporate location-specific imagery and context
        Log.d(TAG, "Location context feature not yet implemented");
        generateReminiscenceStory(patientDetails, callback);
    }
    
    /**
     * Get extracted memories from recent conversations for story personalization
     */
    private String getExtractedMemoriesForStory(Context context) {
        synchronized (GeminiStoryGenerator.class) {
            if (cachedMemoriesContext != null && !cachedMemoriesContext.isEmpty()) {
                Log.d(TAG, "Using cached memories for story personalization");
                return cachedMemoriesContext;
            } else {
                Log.d(TAG, "No cached memories available for story personalization");
                return "";
            }
        }
    }
    
    // Static cache for memories context
    private static String cachedMemoriesContext = "";
    
    /**
     * Mark memories as used in story generation to avoid repetition
     */
    public void markMemoriesAsUsedInStory(Context context, java.util.List<String> usedMemoryTexts) {
        if (context == null || usedMemoryTexts == null || usedMemoryTexts.isEmpty()) {
            return;
        }
        
        try {
            // Use ConversationRepository to mark memories as used
            com.mihir.alzheimerscaregiver.repository.ConversationRepository repository = 
                new com.mihir.alzheimerscaregiver.repository.ConversationRepository();
            
            // For each used memory text, find and mark corresponding Firebase memory as used
            for (String memoryText : usedMemoryTexts) {
                // This would need patient ID to work properly with Firebase
                Log.d(TAG, "Marking memory as used in story: " + memoryText.substring(0, Math.min(50, memoryText.length())));
                
                // Note: In a full implementation, we would:
                // 1. Search for memories by text content
                // 2. Update their usedInStory flag to true  
                // 3. Set lastUsedInStory timestamp
                // This requires patient ID which isn't available in this context
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error marking memories as used in story", e);
        }
    }
}
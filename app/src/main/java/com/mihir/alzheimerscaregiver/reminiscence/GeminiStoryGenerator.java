package com.mihir.alzheimerscaregiver.reminiscence;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mihir.alzheimerscaregiver.BuildConfig;

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
        
        // Real Gemini API is now enabled with your API key!
        // Comment out the mock generator lines below if you want to test with mock stories
        
        // generateMockStory(patientDetails, callback);
        // return;
        
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
        
        // Build the prompt
        String prompt = buildStoryPrompt(patientDetails);
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
                        // Try next model or fallback to mock story
                        tryNextModelOrFallback(patientDetails, callback, 1, buildStoryPrompt(patientDetails));
                    }
                    
                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try (Response r = response) {
                            if (!r.isSuccessful()) {
                                String responseBody = r.body() != null ? r.body().string() : "No response body";
                                Log.e(TAG, "API call unsuccessful for " + MODEL_NAMES[0] + ": " + r.code() + " - " + r.message());
                                Log.e(TAG, "Response body: " + responseBody);
                                
                                // Try next model or fallback to mock story
                                mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, 1, buildStoryPrompt(patientDetails)));
                                return;
                            }
                            
                            String responseBody = r.body().string();
                            String story = parseStoryFromResponse(responseBody);
                            
                            if (story != null && !story.trim().isEmpty()) {
                                Log.d(TAG, "Story generated successfully");
                                mainHandler.post(() -> callback.onSuccess(story.trim()));
                            } else {
                                Log.w(TAG, "Generated story is empty");
                                mainHandler.post(() -> callback.onError("Generated story is empty. Please try again."));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing response", e);
                            mainHandler.post(() -> callback.onError("Error processing generated story"));
                        }
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error creating request", e);
                mainHandler.post(() -> callback.onError("Error creating request"));
            }
        });
        // END OF REAL API CODE */
    }

    /**
     * Tries the next available model or falls back to mock story generation
     */
    private void tryNextModelOrFallback(PatientDetails patientDetails, StoryGenerationCallback callback, 
                                      int modelIndex, String prompt) {
        if (modelIndex >= MODEL_NAMES.length) {
            Log.w(TAG, "All API models failed, falling back to mock story generation");
            generateMockStory(patientDetails, callback);
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
                    mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, modelIndex + 1, prompt));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (Response r = response) {
                        if (!r.isSuccessful()) {
                            String responseBody = r.body() != null ? r.body().string() : "No response body";
                            Log.e(TAG, "API call unsuccessful for " + currentModel + ": " + r.code() + " - " + r.message());
                            
                            // Try next model
                            mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, modelIndex + 1, prompt));
                            return;
                        }

                        String responseBody = r.body().string();
                        String story = parseStoryFromResponse(responseBody);
                        
                        if (story == null || story.trim().isEmpty()) {
                            Log.w(TAG, "Generated story is empty for " + currentModel);
                            mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, modelIndex + 1, prompt));
                            return;
                        }
                        
                        Log.d(TAG, "Successfully generated story with model: " + currentModel);
                        mainHandler.post(() -> callback.onSuccess(story));
                        
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing response for " + currentModel, e);
                        mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, modelIndex + 1, prompt));
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating request for " + currentModel, e);
            mainHandler.post(() -> tryNextModelOrFallback(patientDetails, callback, modelIndex + 1, prompt));
        }
    }
    
    /**
     * Temporary diverse story generator for testing UI functionality
     * Remove this method once the Gemini API is properly configured
     */
    private void generateMockStory(PatientDetails patientDetails, StoryGenerationCallback callback) {
        // Simulate API delay
        mainHandler.postDelayed(() -> {
            String mockStory = generateDiverseStory(patientDetails);
            
            Log.d(TAG, "Generated diverse mock story successfully");
            callback.onSuccess(mockStory);
        }, 2000); // 2 second delay to simulate API call
    }
    
    /**
     * Generates diverse, warm stories focusing on different aspects of the patient's life and birthplace
     */
    private String generateDiverseStory(PatientDetails patientDetails) {
        // Array of different story themes and templates
        String[] storyTemplates = {
            // Theme 1: Community and Workplace
            "The people of %s still remember %s with great fondness. As a dedicated %s, %s brought joy and reliability to everyone they worked with. " +
            "In %s, neighbors would often say that %s had a special way of making even the busiest days feel peaceful. " +
            "The community gatherings were always brighter when %s was there, sharing stories and laughter that echoed through the streets of %s.",
            
            // Theme 2: Seasonal memories and local traditions
            "When spring arrived in %s, %s would always be among the first to notice the changes in the town. " +
            "Working as a %s since %s, %s became part of the rhythm of the seasons that made %s so special. " +
            "Local festivals and celebrations were never quite the same without %s's warm presence and the traditions they helped keep alive.",
            
            // Theme 3: Daily life and simple pleasures
            "Every morning in %s held a special magic, and %s knew exactly where to find it. " +
            "The local cafés and markets became familiar places where %s, known for their work as a %s, would exchange warm greetings with friends. " +
            "These simple moments of connection made %s not just a place on the map, but a true home filled with cherished memories.",
            
            // Theme 4: Legacy and impact on place
            "The streets of %s tell many stories, but few as heartwarming as %s's journey there. " +
            "From %s onward, %s's work as a %s became woven into the very fabric of the community. " +
            "Visitors to %s today still hear tales of kindness and dedication that %s left behind, a beautiful legacy that continues to inspire.",
            
            // Theme 5: Natural beauty and connection to place
            "In %s, the natural beauty seemed to dance with %s's gentle spirit. " +
            "Whether working as a %s or simply enjoying quiet moments, %s found peace in the landscapes that surrounded their beloved hometown. " +
            "The sunsets over %s still carry whispers of the joy and contentment that %s brought to everyone they met.",
            
            // Theme 6: Friendship and community bonds
            "The friendships that bloomed in %s were like flowers in %s's garden - carefully tended and full of life. " +
            "As a respected %s, %s became the person others turned to for both wisdom and laughter. " +
            "The coffee shops and gathering places of %s still echo with the warmth of conversations that %s shared with so many dear friends.",
            
            // Theme 7: Generational connections
            "Stories passed down through families in %s often mention %s with a smile. " +
            "Working diligently as a %s from %s, %s became a bridge between generations, sharing wisdom and creating bonds that lasted lifetimes. " +
            "Children who grew up in %s remember %s's encouraging words and the way they made everyone feel valued and heard.",
            
            // Theme 8: Cultural and local heritage
            "The cultural tapestry of %s was made richer by %s's presence and contributions. " +
            "As a dedicated %s, %s helped preserve the unique character that made %s such a special place to call home. " +
            "Local traditions and celebrations gained extra meaning through %s's participation and the joy they brought to community gatherings."
        };
        
        // Randomly select a story template
        java.util.Random random = new java.util.Random();
        int templateIndex = random.nextInt(storyTemplates.length);
        String selectedTemplate = storyTemplates[templateIndex];
        
        // Fill in the template with patient details
        return String.format(selectedTemplate,
            patientDetails.birthplace, patientDetails.name, patientDetails.profession,
            patientDetails.name, patientDetails.birthplace, patientDetails.name,
            patientDetails.name, patientDetails.birthplace, patientDetails.birthYear,
            patientDetails.name, patientDetails.birthplace, patientDetails.name,
            patientDetails.name, patientDetails.birthplace, patientDetails.name,
            patientDetails.profession, patientDetails.birthplace, patientDetails.name
        );
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
     * Builds diverse prompts for story generation focusing on different themes
     * This method can be extended in the future to include historical events or other context
     */
    private String buildStoryPrompt(PatientDetails details) {
        // Array of different story themes to create variety
        String[] storyThemes = {
            // Theme 1: Community and relationships
            "Generate a warm, positive story (3-4 sentences) about the community connections of %s, who lived in %s and worked as a %s. " +
            "Focus on friendships, neighbors, and the bonds they formed in %s. Show how %s brought people together and created lasting relationships.",
            
            // Theme 2: Daily life and local culture
            "Create a nostalgic story (3-4 sentences) about daily life in %s through the eyes of %s, a local %s. " +
            "Describe the sights, sounds, and traditions that made %s special. Focus on simple pleasures and the unique character of the town.",
            
            // Theme 3: Seasonal and natural beauty
            "Write a peaceful story (3-4 sentences) about how %s experienced the natural beauty and changing seasons in %s while working as a %s. " +
            "Paint a picture of the landscapes, weather, and outdoor life that made %s a wonderful place to live.",
            
            // Theme 4: Professional life and community service
            "Tell an inspiring story (3-4 sentences) about %s's meaningful work as a %s in %s, and how it impacted the local community. " +
            "Show the pride, dedication, and positive difference %s made in people's lives through their profession.",
            
            // Theme 5: Local traditions and celebrations
            "Describe a joyful story (3-4 sentences) about local celebrations, festivals, or traditions in %s that %s participated in as a respected %s. " +
            "Focus on community gatherings, shared meals, music, or cultural events that brought %s together.",
            
            // Theme 6: Generational wisdom and mentorship
            "Create a heartwarming story (3-4 sentences) about how %s, working as a %s in %s, shared wisdom and guidance with younger generations. " +
            "Show the lasting impact of their kindness and the way they helped others grow and learn in %s."
        };
        
        // Randomly select a story theme
        java.util.Random random = new java.util.Random();
        int themeIndex = random.nextInt(storyThemes.length);
        String selectedTheme = storyThemes[themeIndex];
        
        // Build the complete prompt
        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format(selectedTheme, 
            details.name, details.birthplace, details.profession,
            details.birthplace, details.name, details.name,
            details.birthplace, details.name));
        
        prompt.append(" Write it in plain, simple English that elderly patients can easily understand. ");
        prompt.append("Keep the tone warm, positive, and encouraging. ");
        
        // Add birth year context if available
        if (details.birthYear != null && !details.birthYear.trim().isEmpty()) {
            prompt.append("Consider that ").append(details.name)
                  .append(" was born in ").append(details.birthYear).append(". ");
        }
        
        // Add other details if available
        if (details.otherDetails != null && !details.otherDetails.trim().isEmpty()) {
            prompt.append("Additional context: ")
                  .append(details.otherDetails)
                  .append(". ");
        }
        
        prompt.append("Make the story unique and avoid repetitive phrases from previous stories.");
        
        return prompt.toString();
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
}
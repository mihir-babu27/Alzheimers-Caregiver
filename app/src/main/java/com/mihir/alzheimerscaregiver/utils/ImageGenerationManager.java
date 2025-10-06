package com.mihir.alzheimerscaregiver.utils;

import android.content.Context;
import android.util.Log;
import com.mihir.alzheimerscaregiver.data.model.PatientProfile;
import com.mihir.alzheimerscaregiver.BuildConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Enhanced therapeutic image generation using FLUX.1-dev model
 * 🚀 HIGH-QUALITY: Premium model with 1024x1024 resolution
 * 🎯 STORY-BASED: Uses actual story content as scene context 
 * 🎨 THERAPEUTIC: Safe, culturally-aware scenes for Alzheimer's care
 * 
 * FLUX.1-dev generates high-quality therapeutic images in 15-45 seconds
 * Now supports story-based scene generation for contextual relevance
 * 
 * Usage:
 * ImageGenerationManager imageGen = new ImageGenerationManager(context);
 * SceneImageParams params = new SceneImageParams("photorealistic digital painting", "serene and therapeutic");
 * 
 * // Generate based on story content
 * imageGen.generateSceneImage(patientProfile, storyContent, params, callback);
 * 
 * // Generate based on profile only  
 * imageGen.generateSceneImage(patientProfile, params, callback);
 */
public class ImageGenerationManager {
    private static final String TAG = "ImageGenerationManager";
    
    // FLUX.1-dev API Configuration
    private static final String IMAGE_API_URL = "https://api-inference.huggingface.co/models/black-forest-labs/FLUX.1-dev";
    private static final String API_KEY = BuildConfig.HUGGING_FACE_API_KEY;
    
    private final Context context;
    private final ExecutorService executorService;
    private final OkHttpClient httpClient;
    private final File cacheDir;
    
    public interface ImageGenerationCallback {
        void onImageGenerated(String imagePath, String description);
        void onImageGenerationFailed(String error);
    }
    
    /**
     * Configuration parameters for FLUX.1-dev image generation
     */
    public static class SceneImageParams {
        public String style = "photorealistic digital painting";
        public String mood = "serene and therapeutic";
        public int inferenceSteps = 20;    // FLUX.1-dev optimal
        public double guidanceScale = 3.5;  // FLUX.1-dev optimal
        public int width = 1024;            // FLUX.1-dev native resolution
        public int height = 1024;           // FLUX.1-dev native resolution
        public boolean enableCaching = true;
        
        public SceneImageParams() {}
        
        public SceneImageParams(String style, String mood) {
            this.style = style;
            this.mood = mood;
        }
        
        public SceneImageParams(String style, String mood, int width, int height) {
            this.style = style;
            this.mood = mood;
            this.width = width;
            this.height = height;
        }
    }
    
    public ImageGenerationManager(Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(180, TimeUnit.SECONDS)   // FLUX.1-dev higher quality takes longer
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.cacheDir = new File(context.getCacheDir(), "generated_images");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }
    
    /**
     * Generates actual illustration scenes based on patient profile (backward compatibility)
     * @param patientProfile Patient's profile with personal details
     * @param params Image generation parameters (style, mood, dimensions)
     * @param callback Callback for success/failure handling
     */
    public void generateSceneImage(PatientProfile patientProfile, SceneImageParams params, ImageGenerationCallback callback) {
        generateSceneImage(patientProfile, null, params, callback);
    }
    
    /**
     * Generates actual illustration scenes based on patient profile and story content
     * @param patientProfile Patient's profile with personal details
     * @param storyContent The generated story content to use as scene context
     * @param params Image generation parameters (style, mood, dimensions)
     * @param callback Callback for success/failure handling
     */
    public void generateSceneImage(PatientProfile patientProfile, String storyContent, SceneImageParams params, ImageGenerationCallback callback) {
        if (patientProfile == null) {
            callback.onImageGenerationFailed("Patient profile is required");
            return;
        }
        
        if (params == null) {
            params = new SceneImageParams();
        }
        
        final SceneImageParams finalParams = params;
        
        executorService.execute(() -> {
            try {
                // Check cache first if enabled
                if (finalParams.enableCaching) {
                    String cachedImagePath = getCachedImagePath(patientProfile, storyContent, finalParams);
                    if (cachedImagePath != null) {
                        String description = generateDetailedSceneDescription(patientProfile, storyContent);
                        callback.onImageGenerated(cachedImagePath, description);
                        return;
                    }
                }
                
                // Generate image using FLUX.1-dev API
                generateImageFromAPI(patientProfile, storyContent, finalParams, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "Error in generateSceneImage", e);
                callback.onImageGenerationFailed("Error generating scene: " + e.getMessage());
            }
        });
    }
    
    /**
     * Generate image using FLUX.1-dev API with complete HTTP implementation
     */
    private void generateImageFromAPI(PatientProfile patientProfile, String storyContent, SceneImageParams params, ImageGenerationCallback callback) {
        try {
            // Build optimized prompt for FLUX.1-dev
            String prompt = buildImagePrompt(patientProfile, storyContent, params);
            String negativePrompt = buildNegativePrompt();
            
            Log.d(TAG, "Generating image with FLUX.1-dev: " + prompt);
            
            // Create JSON payload optimized for FLUX.1-dev
            JSONObject requestJson = new JSONObject();
            requestJson.put("inputs", prompt);
            
            JSONObject parameters = new JSONObject();
            parameters.put("negative_prompt", negativePrompt);
            parameters.put("num_inference_steps", params.inferenceSteps);
            parameters.put("guidance_scale", params.guidanceScale);
            parameters.put("width", params.width);
            parameters.put("height", params.height);
            // Note: scheduler parameter not supported by FLUX.1-dev API
            
            requestJson.put("parameters", parameters);
            
            // Create HTTP request
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                requestJson.toString()
            );
            
            Request request = new Request.Builder()
                    .url(IMAGE_API_URL)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "AlzheimersCaregiver/1.0")
                    .post(body)
                    .build();
            
            // Execute request with callback
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "FLUX.1-dev API call failed", e);
                    callback.onImageGenerationFailed("Network error: " + e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            byte[] imageBytes = response.body().bytes();
                            
                            // Save to cache and get file path
                            String imagePath = saveImageToCache(imageBytes, patientProfile, storyContent, params);
                            String description = generateDetailedSceneDescription(patientProfile, storyContent);
                            
                            if (imagePath != null) {
                                callback.onImageGenerated(imagePath, description);
                            } else {
                                callback.onImageGenerationFailed("Failed to save generated image");
                            }
                        } else {
                            String responseBody = response.body() != null ? response.body().string() : "";
                            Log.e(TAG, "API Error Response: " + responseBody);
                            
                            // Try to parse error from response
                            String errorMessage = "API request failed";
                            if (!responseBody.isEmpty()) {
                                try {
                                    JSONObject errorJson = new JSONObject(responseBody);
                                    if (errorJson.has("error")) {
                                        errorMessage = errorJson.getString("error");
                                    }
                                } catch (JSONException je) {
                                    errorMessage = responseBody;
                                }
                            }
                            
                            Log.e(TAG, "FLUX.1-dev API error: " + response.code() + " - " + errorMessage);
                            callback.onImageGenerationFailed("Image generation failed: " + errorMessage);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing API response", e);
                        callback.onImageGenerationFailed("Error processing response: " + e.getMessage());
                    } finally {
                        if (response.body() != null) {
                            response.body().close();
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating API request", e);
            callback.onImageGenerationFailed("Error creating request: " + e.getMessage());
        }
    }
    
    private String saveImageToCache(byte[] imageData, PatientProfile patientProfile, String storyContent, SceneImageParams params) {
        try {
            String cacheKey = generateCacheKey(patientProfile, storyContent, params);
            File imageFile = new File(cacheDir, cacheKey + ".jpg");
            
            try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                fos.write(imageData);
                fos.flush();
            }
            
            Log.d(TAG, "Image saved to cache: " + imageFile.getAbsolutePath());
            return imageFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Error saving image to cache", e);
            return null;
        }
    }
    
    private String getCachedImagePath(PatientProfile patientProfile, String storyContent, SceneImageParams params) {
        try {
            String cacheKey = generateCacheKey(patientProfile, storyContent, params);
            File cacheFile = new File(cacheDir, cacheKey + ".jpg");
            
            if (cacheFile.exists() && cacheFile.length() > 0) {
                // Check if cache is still valid (7 days)
                long cacheAge = System.currentTimeMillis() - cacheFile.lastModified();
                long maxAge = 7 * 24 * 60 * 60 * 1000L; // 7 days in milliseconds
                
                if (cacheAge < maxAge) {
                    Log.d(TAG, "Using cached image: " + cacheFile.getAbsolutePath());
                    return cacheFile.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error checking cache", e);
        }
        return null;
    }
    
    private String generateCacheKey(PatientProfile patientProfile, String storyContent, SceneImageParams params) {
        try {
            String input = patientProfile.getName() + patientProfile.getBirthplace() + 
                          patientProfile.getProfession() + patientProfile.getHobbies() + 
                          (storyContent != null ? storyContent.substring(0, Math.min(200, storyContent.length())) : "") +
                          params.style + params.mood + params.width + params.height;
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes());
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.w(TAG, "Error generating cache key", e);
            return String.valueOf(System.currentTimeMillis());
        }
    }
    
    private String buildImagePrompt(PatientProfile patientProfile, String storyContent, SceneImageParams params) {
        StringBuilder prompt = new StringBuilder();
        
        // FLUX.1-dev excels with detailed, structured prompts
        prompt.append("A ").append(params.style).append(" depicting a ").append(params.mood).append(" therapeutic scene. ");
        
        // Use story content as primary context if available
        if (storyContent != null && !storyContent.trim().isEmpty()) {
            // Extract key scene elements from the story for the image
            String sceneContext = extractSceneFromStory(storyContent);
            prompt.append(sceneContext).append(". ");
        }
        
        // Add cultural context for personalization
        String culturalContext = getCulturalContext(patientProfile);
        if (!culturalContext.isEmpty()) {
            prompt.append(culturalContext).append(". ");
        }
        
        // If no story content, build scene based on patient details
        if (storyContent == null || storyContent.trim().isEmpty()) {
            String scenery = buildSceneryDescription(patientProfile);
            prompt.append(scenery.replace("Setting: ", "")).append(". ");
            
            // Add hobby/interest elements with more detail for FLUX.1-dev
            String hobbyElements = buildHobbyElements(patientProfile);
            if (!hobbyElements.isEmpty()) {
                prompt.append("Scene includes elements of ").append(hobbyElements.replace("Activities: ", "")).append(". ");
            }
            
            // Add pet elements if applicable
            String petElements = buildPetElements(patientProfile);
            if (!petElements.isEmpty()) {
                prompt.append(petElements.replace("Companions: ", "")).append(". ");
            }
        }
        
        // FLUX.1-dev quality and style instructions - more specific for better results
        prompt.append("Masterpiece quality, highly detailed, perfect composition, ");
        prompt.append("peaceful mood, therapeutic, memory-evoking, cultural authenticity.");
        
        return prompt.toString();
    }
    
    /**
     * Extract key visual elements from story content for image generation
     */
    private String extractSceneFromStory(String storyContent) {
        if (storyContent == null || storyContent.trim().isEmpty()) {
            return "";
        }
        
        StringBuilder sceneElements = new StringBuilder();
        
        // Look for location/setting keywords
        String[] locationKeywords = {"home", "house", "garden", "kitchen", "living room", "bedroom", "courtyard", 
                                   "temple", "market", "school", "village", "town", "city", "farm", "field",
                                   "park", "beach", "mountain", "river", "lake", "forest", "shop", "office"};
        
        // Look for activity/object keywords  
        String[] activityKeywords = {"cooking", "reading", "writing", "playing", "singing", "dancing", "working",
                                   "teaching", "learning", "praying", "walking", "sitting", "laughing", "talking",
                                   "book", "music", "flowers", "plants", "food", "tea", "coffee", "family", "friends"};
        
        // Look for emotional/atmospheric keywords
        String[] atmosphereKeywords = {"warm", "peaceful", "joyful", "comfortable", "cozy", "bright", "sunny",
                                     "gentle", "quiet", "serene", "happy", "content", "loving", "caring"};
        
        String storyLower = storyContent.toLowerCase();
        
        // Find settings
        for (String keyword : locationKeywords) {
            if (storyLower.contains(keyword)) {
                sceneElements.append("A warm ").append(keyword).append(" ");
                break;
            }
        }
        
        // Find activities  
        for (String keyword : activityKeywords) {
            if (storyLower.contains(keyword)) {
                sceneElements.append("with ").append(keyword).append(" ");
                break;
            }
        }
        
        // Find atmosphere
        for (String keyword : atmosphereKeywords) {
            if (storyLower.contains(keyword)) {
                sceneElements.append("in a ").append(keyword).append(" atmosphere");
                break;
            }
        }
        
        // If no specific elements found, create a general therapeutic scene
        if (sceneElements.length() == 0) {
            sceneElements.append("A warm, inviting space filled with memories and comfort");
        }
        
        return sceneElements.toString().trim();
    }
    
    private String generateDetailedSceneDescription(PatientProfile patientProfile, String storyContent) {
        StringBuilder description = new StringBuilder();
        
        description.append("A therapeutic scene personalized for ").append(patientProfile.getName()).append(". ");
        
        // Use story content as primary description if available
        if (storyContent != null && !storyContent.trim().isEmpty()) {
            description.append("This illustration captures the essence of their story: ");
            String storyContext = extractSceneFromStory(storyContent);
            description.append(storyContext).append(". ");
        } else {
            // Fallback to profile-based description
            // Add cultural context
            String culturalContext = getCulturalContext(patientProfile);
            if (!culturalContext.isEmpty()) {
                description.append(culturalContext).append(". ");
            }
            
            // Add setting details
            String scenery = buildSceneryDescription(patientProfile);
            description.append(scenery).append(". ");
            
            // Add activity elements
            String hobbyElements = buildHobbyElements(patientProfile);
            if (!hobbyElements.isEmpty()) {
                description.append(hobbyElements).append(". ");
            }
            
            // Add companion elements
            String petElements = buildPetElements(patientProfile);
            if (!petElements.isEmpty()) {
                description.append(petElements).append(". ");
            }
        }
        
        description.append("This peaceful environment is designed to evoke positive memories and provide comfort.");
        
        return description.toString();
    }

    /**
     * Build negative prompt to avoid unwanted elements
     */
    private String buildNegativePrompt() {
        return "human faces, portraits, people, person, man, woman, child, facial features, eyes, mouth, nose, " +
               "realistic human anatomy, identifiable individuals, close-up of people, " +
               "violent scenes, disturbing content, dark atmosphere, scary imagery, horror elements, " +
               "medical equipment, hospital setting, clinical environment, " +
               "low quality, blurry, distorted, ugly, deformed, artifacts, noise, watermark, text, signature, logo";
    }
    
    /**
     * Get cultural context description based on patient's background for FLUX.1-dev
     */
    private String getCulturalContext(PatientProfile patientProfile) {
        String birthplace = patientProfile.getBirthplace();
        if (birthplace == null || birthplace.trim().isEmpty()) {
            return "";
        }
        
        String birthplaceLower = birthplace.toLowerCase();
        
        // Indian regional contexts for therapeutic imagery
        if (birthplaceLower.contains("karnataka") || birthplaceLower.contains("bangalore") || birthplaceLower.contains("mysore")) {
            return "A warm, traditional Indian home with cultural architectural elements and peaceful garden";
        } else if (birthplaceLower.contains("kerala") || birthplaceLower.contains("kochi") || birthplaceLower.contains("trivandrum")) {
            return "A serene Kerala-style home with lush greenery, wooden architecture, and tranquil backwaters in the background";
        } else if (birthplaceLower.contains("maharashtra") || birthplaceLower.contains("mumbai") || birthplaceLower.contains("pune")) {
            return "A comfortable Maharashtrian home with traditional elements and a peaceful courtyard garden";
        } else if (birthplaceLower.contains("tamil") || birthplaceLower.contains("chennai") || birthplaceLower.contains("madurai")) {
            return "A traditional Tamil home with cultural motifs, temple architecture influences, and flowering plants";
        } else if (birthplaceLower.contains("rajasthan") || birthplaceLower.contains("jaipur") || birthplaceLower.contains("udaipur")) {
            return "A Rajasthani haveli-style setting with warm colors, traditional patterns, and desert garden elements";
        } else if (birthplaceLower.contains("gujarat") || birthplaceLower.contains("ahmedabad") || birthplaceLower.contains("surat")) {
            return "A Gujarati home with intricate architectural details, vibrant colors, and peaceful courtyards";
        } else if (birthplaceLower.contains("india") || birthplaceLower.contains("indian")) {
            return "A warm, traditional Indian home with cultural architectural elements and peaceful garden";
        }
        
        return "A culturally authentic home environment reflecting " + birthplace + " traditions";
    }
    
    private String buildSceneryDescription(PatientProfile patientProfile) {
        StringBuilder scenery = new StringBuilder("Setting: ");
        
        String occupation = patientProfile.getProfession();
        if (occupation != null && !occupation.trim().isEmpty()) {
            String occLower = occupation.toLowerCase();
            if (occLower.contains("teacher") || occLower.contains("professor") || occLower.contains("education")) {
                scenery.append("A tranquil garden space with flowering trees and comfortable seating areas. Warm, golden light creates a peaceful atmosphere where memories feel safe and cherished. Natural elements like plants and water features add to the calming environment.");
            } else if (occLower.contains("doctor") || occLower.contains("nurse") || occLower.contains("medical")) {
                scenery.append("A serene healing garden with medicinal plants and comfortable rest areas. Soft lighting and natural sounds create a therapeutic environment perfect for reflection and peace.");
            } else if (occLower.contains("farmer") || occLower.contains("agriculture")) {
                scenery.append("A pastoral landscape with flourishing crops and fruit trees. Rolling fields under a gentle sky with a cozy farmhouse surrounded by well-tended gardens.");
            } else {
                scenery.append("A tranquil garden space with flowering trees and comfortable seating areas. Warm, golden light creates a peaceful atmosphere where memories feel safe and cherished. Natural elements like plants and water features add to the calming environment.");
            }
        } else {
            scenery.append("A tranquil garden space with flowering trees and comfortable seating areas. Warm, golden light creates a peaceful atmosphere where memories feel safe and cherished. Natural elements like plants and water features add to the calming environment.");
        }
        
        return scenery.toString();
    }
    
    private String buildHobbyElements(PatientProfile patientProfile) {
        String hobbies = patientProfile.getHobbies();
        if (hobbies == null || hobbies.trim().isEmpty()) {
            return "";
        }
        
        StringBuilder elements = new StringBuilder("Activities: Elements of ");
        String hobbiesLower = hobbies.toLowerCase();
        
        if (hobbiesLower.contains("read") || hobbiesLower.contains("book")) {
            elements.append("reading with comfortable seating, bookshelves, and good lighting");
        } else if (hobbiesLower.contains("garden") || hobbiesLower.contains("plant")) {
            elements.append("gardening with beautiful flowers, plants, and gardening tools arranged peacefully");
        } else if (hobbiesLower.contains("cook") || hobbiesLower.contains("food")) {
            elements.append("cooking with a warm kitchen setting, spices, and homemade meals");
        } else if (hobbiesLower.contains("music") || hobbiesLower.contains("sing")) {
            elements.append("music with instruments, sheet music, and a cozy performance space");
        } else if (hobbiesLower.contains("paint") || hobbiesLower.contains("art")) {
            elements.append("art and painting with canvases, brushes, and creative materials in good light");
        } else if (hobbiesLower.contains("walk") || hobbiesLower.contains("exercise")) {
            elements.append("gentle exercise with walking paths, natural scenery, and fresh air");
        } else {
            elements.append(hobbies).append(" represented through thoughtful visual elements");
        }
        
        return elements.toString();
    }
    
    private String buildPetElements(PatientProfile patientProfile) {
        // For now, assume general pet-friendly environment
        // This could be expanded if pet information is added to PatientProfile
        return "Companions: A special corner dedicated to beloved animal companions, filled with warmth and care.";
    }
    
    /**
     * Clean up old cached images (call periodically)
     */
    public void cleanupCache() {
        executorService.execute(() -> {
            try {
                File[] files = cacheDir.listFiles();
                if (files != null) {
                    long currentTime = System.currentTimeMillis();
                    long maxAge = 7 * 24 * 60 * 60 * 1000L; // 7 days
                    int deletedCount = 0;
                    
                    for (File file : files) {
                        if (file.isFile() && (currentTime - file.lastModified()) > maxAge) {
                            if (file.delete()) {
                                deletedCount++;
                            }
                        }
                    }
                    
                    Log.d(TAG, "Cleaned up " + deletedCount + " old cached images");
                }
            } catch (Exception e) {
                Log.w(TAG, "Error cleaning up cache", e);
            }
        });
    }
}
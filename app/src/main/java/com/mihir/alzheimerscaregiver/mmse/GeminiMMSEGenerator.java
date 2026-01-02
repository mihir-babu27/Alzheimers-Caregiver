package com.mihir.alzheimerscaregiver.mmse;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mihir.alzheimerscaregiver.BuildConfig;
import com.mihir.alzheimerscaregiver.data.model.PatientProfile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Advanced MMSE Question Generator using Gemini AI
 * 
 * Creates personalized cognitive assessment questions based on:
 * 1. Memory-Based Questions (40%) - From extracted conversation memories
 * 2. Profile-Based Questions (30%) - From patient demographic data  
 * 3. Standard MMSE Questions (30%) - Traditional cognitive assessments
 * 
 * Features:
 * - Accesses same memory cache as GeminiStoryGenerator
 * - Dynamic difficulty adjustment (Easy/Medium/Hard)
 * - Cultural sensitivity for Indian patients
 * - Clinical validity maintained for diagnostic purposes
 */
public class GeminiMMSEGenerator {
    private static final String TAG = "GeminiMMSEGenerator";
    
    // Gemini API Configuration - Updated with newer available models
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String[] MODEL_NAMES = {
       
        "gemini-2.5-flash",          // Recommended: Fast, efficient, multimodal (Active)
        "gemini-2.5-flash-lite",     // Extremely low cost/latency fallback
        "gemini-2.5-pro"             // High intelligence for complex reasoning
    };
    private static final String GENERATE_ENDPOINT = ":generateContent?key=";
    private static final String API_KEY = BuildConfig.GEMINI_API_KEY;
    
    // Question Distribution
    private static final int TOTAL_QUESTIONS = 15;
    private static final int MEMORY_QUESTIONS = 6;  // 40%
    private static final int PROFILE_QUESTIONS = 4; // 30% 
    private static final int STANDARD_QUESTIONS = 5; // 30%
    
    private final Context context;
    private final ExecutorService executorService;
    private final OkHttpClient httpClient;
    private final Handler mainHandler;
    
    // Track current model index for fallback
    private int currentModelIndex = 0;
    
    public interface MMSEGenerationCallback {
        void onQuestionsGenerated(List<PersonalizedMMSEQuestion> questions);
        void onGenerationFailed(String error);
    }
    
    /**
     * Personalized MMSE Question with enhanced metadata
     */
    public static class PersonalizedMMSEQuestion {
        public final String id;
        public final String section;
        public final String question;
        public final String type;
        public final String[] options;
        public final String correctAnswer;
        public final List<String> acceptedAnswers;
        public final int score;
        public final String difficulty; // Easy, Medium, Hard
        public final String source; // Memory, Profile, Standard
        public final String memoryContext; // For memory-based questions
        public final String imageUrl; // For image-based questions
        
        public PersonalizedMMSEQuestion(String id, String section, String question, String type,
                                      String[] options, String correctAnswer, List<String> acceptedAnswers,
                                      int score, String difficulty, String source, String memoryContext,
                                      String imageUrl) {
            this.id = id;
            this.section = section;
            this.question = question;
            this.type = type;
            this.options = options;
            this.correctAnswer = correctAnswer;
            this.acceptedAnswers = acceptedAnswers;
            this.score = score;
            this.difficulty = difficulty;
            this.source = source;
            this.memoryContext = memoryContext;
            this.imageUrl = imageUrl;
        }
        
        // Convenience constructor for non-image questions
        public PersonalizedMMSEQuestion(String id, String section, String question, String type,
                                      String[] options, String correctAnswer, List<String> acceptedAnswers,
                                      int score, String difficulty, String source, String memoryContext) {
            this(id, section, question, type, options, correctAnswer, acceptedAnswers, score,
                 difficulty, source, memoryContext, null);
        }
    }
    
    public GeminiMMSEGenerator(Context context) {
        this.context = context;
        this.executorService = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Generate personalized MMSE questions for a patient
     */
    public void generatePersonalizedQuestions(PatientProfile patientProfile, MMSEGenerationCallback callback) {
        if (patientProfile == null) {
            callback.onGenerationFailed("Patient profile is required");
            return;
        }
        
        executorService.execute(() -> {
            try {
                // Get extracted memories from GeminiStoryGenerator cache
                String extractedMemories = getExtractedMemoriesForMMSE();
                
                // Generate questions using Gemini API
                generateQuestionsFromAPI(patientProfile, extractedMemories, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "Error generating personalized MMSE questions", e);
                callback.onGenerationFailed("Error generating questions: " + e.getMessage());
            }
        });
    }
    
    /**
     * Access cached memories from GeminiStoryGenerator
     */
    private String getExtractedMemoriesForMMSE() {
        try {
            // Access the same memory cache used by story generation
            java.lang.reflect.Field field = com.mihir.alzheimerscaregiver.reminiscence.GeminiStoryGenerator.class
                .getDeclaredField("cachedMemoriesContext");
            field.setAccessible(true);
            String cachedMemories = (String) field.get(null);
            
            if (cachedMemories != null && !cachedMemories.trim().isEmpty()) {
                Log.d(TAG, "Retrieved cached memories for MMSE generation: " + 
                    (cachedMemories.length() > 100 ? cachedMemories.substring(0, 100) + "..." : cachedMemories));
                return cachedMemories;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not access cached memories, using profile-only generation", e);
        }
        return "";
    }
    
    /**
     * Generate questions using Gemini API with sophisticated prompting
     */
    private void generateQuestionsFromAPI(PatientProfile patientProfile, String extractedMemories, MMSEGenerationCallback callback) {
        try {
            String prompt = buildMMSEGenerationPrompt(patientProfile, extractedMemories);
            
            Log.d(TAG, "Generating personalized MMSE questions with Gemini");
            
            // Create request body
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
            
            // Add generation config for better JSON output
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.7);
            generationConfig.put("topK", 40);
            generationConfig.put("topP", 0.95);
            generationConfig.put("maxOutputTokens", 2048);
            requestBody.put("generationConfig", generationConfig);
            
            RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                requestBody.toString()
            );
            
            // Build URL with current model
            String currentModel = MODEL_NAMES[currentModelIndex];
            String apiUrl = BASE_URL + currentModel + GENERATE_ENDPOINT + API_KEY;
            
            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();
            
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Gemini API call failed", e);
                    mainHandler.post(() -> callback.onGenerationFailed("Network error: " + e.getMessage()));
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (response.isSuccessful() && response.body() != null) {
                            String responseBody = response.body().string();
                            List<PersonalizedMMSEQuestion> questions = parseGeminiResponse(responseBody, patientProfile, extractedMemories);
                            
                            if (questions.isEmpty()) {
                                Log.w(TAG, "No questions generated, using fallback");
                                questions = generateFallbackQuestions(patientProfile);
                            }
                            
                            final List<PersonalizedMMSEQuestion> finalQuestions = questions;
                            mainHandler.post(() -> callback.onQuestionsGenerated(finalQuestions));
                        } else {
                            String errorBody = response.body() != null ? response.body().string() : "";
                            Log.e(TAG, "Gemini API error: " + response.code() + " - " + errorBody);
                            
                            // Try fallback model if available (404 = model not found)
                            if (response.code() == 404 && currentModelIndex < MODEL_NAMES.length - 1) {
                                currentModelIndex++;
                                Log.d(TAG, "Model not found, trying fallback: " + MODEL_NAMES[currentModelIndex]);
                                generateQuestionsFromAPI(patientProfile, extractedMemories, callback);
                            } else {
                                mainHandler.post(() -> callback.onGenerationFailed("API error: " + response.code()));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing Gemini response", e);
                        
                        // Try fallback model if available  
                        if (currentModelIndex < MODEL_NAMES.length - 1) {
                            currentModelIndex++;
                            Log.d(TAG, "Trying fallback model due to response error: " + MODEL_NAMES[currentModelIndex]);
                            generateQuestionsFromAPI(patientProfile, extractedMemories, callback);
                        } else {
                            mainHandler.post(() -> callback.onGenerationFailed("Error processing response: " + e.getMessage()));
                        }
                    } finally {
                        if (response.body() != null) {
                            response.body().close();
                        }
                    }
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating Gemini request", e);
            callback.onGenerationFailed("Error creating request: " + e.getMessage());
        }
    }
    
    /**
     * Build comprehensive prompt for MMSE question generation
     */
    private String buildMMSEGenerationPrompt(PatientProfile patientProfile, String extractedMemories) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are an expert neuropsychologist creating personalized MMSE questions for cognitive assessment.\n\n");
        
        prompt.append("CURRENT DATE: October 21, 2025\n");
        prompt.append("CURRENT YEAR: 2025\n\n");
        
        prompt.append("PATIENT PROFILE:\n");
        prompt.append("- Name: ").append(patientProfile.getName() != null ? patientProfile.getName() : "Not provided").append("\n");
        prompt.append("- Birth Year: ").append(patientProfile.getBirthYear() != null ? patientProfile.getBirthYear() : "Not provided").append("\n");
        prompt.append("- Birthplace: ").append(patientProfile.getBirthplace() != null ? patientProfile.getBirthplace() : "Not provided").append("\n");
        prompt.append("- Profession: ").append(patientProfile.getProfession() != null ? patientProfile.getProfession() : "Not provided").append("\n");
        prompt.append("- Hobbies: ").append(patientProfile.getHobbies() != null ? patientProfile.getHobbies() : "Not provided").append("\n");
        
        if (extractedMemories != null && !extractedMemories.isEmpty()) {
            prompt.append("\nEXTRACTED CONVERSATION MEMORIES:\n");
            prompt.append(extractedMemories);
            prompt.append("\n");
        } else {
            prompt.append("\nNO CONVERSATION MEMORIES AVAILABLE - Focus on profile-based and standard questions only.\n");
        }
        
        prompt.append("\nTASK: Generate exactly ").append(TOTAL_QUESTIONS).append(" personalized MMSE questions with this distribution:\n");
        prompt.append("- ").append(MEMORY_QUESTIONS).append(" Memory-Based Questions (40%) - Use extracted memories if available\n");
        prompt.append("- ").append(PROFILE_QUESTIONS).append(" Profile-Based Questions (30%) - Use patient demographic data\n");
        prompt.append("- ").append(STANDARD_QUESTIONS).append(" Standard MMSE Questions (30%) - Traditional cognitive assessment\n\n");
        
        prompt.append("QUESTION CATEGORIES TO INCLUDE:\n");
        prompt.append("1. Orientation (Time & Place)\n");
        prompt.append("2. Registration (Immediate recall)\n");
        prompt.append("3. Attention & Concentration\n");
        prompt.append("4. Delayed Recall\n");
        prompt.append("5. Language & Naming\n");
        prompt.append("6. Visuospatial Skills\n\n");
        
        prompt.append("CRITICAL PERSONALIZATION RULES:\n");
        prompt.append("1. ONLY create memory-based questions if specific memories are provided above\n");
        prompt.append("2. ONLY create profile-based questions using information explicitly listed in patient profile\n");
        prompt.append("3. NEVER ask about information not mentioned (like school friends, pets, etc.)\n");
        prompt.append("4. USE CORRECT DATES: Current year is 2025, not 2024\n");
        prompt.append("5. For orientation questions, use correct current date/year\n");
        prompt.append("6. Memory questions should use specific places, people, or events from the extracted memories\n");
        prompt.append("7. Profile questions should only use: birthplace, profession, birth year, hobbies if provided\n");
        prompt.append("8. Maintain clinical validity - all questions must assess cognitive function\n");
        prompt.append("9. Use Indian cultural context appropriately\n\n");
        
        prompt.append("OUTPUT FORMAT: Return ONLY a valid JSON object with this exact structure:\n");
        prompt.append("{\n");
        prompt.append("  \"questions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"id\": \"unique_id\",\n");
        prompt.append("      \"section\": \"MMSE category\",\n");
        prompt.append("      \"question\": \"Question text\",\n");
        prompt.append("      \"type\": \"text|multiple_choice|recall\",\n");
        prompt.append("      \"options\": [\"option1\", \"option2\", \"option3\"] // only for multiple_choice\n");
        prompt.append("      \"correctAnswer\": \"expected answer\",\n");
        prompt.append("      \"acceptedAnswers\": [\"answer1\", \"answer2\", \"answer3\"], // alternative correct answers\n");
        prompt.append("      \"score\": 1,\n");
        prompt.append("      \"difficulty\": \"Easy|Medium|Hard\",\n");
        prompt.append("      \"source\": \"Memory|Profile|Standard\",\n");
        prompt.append("      \"memoryContext\": \"relevant memory details\" // for memory-based questions\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        
        prompt.append("EXAMPLE PERSONALIZED QUESTIONS:\n");
        if (!extractedMemories.isEmpty()) {
            prompt.append("Memory-Based: \"What school did you attend in Vijayanagar?\" (Answer: The New Cambridge English School)\n");
            prompt.append("Memory-Based: \"What games did you enjoy playing?\" (Answers: Cricket, GTA, Prince of Persia)\n");
            prompt.append("Memory-Based: \"What pet did you have?\" (Answers: Cat, Tommy, Ollie)\n");
        }
        prompt.append("Profile-Based: \"What year were you born?\" (Answer: ").append(patientProfile.getBirthYear()).append(")\n");
        prompt.append("Profile-Based: \"What city do you live in?\" (Answers: ").append(patientProfile.getBirthplace()).append(", Bangalore, Bengaluru)\n");
        prompt.append("Standard: \"What is today's date?\"\n");
        prompt.append("Standard: \"Spell WORLD backwards\"\n\n");
        
        prompt.append("Generate exactly ").append(TOTAL_QUESTIONS).append(" questions now. Return ONLY the JSON - no additional text.");
        
        return prompt.toString();
    }
    
    /**
     * Parse Gemini API response into PersonalizedMMSEQuestion objects
     */
    private List<PersonalizedMMSEQuestion> parseGeminiResponse(String responseBody, PatientProfile patientProfile, String extractedMemories) {
        List<PersonalizedMMSEQuestion> questions = new ArrayList<>();
        
        try {
            JSONObject response = new JSONObject(responseBody);
            JSONArray candidates = response.getJSONArray("candidates");
            
            if (candidates.length() > 0) {
                JSONObject candidate = candidates.getJSONObject(0);
                JSONObject content = candidate.getJSONObject("content");
                JSONArray parts = content.getJSONArray("parts");
                
                if (parts.length() > 0) {
                    String generatedText = parts.getJSONObject(0).getString("text");
                    
                    // Clean up the response to extract JSON
                    generatedText = generatedText.trim();
                    if (generatedText.startsWith("```json")) {
                        generatedText = generatedText.substring(7);
                    }
                    if (generatedText.endsWith("```")) {
                        generatedText = generatedText.substring(0, generatedText.length() - 3);
                    }
                    
                    Log.d(TAG, "Generated MMSE questions: " + generatedText.substring(0, Math.min(200, generatedText.length())));
                    
                    JSONObject questionsJson = new JSONObject(generatedText);
                    JSONArray questionArray = questionsJson.getJSONArray("questions");
                    
                    for (int i = 0; i < questionArray.length(); i++) {
                        JSONObject questionObj = questionArray.getJSONObject(i);
                        
                        String id = questionObj.optString("id", "q" + (i + 1));
                        String section = questionObj.optString("section", "General");
                        String question = questionObj.getString("question");
                        String type = questionObj.optString("type", "text");
                        String correctAnswer = questionObj.optString("correctAnswer", "");
                        int score = questionObj.optInt("score", 1);
                        String difficulty = questionObj.optString("difficulty", "Medium");
                        String source = questionObj.optString("source", "Standard");
                        String memoryContext = questionObj.optString("memoryContext", "");
                        
                        String[] options = null;
                        if (questionObj.has("options")) {
                            JSONArray optionsArray = questionObj.getJSONArray("options");
                            options = new String[optionsArray.length()];
                            for (int j = 0; j < optionsArray.length(); j++) {
                                options[j] = optionsArray.getString(j);
                            }
                        }
                        
                        List<String> acceptedAnswers = new ArrayList<>();
                        if (questionObj.has("acceptedAnswers")) {
                            JSONArray acceptedArray = questionObj.getJSONArray("acceptedAnswers");
                            for (int j = 0; j < acceptedArray.length(); j++) {
                                acceptedAnswers.add(acceptedArray.getString(j));
                            }
                        }
                        if (acceptedAnswers.isEmpty() && !correctAnswer.isEmpty()) {
                            acceptedAnswers.add(correctAnswer);
                        }
                        
                        PersonalizedMMSEQuestion mmseQuestion = new PersonalizedMMSEQuestion(
                            id, section, question, type, options, correctAnswer, acceptedAnswers,
                            score, difficulty, source, memoryContext
                        );
                        
                        questions.add(mmseQuestion);
                    }
                }
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing Gemini response", e);
        }
        
        return questions;
    }
    
    /**
     * Generate fallback questions if API fails
     */
    private List<PersonalizedMMSEQuestion> generateFallbackQuestions(PatientProfile patientProfile) {
        List<PersonalizedMMSEQuestion> fallbackQuestions = new ArrayList<>();
        
        // Basic orientation questions
        fallbackQuestions.add(new PersonalizedMMSEQuestion(
            "fb1", "Orientation", "What year is it?", "text", null,
            String.valueOf(java.time.Year.now().getValue()), 
            java.util.Arrays.asList(String.valueOf(java.time.Year.now().getValue())),
            1, "Easy", "Standard", ""
        ));
        
        // Profile-based question
        if (patientProfile.getBirthYear() != null) {
            fallbackQuestions.add(new PersonalizedMMSEQuestion(
                "fb2", "Personal Memory", "What year were you born?", "text", null,
                patientProfile.getBirthYear(), 
                java.util.Arrays.asList(patientProfile.getBirthYear()),
                1, "Easy", "Profile", ""
            ));
        }
        
        // Standard MMSE questions
        fallbackQuestions.add(new PersonalizedMMSEQuestion(
            "fb3", "Attention", "Spell WORLD backwards", "multiple_choice", 
            new String[]{"DLROW", "WROLD", "DLOWR"}, "DLROW",
            java.util.Arrays.asList("DLROW"), 1, "Medium", "Standard", ""
        ));
        
        Log.d(TAG, "Generated " + fallbackQuestions.size() + " fallback questions");
        return fallbackQuestions;
    }
}
package com.mihir.alzheimerscaregiver.services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mihir.alzheimerscaregiver.data.entity.MemoryQuestionEntity;
import com.mihir.alzheimerscaregiver.BuildConfig;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * ProactiveQuestionGeneratorService - Generates MMSE questions immediately when memories are extracted
 * This service creates questions in real-time during conversations and stores them in the database
 * for later use in Enhanced MMSE assessments
 */
public class ProactiveQuestionGeneratorService {
    
    private static final String TAG = "ProactiveQuestionGen";
    private static final String FIREBASE_COLLECTION = "patients";
    
    // Same Gemini models as the enhanced MMSE system
    private static final String[] MODEL_NAMES = {
        "gemini-2.0-flash-exp",
        "gemini-1.5-flash-8b", 
        "gemini-1.5-flash",
        "gemini-1.5-pro"
    };
    
    private final Context context;
    private final FirebaseFirestore db;
    private final OkHttpClient client;
    private final ExecutorService executor;
    private final Handler mainHandler;
    
    private int currentModelIndex = 0;
    private String geminiApiKey;
    
    public interface QuestionGenerationCallback {
        void onQuestionsGenerated(List<MemoryQuestionEntity> questions);
        void onError(String error);
    }
    
    public ProactiveQuestionGeneratorService(Context context) {
        this.context = context;
        this.db = FirebaseFirestore.getInstance();
        this.client = new OkHttpClient();
        this.executor = Executors.newFixedThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Get API key from resources or shared preferences
        this.geminiApiKey = getGeminiApiKey();
    }
    
    /**
     * Generate questions immediately when memories are extracted from conversation
     */
    public void generateQuestionsFromMemories(String patientId, List<String> extractedMemories, 
                                            String conversationId, QuestionGenerationCallback callback) {
        
        if (extractedMemories == null || extractedMemories.isEmpty()) {
            Log.d(TAG, "No memories provided for question generation");
            mainHandler.post(() -> callback.onQuestionsGenerated(new ArrayList<>()));
            return;
        }
        
        Log.d(TAG, "🧠 Starting proactive question generation for " + extractedMemories.size() + " memories");
        
        executor.execute(() -> {
            try {
                List<MemoryQuestionEntity> generatedQuestions = new ArrayList<>();
                
                // Generate 1-2 questions per memory (limit to avoid overwhelming)
                for (String memory : extractedMemories) {
                    if (memory != null && !memory.trim().isEmpty()) {
                        List<MemoryQuestionEntity> memoryQuestions = generateQuestionsForSingleMemory(
                            patientId, memory, conversationId);
                        generatedQuestions.addAll(memoryQuestions);
                        
                        // Limit total questions to prevent database overflow
                        if (generatedQuestions.size() >= 10) {
                            break;
                        }
                    }
                }
                
                // Store questions in Firebase
                if (!generatedQuestions.isEmpty()) {
                    storeQuestionsInDatabase(generatedQuestions);
                }
                
                Log.d(TAG, "✅ Generated " + generatedQuestions.size() + " questions for future MMSE use");
                
                mainHandler.post(() -> callback.onQuestionsGenerated(generatedQuestions));
                
            } catch (Exception e) {
                Log.e(TAG, "❌ Error in proactive question generation", e);
                mainHandler.post(() -> callback.onError("Question generation failed: " + e.getMessage()));
            }
        });
    }
    
    /**
     * Generate 1-2 questions for a single memory
     */
    private List<MemoryQuestionEntity> generateQuestionsForSingleMemory(String patientId, 
                                                                        String memory, String conversationId) {
        List<MemoryQuestionEntity> questions = new ArrayList<>();
        
        try {
            String prompt = createMemoryQuestionPrompt(memory);
            String response = callGeminiAPI(prompt);
            
            if (response != null) {
                questions = parseQuestionResponse(response, patientId, memory, conversationId);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error generating questions for memory: " + memory, e);
        }
        
        return questions;
    }
    
    /**
     * Create specialized prompt for memory-based question generation
     */
    private String createMemoryQuestionPrompt(String memory) {
        return "You are an expert neuropsychologist creating MMSE fill-in-the-blank MCQ questions for Alzheimer's patients. " +
                "Create 1-2 FILL-IN-THE-BLANK questions based on this exact memory the patient shared:\n\n" +
                "PATIENT MEMORY: \"" + memory + "\"\n\n" +
                "CRITICAL REQUIREMENTS:\n" +
                "1. Create ONLY fill-in-the-blank questions using words DIRECTLY from the patient's memory\n" +
                "2. Replace ONE key word/phrase from the memory with a blank (_____)\n" +
                "3. The correct answer MUST be a word/phrase the patient actually said\n" +
                "4. Provide 4 multiple choice options (A, B, C, D) where one is correct\n" +
                "5. Other options should be plausible but different words\n" +
                "6. Questions should test specific recall from the memory\n\n" +
                "EXAMPLES:\n" +
                "Memory: \"I enjoyed swimming early in the morning\"\n" +
                "✅ GOOD: \"I enjoyed swimming early in the _____\"\n" +
                "Options: A) morning B) evening C) afternoon D) night\n" +
                "Answer: morning\n\n" +
                "Memory: \"playing cricket and volleyball with friends\"\n" +
                "✅ GOOD: \"I was playing _____ and volleyball with friends\"\n" +
                "Options: A) cricket B) football C) tennis D) basketball\n" +
                "Answer: cricket\n\n" +
                "Memory: \"Vijayanagar swimming pool\"\n" +
                "✅ GOOD: \"I went to the _____ swimming pool\"\n" +
                "Options: A) Vijayanagar B) community C) public D) local\n" +
                "Answer: Vijayanagar\n\n" +
                "Return ONLY a JSON array with this EXACT format:\n" +
                "[\n" +
                "  {\n" +
                "    \"question\": \"[Fill-in-the-blank question with _____]\",\n" +
                "    \"answer\": \"[Exact word from patient's memory]\",\n" +
                "    \"difficulty\": \"easy\",\n" +
                "    \"options\": [\"option1\", \"option2\", \"option3\", \"option4\"]\n" +
                "  }\n" +
                "]\n\n" +
                "Generate exactly 1 question per memory. Focus on the most distinctive word from the patient's exact statement.";
    }
    
    /**
     * Call Gemini API with fallback system
     */
    private String callGeminiAPI(String prompt) throws Exception {
        return tryApiCallOrFallback(prompt, 0);
    }
    
    private String tryApiCallOrFallback(String prompt, int modelIndex) throws Exception {
        if (modelIndex >= MODEL_NAMES.length) {
            throw new Exception("All Gemini models failed");
        }
        
        try {
            String modelName = MODEL_NAMES[modelIndex];
            Log.d(TAG, "Trying Gemini model: " + modelName);
            
            JSONObject requestJson = new JSONObject();
            JSONArray contentsArray = new JSONArray();
            JSONObject contentObject = new JSONObject();
            JSONArray partsArray = new JSONArray();
            JSONObject partObject = new JSONObject();
            
            partObject.put("text", prompt);
            partsArray.put(partObject);
            contentObject.put("parts", partsArray);
            contentsArray.put(contentObject);
            requestJson.put("contents", contentsArray);
            
            RequestBody body = RequestBody.create(
                requestJson.toString(), 
                MediaType.get("application/json; charset=utf-8"));
            
            Request request = new Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + geminiApiKey)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    
                    if (jsonResponse.has("candidates")) {
                        JSONArray candidates = jsonResponse.getJSONArray("candidates");
                        if (candidates.length() > 0) {
                            JSONObject content = candidates.getJSONObject(0)
                                .getJSONObject("content");
                            JSONArray parts = content.getJSONArray("parts");
                            if (parts.length() > 0) {
                                return parts.getJSONObject(0).getString("text");
                            }
                        }
                    }
                    throw new Exception("Invalid response format");
                } else if (response.code() == 404) {
                    Log.w(TAG, "Model " + modelName + " not found, trying fallback");
                    return tryApiCallOrFallback(prompt, modelIndex + 1);
                } else {
                    throw new Exception("API call failed: " + response.code());
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error with model " + MODEL_NAMES[modelIndex], e);
            if (modelIndex < MODEL_NAMES.length - 1) {
                return tryApiCallOrFallback(prompt, modelIndex + 1);
            } else {
                throw e;
            }
        }
    }
    
    /**
     * Parse AI response into MemoryQuestionEntity objects
     */
    private List<MemoryQuestionEntity> parseQuestionResponse(String response, String patientId, 
                                                           String memory, String conversationId) {
        List<MemoryQuestionEntity> questions = new ArrayList<>();
        
        try {
            // Extract JSON array from response
            String jsonContent = extractJsonFromResponse(response);
            JSONArray questionArray = new JSONArray(jsonContent);
            
            for (int i = 0; i < questionArray.length(); i++) {
                JSONObject questionObj = questionArray.getJSONObject(i);
                
                String questionText = questionObj.getString("question");
                String answer = questionObj.getString("answer");
                String difficulty = questionObj.optString("difficulty", "medium");
                
                // Handle MCQ options array
                List<String> alternatives = new ArrayList<>();
                if (questionObj.has("options")) {
                    JSONArray optionsArray = questionObj.getJSONArray("options");
                    for (int j = 0; j < optionsArray.length(); j++) {
                        alternatives.add(optionsArray.getString(j));
                    }
                } else if (questionObj.has("alternatives")) {
                    // Fallback for old format
                    JSONArray altArray = questionObj.getJSONArray("alternatives");
                    for (int j = 0; j < altArray.length(); j++) {
                        alternatives.add(altArray.getString(j));
                    }
                }
                
                // Create question entity
                MemoryQuestionEntity question = new MemoryQuestionEntity(
                    generateQuestionId(patientId),
                    patientId,
                    memory,
                    questionText,
                    answer,
                    difficulty
                );
                
                if (!alternatives.isEmpty()) {
                    question.setAlternativeAnswers(alternatives);
                }
                question.setConversationId(conversationId);
                
                questions.add(question);
                
                Log.d(TAG, "Generated question: " + questionText);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing question response", e);
        }
        
        return questions;
    }
    
    /**
     * Extract JSON content from AI response that might have extra text
     */
    private String extractJsonFromResponse(String response) {
        // Look for JSON array pattern
        int startIndex = response.indexOf('[');
        int endIndex = response.lastIndexOf(']');
        
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1);
        }
        
        return response; // Return as-is if no clear JSON pattern
    }
    
    /**
     * Store generated questions in Firebase under patients/{patientId}/memory_questions
     */
    private void storeQuestionsInDatabase(List<MemoryQuestionEntity> questions) {
        for (MemoryQuestionEntity question : questions) {
            // Store under patients/{patientId}/memory_questions subcollection
            db.collection(FIREBASE_COLLECTION)
                .document(question.getPatientId())
                .collection("memory_questions")
                .add(question)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Question stored with ID: " + documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error storing question", e);
                });
        }
    }
    
    /**
     * Generate unique question ID
     */
    private String generateQuestionId(String patientId) {
        return patientId + "_q_" + System.currentTimeMillis();
    }
    
    /**
     * Get Gemini API key from BuildConfig (same as other Gemini services)
     */
    private String getGeminiApiKey() {
        try {
            return BuildConfig.GEMINI_API_KEY;
        } catch (Exception e) {
            Log.e(TAG, "Error getting Gemini API key, trying Google API key", e);
            return BuildConfig.GOOGLE_API_KEY;
        }
    }
}
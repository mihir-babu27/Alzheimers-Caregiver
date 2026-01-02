package com.mihir.alzheimerscaregiver.services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mihir.alzheimerscaregiver.data.entity.MemoryQuestionEntity;
import com.mihir.alzheimerscaregiver.BuildConfig;
import com.mihir.alzheimerscaregiver.utils.LanguagePreferenceManager;
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
    
        "gemini-2.5-flash",          // Recommended: Fast, efficient, multimodal (Active)
        "gemini-2.5-flash-lite",     // Extremely low cost/latency fallback
        "gemini-2.5-pro"             // High intelligence for complex reasoning
    };
    
    private final Context context;
    private final FirebaseFirestore db;
    private final OkHttpClient client;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final String preferredLanguage;
    
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
        
        // Get user's preferred language for culturally appropriate question generation
        this.preferredLanguage = LanguagePreferenceManager.getPreferredLanguage(context);
        
        // Get API key from resources or shared preferences
        this.geminiApiKey = getGeminiApiKey();
        
        Log.d(TAG, "🌍 ProactiveQuestionGeneratorService initialized with language: " + preferredLanguage);
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
                
                // Filter memories to focus on the most valuable ones for MMSE questions
                List<String> filteredMemories = filterMemoriesForQuestionGeneration(extractedMemories);
                Log.d(TAG, "📝 Filtered " + extractedMemories.size() + " memories down to " + filteredMemories.size() + " for question generation");
                
                // Generate questions with aggressive rate limiting to avoid API 429 errors
                for (int i = 0; i < filteredMemories.size() && generatedQuestions.size() < 4; i++) {
                    String memory = filteredMemories.get(i);
                    if (memory != null && !memory.trim().isEmpty()) {
                        try {
                            // Aggressive rate limiting to prevent 429 errors
                            if (i > 0) {
                                Thread.sleep(5000); // 5 second delay between calls
                            }
                            
                            List<MemoryQuestionEntity> memoryQuestions = generateQuestionsForSingleMemory(
                                patientId, memory, conversationId);
                            generatedQuestions.addAll(memoryQuestions);
                            
                            // Additional delay after successful call
                            Thread.sleep(2000); // 2 second delay after each successful call
                            
                        } catch (InterruptedException e) {
                            Log.w(TAG, "Question generation interrupted", e);
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            Log.e(TAG, "Error generating questions for memory: " + memory + " (" + e.getMessage() + ")", e);
                            // Add delay even on failure to prevent rapid retries
                            try {
                                Thread.sleep(3000); // 3 second delay after error
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
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
     * Create specialized prompt for memory-based question generation with multi-language support
     */
    private String createMemoryQuestionPrompt(String memory) {
        String languageSpecificInstructions = getLanguageSpecificQuestionInstructions(memory);
        String culturalExamples = getCulturalQuestionExamples(memory);
        
        return "You are an expert neuropsychologist creating MMSE fill-in-the-blank MCQ questions for Alzheimer's patients across multiple languages and cultures.\n\n" +
                languageSpecificInstructions + "\n\n" +
                "PATIENT MEMORY TO USE: \"" + memory + "\"\n\n" +
                "CRITICAL MULTI-LANGUAGE REQUIREMENTS:\n" +
                "1. Create ONLY fill-in-the-blank questions using words DIRECTLY from the patient's memory\n" +
                "2. PRESERVE original language terms - DO NOT translate cultural/family words\n" +
                "3. Replace ONE key word/phrase from the memory with a blank (_____)\n" +
                "4. The correct answer MUST be the exact word/phrase the patient said\n" +
                "5. Provide 4 multiple choice options where one is correct\n" +
                "6. Alternative options should be culturally appropriate and in the same language context\n" +
                "7. Respect cultural sensitivity - family terms, place names, festivals should remain authentic\n\n" +
                culturalExamples + "\n\n" +
                "ADVANCED EXAMPLES:\n" +
                "Memory: \"मैं अपनी माँ के साथ दिवाली मनाता था\" (Hindi)\n" +
                "✅ GOOD: \"मैं अपनी _____ के साथ दिवाली मनाता था\"\n" +
                "Options: A) माँ B) बहन C) दादी D) चाची\n" +
                "Answer: माँ\n\n" +
                "Memory: \"என் அம்மா சென்னையில் இருந்தார்\" (Tamil)\n" +
                "✅ GOOD: \"என் அம்மா _____ இருந்தார்\"\n" +
                "Options: A) சென்னையில் B) மும்பையில் C) பெங்களூருவில் D) கொச்சியில்\n" +
                "Answer: சென்னையில்\n\n" +
                "Memory: \"నా నాన్న హైదరాబాద్‌లో పనిచేసేవారు\" (Telugu)\n" +
                "✅ GOOD: \"నా _____ హైదరాబాద్‌లో పనిచేసేవారు\"\n" +
                "Options: A) నాన్న B) అన్న C) మామ D) పిన్నయ్య\n" +
                "Answer: నాన్న\n\n" +
                "Return ONLY a JSON array with this EXACT format:\n" +
                "[\n" +
                "  {\n" +
                "    \"question\": \"[Fill-in-the-blank question with _____ in original language]\",\n" +
                "    \"answer\": \"[Exact word from patient's memory in original language]\",\n" +
                "    \"difficulty\": \"easy\",\n" +
                "    \"options\": [\"option1\", \"option2\", \"option3\", \"option4\"]\n" +
                "  }\n" +
                "]\n\n" +
                "Generate exactly 1 question per memory. Focus on culturally significant words that test personal recall while maintaining linguistic authenticity.";
    }
    
    /**
     * Get language-specific instructions based on the memory content
     */
    private String getLanguageSpecificQuestionInstructions(String memory) {
        StringBuilder instructions = new StringBuilder();
        instructions.append("LANGUAGE & CULTURAL ANALYSIS:\n");
        
        // Detect language patterns in the memory
        if (containsHindiText(memory)) {
            instructions.append("• Memory contains Hindi (हिंदी) - preserve Devanagari script and cultural terms\n");
            instructions.append("• Keep family relationships: माँ, पापा, दादी, नाना, भाई, बहन in original form\n");
            instructions.append("• Maintain place names and cultural references as spoken\n");
        } else if (containsTamilText(memory)) {
            instructions.append("• Memory contains Tamil (தமிழ்) - preserve Tamil script and cultural terms\n");
            instructions.append("• Keep family relationships: அம்மா, அப்பா, பாட்டி, தாத்தா, அண்ணா, அக்காள் in original form\n");
            instructions.append("• Maintain Tamil place names and cultural references as mentioned\n");
        } else if (containsTeluguText(memory)) {
            instructions.append("• Memory contains Telugu (తెలుగు) - preserve Telugu script and cultural terms\n");
            instructions.append("• Keep family relationships: అమ్మ, నాన్న, అజ్జ, అవ్వ, అన్న, అక్క in original form\n");
            instructions.append("• Maintain Telugu place names and cultural references as mentioned\n");
        } else if (containsKannadaText(memory)) {
            instructions.append("• Memory contains Kannada (ಕನ್ನಡ) - preserve Kannada script and cultural terms\n");
            instructions.append("• Keep family relationships: ಅಮ್ಮ, ಅಪ್ಪ, ಅಜ್ಜಿ, ಅಜ್ಜ, ಅಣ್ಣ, ಅಕ್ಕ in original form\n");
            instructions.append("• Maintain Kannada place names and cultural references as mentioned\n");
        } else if (containsMalayalamText(memory)) {
            instructions.append("• Memory contains Malayalam (മലയാളം) - preserve Malayalam script and cultural terms\n");
            instructions.append("• Keep family relationships: അമ്മ, അച്ഛൻ, അമ്മുമ്മ, അച്ഛപ്പൻ, ചേട്ടൻ, ചേച്ചി in original form\n");
            instructions.append("• Maintain Malayalam place names and cultural references as mentioned\n");
        } else {
            instructions.append("• Memory appears to be in English but may contain Indian cultural references\n");
            instructions.append("• Preserve any native language terms that appear in English conversation\n");
            instructions.append("• Respect cultural context and family terms as mentioned\n");
        }
        
        instructions.append("• CRITICAL: Generate questions in the same language as the original memory\n");
        instructions.append("• Multiple choice options must be culturally appropriate and linguistically consistent\n");
        
        return instructions.toString();
    }
    
    /**
     * Get cultural question examples based on detected content
     */
    private String getCulturalQuestionExamples(String memory) {
        StringBuilder examples = new StringBuilder();
        examples.append("CULTURAL CONTEXT EXAMPLES:\n");
        
        if (containsHindiText(memory)) {
            examples.append("Hindi Memory Examples:\n");
            examples.append("• Festival: \"होली के दिन रंग खेलते थे\" → \"_____ के दिन रंग खेलते थे\" (होली)\n");
            examples.append("• Food: \"माँ बनाती थी पूरी\" → \"माँ बनाती थी _____\" (पूरी)\n");
            examples.append("• Place: \"दिल्ली में रहते थे\" → \"_____ में रहते थे\" (दिल्ली)\n\n");
        } else if (containsTamilText(memory)) {
            examples.append("Tamil Memory Examples:\n");
            examples.append("• Festival: \"பொங்கல் நாளில் கொண்டாடினோம்\" → \"_____ நாளில் கொண்டாடினோம்\" (பொங்கல்)\n");
            examples.append("• Food: \"அம்மா இட்லி செய்வாங்க\" → \"அம்மா _____ செய்வாங்க\" (இட்லி)\n");
            examples.append("• Place: \"சென்னையில் வசித்தோம்\" → \"_____ வசித்தோம்\" (சென்னையில்)\n\n");
        } else if (containsTeluguText(memory)) {
            examples.append("Telugu Memory Examples:\n");
            examples.append("• Festival: \"ఉగాది రోజున జరుపుకున్నాం\" → \"_____ రోజున జరుపుకున్నాం\" (ఉగాది)\n");
            examples.append("• Food: \"అమ్మ బిర్యానీ చేసేది\" → \"అమ్మ _____ చేసేది\" (బిర్యానీ)\n");
            examples.append("• Place: \"హైదరాబాద్‌లో నివసించాం\" → \"_____ నివసించాం\" (హైదరాబాద్‌లో)\n\n");
        } else {
            examples.append("English/Mixed Language Examples:\n");
            examples.append("• Cultural: \"Visited the temple during Diwali\" → \"Visited the temple during _____\" (Diwali)\n");
            examples.append("• Family: \"My nani made delicious kheer\" → \"My _____ made delicious kheer\" (nani)\n");
            examples.append("• Place: \"Grew up in Bangalore\" → \"Grew up in _____\" (Bangalore)\n\n");
        }
        
        return examples.toString();
    }
    
    /**
     * Filter memories to focus on the most valuable ones for MMSE question generation
     */
    private List<String> filterMemoriesForQuestionGeneration(List<String> memories) {
        List<String> filtered = new ArrayList<>();
        
        for (String memory : memories) {
            if (memory == null || memory.trim().isEmpty()) continue;
            
            String lowerMemory = memory.toLowerCase();
            
            // Skip metadata entries that aren't suitable for questions
            if (lowerMemory.startsWith("language:") || 
                lowerMemory.startsWith("emotion:") ||
                lowerMemory.startsWith("term:") ||
                memory.length() < 10) { // Skip very short memories
                continue;
            }
            
            // Prioritize substantive memories and activities
            if (lowerMemory.startsWith("memory:") || 
                lowerMemory.startsWith("activity:") ||
                containsSubstantiveContent(memory)) {
                filtered.add(memory);
            }
        }
        
        // If we filtered too aggressively, add some term entries back
        if (filtered.size() < 2) {
            for (String memory : memories) {
                if (memory != null && memory.startsWith("term:") && 
                    (containsNativeScript(memory) || memory.length() > 15)) {
                    filtered.add(memory);
                    if (filtered.size() >= 2) break;
                }
            }
        }
        
        return filtered.subList(0, Math.min(filtered.size(), 2)); // Max 2 memories to prevent API overload
    }
    
    /**
     * Check if memory contains substantive content worth making questions about
     */
    private boolean containsSubstantiveContent(String memory) {
        String lower = memory.toLowerCase();
        return lower.contains("playing") || lower.contains("swimming") || 
               lower.contains("friends") || lower.contains("morning") ||
               lower.contains("games") || lower.contains("pool") ||
               containsNativeScript(memory);
    }
    
    /**
     * Check if text contains native script (non-Latin characters)
     */
    private boolean containsNativeScript(String text) {
        return containsHindiText(text) || containsTamilText(text) || 
               containsTeluguText(text) || containsKannadaText(text) || 
               containsMalayalamText(text);
    }
    
    // Helper methods to detect language content
    private boolean containsHindiText(String text) {
        return text.matches(".*[\\u0900-\\u097F].*"); // Devanagari script range
    }
    
    private boolean containsTamilText(String text) {
        return text.matches(".*[\\u0B80-\\u0BFF].*"); // Tamil script range
    }
    
    private boolean containsTeluguText(String text) {
        return text.matches(".*[\\u0C00-\\u0C7F].*"); // Telugu script range
    }
    
    private boolean containsKannadaText(String text) {
        return text.matches(".*[\\u0C80-\\u0CFF].*"); // Kannada script range
    }
    
    private boolean containsMalayalamText(String text) {
        return text.matches(".*[\\u0D00-\\u0D7F].*"); // Malayalam script range
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
                } else if (response.code() == 429) {
                    // Rate limit hit - add exponential backoff delay
                    int delaySeconds = (int) Math.pow(2, modelIndex) * 5; // 5s, 10s, 20s, 40s
                    Log.w(TAG, "Rate limit hit for model " + modelName + ", waiting " + delaySeconds + " seconds before retry");
                    try {
                        Thread.sleep(delaySeconds * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Interrupted during rate limit backoff");
                    }
                    throw new Exception("API call failed: " + response.code());
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